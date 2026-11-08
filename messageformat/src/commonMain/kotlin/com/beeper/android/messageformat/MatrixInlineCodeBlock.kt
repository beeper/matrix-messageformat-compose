package com.beeper.android.messageformat

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.gestures.stopScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sign

/**
 * Measurements of the [InlineCodeBlockInfo]s in a styled text, shared between rendering the code blocks
 * via [toCodeBlockInlineContent] and predicting the text layout via [placeholders].
 */
class InlineCodeBlockLayout internal constructor(
    internal val text: AnnotatedString,
    internal val style: TextStyle,
    internal val density: Density,
    private val blocks: List<MeasuredCodeBlock>,
    private val ellipsisWidth: Float,
) {
    /**
     * Placeholders for the lines of all code blocks that don't fit into [maxWidth] and thus need to scroll.
     * Code blocks that fit keep rendering as plain text.
     */
    fun placeholders(maxWidth: Dp): List<AnnotatedString.Range<Placeholder>> {
        return scrollingBlocks(maxWidth).flatMap { scrollingBlock ->
            scrollingBlock.block.lines.flatMap {
                listOf(
                    AnnotatedString.Range(scrollingBlock.line, it.start, it.end),
                    AnnotatedString.Range(scrollingBlock.lineEnd, it.end, it.end + 1),
                )
            }
        }
    }

    internal fun scrollingBlocks(maxWidth: Dp): List<ScrollingCodeBlock> = with(density) {
        val availableWidth = maxWidth.toPx() - PLACEHOLDER_WIDTH_EPSILON_SP.sp.toPx()
        blocks.mapNotNull { block ->
            if (block.contentWidth <= availableWidth - block.indention) {
                return@mapNotNull null
            }
            val viewportWidth = availableWidth - block.indention - ellipsisWidth
            ScrollingCodeBlock(
                block = block,
                line = Placeholder(
                    width = viewportWidth.coerceAtLeast(0f).toSp(),
                    height = block.lineHeight.toSp(),
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
                lineEnd = Placeholder(
                    width = ellipsisWidth.toSp(),
                    height = block.lineHeight.toSp(),
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                ),
            )
        }
    }
}

internal class ScrollingCodeBlock(
    val block: MeasuredCodeBlock,
    val line: Placeholder,
    val lineEnd: Placeholder,
)

internal class MeasuredCodeBlock(
    val id: String,
    val lineEnd: String,
    val lines: List<AnnotatedString.Range<String>>,
    val contentWidth: Int,
    val lineHeight: Int,
    val indention: Float,
)

/**
 * Measures the code blocks found in [text], which needs to be the styled text of the parse result
 * these [InlineCodeBlockInfo]s belong to, as rendered with [style].
 */
@Composable
fun Map<String, InlineCodeBlockInfo>.rememberInlineCodeBlockLayout(
    text: AnnotatedString,
    style: TextStyle,
): InlineCodeBlockLayout {
    val density = LocalDensity.current
    if (isEmpty()) {
        return remember(text, style, density) { InlineCodeBlockLayout(text, style, density, emptyList(), 0f) }
    }
    val textMeasurer = rememberTextMeasurer()
    val mergedStyle = LocalTextStyle.current.merge(style)
    return remember(this, text, mergedStyle, density, textMeasurer) {
        val ellipsisWidth = textMeasurer.measure(ELLIPSIS, mergedStyle, density = density).size.width.toFloat()
        val blocks = values.mapNotNull { info ->
            val lineIds = info.lines.toSet()
            val lines = text.getStringAnnotations(MatrixBodyAnnotations.BLOCK_CODE_LINE, 0, text.length)
                .filter { it.item in lineIds }
            if (lines.isEmpty()) {
                return@mapNotNull null
            }
            val start = lines.first().start
            val end = lines.last().end
            val blockLayout = textMeasurer.measure(
                text.withoutParagraphStyles(start, end),
                mergedStyle,
                softWrap = false,
                density = density,
            )
            MeasuredCodeBlock(
                id = lines.first().item,
                lineEnd = info.lineEnd,
                lines = lines,
                contentWidth = blockLayout.size.width,
                lineHeight = (0 until blockLayout.lineCount).maxOf {
                    ceil(blockLayout.getLineBottom(it) - blockLayout.getLineTop(it)).toInt()
                },
                indention = text.indentionFor(start, end, mergedStyle, density),
            )
        }
        InlineCodeBlockLayout(text, mergedStyle, density, blocks, ellipsisWidth)
    }
}

/**
 * Maps code blocks that don't fit into [maxWidth] to [InlineTextContent] rendering each line without wrapping,
 * all lines of a block scrolling horizontally together.
 */
@Composable
fun InlineCodeBlockLayout.toCodeBlockInlineContent(
    maxWidth: Dp,
    textColor: Color,
): Map<String, InlineTextContent> {
    val scrollingBlocks = remember(this, maxWidth) { scrollingBlocks(maxWidth) }
    if (scrollingBlocks.isEmpty()) {
        return emptyMap()
    }
    val flingBehavior = ScrollableDefaults.flingBehavior()
    val coroutineScope = rememberCoroutineScope()
    return buildMap {
        scrollingBlocks.forEach { scrollingBlock ->
            val block = scrollingBlock.block
            put(block.lineEnd, InlineTextContent(scrollingBlock.lineEnd) {})
            val scrollState = key(block.id) { rememberScrollState() }
            val contentWidth = with(density) { block.contentWidth.toDp() }
            block.lines.forEach { line ->
                put(line.item, InlineTextContent(scrollingBlock.line) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clearAndSetSemantics {}
                                .codeBlockScroll(scrollState, flingBehavior, coroutineScope),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            Text(
                                text = text.withoutParagraphStyles(line.start, line.end),
                                modifier = Modifier.width(contentWidth),
                                style = style,
                                color = textColor,
                                textAlign = TextAlign.Left,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                })
            }
        }
    }
}

/**
 * Like [horizontalScroll], but leaves drags towards an edge the content is already scrolled to
 * for parents to handle, e.g. swipe gestures on a message.
 */
private fun Modifier.codeBlockScroll(
    state: ScrollState,
    flingBehavior: FlingBehavior,
    coroutineScope: CoroutineScope,
) = pointerInput(state, flingBehavior) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (state.isScrollInProgress) {
            coroutineScope.launch { state.stopScroll() }
        }
        val velocityTracker = VelocityTracker()
        velocityTracker.addPointerInputChange(down)
        var totalDrag = Offset.Zero
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
            if (!change.pressed || change.isConsumed) {
                return@awaitEachGesture
            }
            totalDrag += change.positionChange()
            if (abs(totalDrag.y) > viewConfiguration.touchSlop && abs(totalDrag.y) > abs(totalDrag.x)) {
                return@awaitEachGesture
            }
            if (abs(totalDrag.x) > viewConfiguration.touchSlop) {
                val canScroll = if (totalDrag.x < 0) state.canScrollForward else state.canScrollBackward
                if (!canScroll) {
                    return@awaitEachGesture
                }
                change.consume()
                velocityTracker.addPointerInputChange(change)
                state.dispatchRawDelta(-(totalDrag.x - sign(totalDrag.x) * viewConfiguration.touchSlop))
                break
            }
        }
        val completed = horizontalDrag(down.id) { change ->
            velocityTracker.addPointerInputChange(change)
            state.dispatchRawDelta(-change.positionChange().x)
            change.consume()
        }
        if (!completed) {
            return@awaitEachGesture
        }
        val velocity = -velocityTracker.calculateVelocity().x
        coroutineScope.launch {
            state.scroll {
                with(flingBehavior) { performFling(velocity) }
            }
        }
    }
}.horizontalScroll(state, enabled = false)

private fun AnnotatedString.withoutParagraphStyles(start: Int, end: Int): AnnotatedString {
    val content = subSequence(start, end.coerceAtMost(length))
    return AnnotatedString(content.text, content.spanStyles)
}

private fun AnnotatedString.indentionFor(
    start: Int,
    end: Int,
    style: TextStyle,
    density: Density,
): Float {
    val textIndent = paragraphStyles
        .filter { it.start <= start && end <= it.end }
        .minByOrNull { it.end - it.start }
        ?.item?.textIndent
        ?: return 0f
    return maxOf(textIndent.firstLine.toPx(style, density), textIndent.restLine.toPx(style, density))
}

private fun TextUnit.toPx(style: TextStyle, density: Density): Float = with(density) {
    when {
        isSp -> toPx()
        isEm && style.fontSize.isSp -> value * style.fontSize.toPx()
        else -> 0f
    }
}

private const val ELLIPSIS = "\u2026"
