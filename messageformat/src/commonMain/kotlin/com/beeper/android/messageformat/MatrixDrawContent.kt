package com.beeper.android.messageformat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.ResolvedTextDirection
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.serialization.json.Json
import kotlin.math.max
import kotlin.math.min

sealed interface DrawPosition {
    val rect: Rect
    val start: Int
    val end: Int
    val textDirection: ResolvedTextDirection

    val isRtl: Boolean
        get() = textDirection == ResolvedTextDirection.Rtl

    data class Block(
        override val rect: Rect,
        override val start: Int,
        override val end: Int,
        override val textDirection: ResolvedTextDirection,
    ) : DrawPosition

    data class InLine(
        override val rect: Rect,
        override val start: Int,
        override val end: Int,
        val line: Int,
        override val textDirection: ResolvedTextDirection,
    ) : DrawPosition
}

/**
 * Call in your Text composable onTextLayout in order to update the [TextLayoutResult] with information to consume in
 * [drawWithContent].
 */
fun MatrixBodyRenderState.onMatrixBodyLayout(
    layoutResult: TextLayoutResult,
) {
    // Find room mention bounding boxes if we want to post-process them
    val roomMentions = flatMapAnnotationsIfSet(style.drawBehindRoomMention, MatrixBodyAnnotations.ROOM_MENTION) { annotation ->
        layoutResult.perLineBoundingBoxesForRange(annotation.start, annotation.end)
    }
    // Find user mention bounding boxes if we want to post-process them
    val userMentions = flatMapAnnotationsIfSet(style.drawBehindUserMention, MatrixBodyAnnotations.USER_MENTION) { annotation ->
        layoutResult.perLineBoundingBoxesForRange(annotation.start, annotation.end).map {
            Pair(annotation.item, it)
        }
    }
    // Find block quotes
    val blockQuotes = mapAnnotationsIfSet(style.drawBehindBlockQuote, MatrixBodyAnnotations.BLOCK_QUOTE) { annotation ->
        annotation.item.toIntOrNull()?.let { indention ->
            layoutResult.blockBoundingBox(annotation.start, annotation.end)?.let { rect ->
                Pair(indention, rect)
            }
        }
    }
    // Find code blocks
    val inlineCode = flatMapAnnotationsIfSet(style.drawBehindInlineCode, MatrixBodyAnnotations.INLINE_CODE) { annotation ->
        layoutResult.perLineBoundingBoxesForRange(annotation.start, annotation.end)
    }
    val blockCode = mapAnnotationsIfSet(style.drawBehindBlockCode, MatrixBodyAnnotations.BLOCK_CODE) { annotation ->
        layoutResult.blockBoundingBox(annotation.start, annotation.end)
    }
    // Find spans
    val spans = flatMapAnnotationsIfSet(style.drawBehindSpan, MatrixBodyAnnotations.SPAN) { annotation ->
        val attributes = try {
            Json.decodeFromString<SpanAttributes>(annotation.item)
        } catch (e: Exception) {
            Logger.withTag("MatrixBodyDrawBehind").e("Span data parsing error", e)
            return@flatMapAnnotationsIfSet emptyList()
        }
        layoutResult.perLineBoundingBoxesForRange(annotation.start, annotation.end).map {
            Pair(attributes, it)
        }
    }
    // Find details tags spans
    val fullDetailsSummaries = mapAnnotationsIfSet(style.drawBehindDetailsSummary, MatrixBodyAnnotations.DETAILS_SUMMARY) { annotation ->
        annotation.item.toIntOrNull()?.let { revealId ->
            layoutResult.blockBoundingBox(annotation.start, annotation.end)?.let { rect ->
                Pair(revealId, rect)
            }
        }
    }
    val detailsSummariesFirstLines = mapAnnotationsIfSet(style.drawBehindDetailsSummaryFirstLine, MatrixBodyAnnotations.DETAILS_SUMMARY) { annotation ->
        annotation.item.toIntOrNull()?.let { revealId ->
            layoutResult.perLineBoundingBoxesForRange(annotation.start, annotation.end).firstOrNull()?.let { rect ->
                Pair(revealId, rect)
            }
        }
    }
    val detailsContents = mapAnnotationsIfSet(style.drawBehindDetailsContent, MatrixBodyAnnotations.DETAILS_CONTENT) { annotation ->
        annotation.item.toIntOrNull()?.let { revealId ->
            layoutResult.blockBoundingBox(annotation.start, annotation.end)?.let { rect ->
                Pair(revealId, rect)
            }
        }
    }
    // Result
    renderResult.value = MatrixFormatOnTextRenderResult(
        userMentions = userMentions,
        roomMentions = roomMentions,
        blockQuotes = blockQuotes,
        inlineCode = inlineCode,
        blockCode = blockCode,
        spans = spans,
        fullDetailsSummaries = fullDetailsSummaries,
        detailsSummariesFirstLines = detailsSummariesFirstLines,
        detailsContents = detailsContents,
    )
}

private fun <T> MatrixBodyRenderState.mapAnnotationsIfSet(function: Any?, tag: String, map: (AnnotatedString.Range<String>) -> T?): ImmutableList<T> {
    return if (function == null) {
        persistentListOf()
    } else {
        text.getStringAnnotations(tag, 0, text.length).mapNotNull(map).toPersistentList()
    }
}

private fun <T> MatrixBodyRenderState.flatMapAnnotationsIfSet(function: Any?, tag: String, map: (AnnotatedString.Range<String>) -> List<T>): ImmutableList<T> {
    return if (function == null) {
        persistentListOf()
    } else {
        text.getStringAnnotations(tag, 0, text.length).flatMap(map).toPersistentList()
    }
}

/**
 * Handles styling applicable after the text layout has happened, taking into account the [MatrixFormatInteractionState] for expandable items,
 * and the provided [MatrixBodyDrawStyle] from [MatrixBodyRenderState.style].
 */
@Composable
fun Modifier.matrixBodyDrawWithContent(
    state: MatrixBodyRenderState,
    interactionState: MatrixFormatInteractionState,
) = drawWithContent {
    val result = state.renderResult.value ?: run {
        drawContent()
        return@drawWithContent
    }
    drawFor(state.style.drawBehindBlockQuote, result.blockQuotes)
    drawFor(state.style.drawBehindBlockCode, result.blockCode)
    drawFor(state.style.drawBehindInlineCode, result.inlineCode)
    drawFor(state.style.drawBehindRoomMention, result.roomMentions)
    drawFor(state.style.drawBehindUserMention, result.userMentions)
    drawFor(state.style.drawBehindSpan, result.spans, interactionState)
    drawFor(state.style.drawBehindDetailsSummary, result.fullDetailsSummaries, interactionState)
    drawFor(state.style.drawBehindDetailsSummaryFirstLine, result.detailsSummariesFirstLines, interactionState)
    drawFor(state.style.drawBehindDetailsContent, result.detailsContents, interactionState)
    drawContent()
    drawFor(state.style.drawAboveSpan, result.spans, interactionState)
}

private fun <T>DrawScope.drawFor(function: (DrawScope.(T) -> Unit)?, items: List<T>) {
    if (function != null) {
        items.forEach {
            function(it)
        }
    }
}

private fun <T, U>DrawScope.drawFor(function: (DrawScope.(T, U) -> Unit)?, items: List<Pair<T, U>>) {
    if (function != null) {
        items.forEach { (a, b) ->
            function(a, b)
        }
    }
}

private fun <T, U, S>DrawScope.drawFor(function: (DrawScope.(T, U, S) -> Unit)?, items: List<Pair<T, U>>, state: S) {
    if (function != null) {
        items.forEach { (a, b) ->
            function(a, b, state)
        }
    }
}

/**
 * Finds bounding boxes for the given text range, one box per line in case the text spans multiple lines.
 */
fun TextLayoutResult.perLineBoundingBoxesForRange(start: Int, end: Int): List<DrawPosition.InLine> {
    return try {
        val firstLine = getLineForOffset(start)
        val lastLine = getLineForOffset(end)
        (firstLine..lastLine).mapNotNull { line ->
            try {
                val currentLineStart = getLineStart(line)
                val startInLine = if (line == firstLine) {
                    start
                } else {
                    currentLineStart
                }
                val endInLine = if (line == lastLine) {
                    end
                } else {
                    getLineEnd(line)
                }
                val perLetterBoundingBoxes = (startInLine..<endInLine).mapNotNull { index ->
                    try {
                        getBoundingBox(index)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (perLetterBoundingBoxes.isEmpty()) {
                    return@mapNotNull null
                }
                DrawPosition.InLine(
                    Rect(
                        // Left and right usually suffice checking the first/last element, but which one for each depends on LTR vs RTL language direction
                        left = min(perLetterBoundingBoxes.first().left, perLetterBoundingBoxes.last().left),
                        right = max(perLetterBoundingBoxes.last().right, perLetterBoundingBoxes.first().right),
                        top = perLetterBoundingBoxes.minOf { it.top },
                        bottom = perLetterBoundingBoxes.maxOf { it.bottom },
                    ),
                    start = startInLine,
                    end = endInLine,
                    line = line,
                    textDirection = getParagraphDirection(startInLine),
                )
            } catch (_: IllegalArgumentException) {
                null
            }
        }
    } catch (_: IllegalArgumentException) {
        emptyList()
    }
}

/**
 * Finds the bounding box for paragraphs.
 */
fun TextLayoutResult.blockBoundingBox(start: Int, end: Int): DrawPosition.Block? {
    return try {
        val firstLine = getLineForOffset(start)
        var lastLine = getLineForOffset(end)
        if (lastLine > firstLine && getLineStart(lastLine) == end) {
            lastLine--
        }
        DrawPosition.Block(
            Rect(
                top = getLineTop(firstLine),
                bottom = getLineBottom(lastLine),
                left = (firstLine..lastLine).minOf { getLineLeft(it) },
                right = (firstLine..lastLine).maxOf { getLineRight(it) },
            ),
            start = start,
            end = end,
            textDirection = getParagraphDirection(start),
        )
    } catch (_: IllegalArgumentException) {
        null
    }
}
