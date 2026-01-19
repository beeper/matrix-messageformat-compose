package com.beeper.android.messageformat

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.AnnotatedString
import java.util.regex.Pattern

const val DEFAULT_UNORDERED_BULLET_STRING = "\u2022 "

/**
 * Styling that is independent of screen density, text size, and theme, and thus can be done as part of the first pre-processing step.
 */
data class MatrixBodyPreFormatStyle(
    val listBulletForDepth: (depth: Int) -> String = { DEFAULT_UNORDERED_BULLET_STRING },
    val formatRoomMention: () -> String = { MENTION_ROOM },
    val formatUserMention: (userId: String, content: AnnotatedString) -> AnnotatedString = { _, content -> content },
    val formatRoomLink: (roomId: String, via: List<String>?, content: AnnotatedString) -> AnnotatedString = { _, _, content -> content },
    val formatMessageLink: (roomId: String, messageId: String, via: List<String>?, content: AnnotatedString) -> AnnotatedString = { _, _, _, content -> content },
    val detailsSummaryIndicatorPlaceholder: String = "\u2007\u2007", // Non-breaking figure space for somewhat reliable placeholders, so drawBehind can draw the actual one based on state
    val formatInlineImageFallback: (InlineImageInfo) -> String = { it.title ?: it.alt ?: "IMG" },
    val autoLinkUrlPattern: Pattern? = DEFAULT_WEB_URL_PATTERN,
)

/**
 * Styling relevant to the final drawing stage of the formatted body text, mostly done directly in a [DrawScope] after the [AnnotatedString] has already
 * been layout in the Text composable.
 */
data class MatrixBodyDrawStyle(
    val defaultForegroundColor: Color = Color.Gray,
    val drawBehindUserMention: (DrawScope.(String, DrawPosition.InLine) -> Unit)? = { _, pos ->
        val rect = pos.rect
        drawRoundRect(
            Color.Blue,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(4f * density, 4f * density)
        )
    },
    val drawBehindRoomMention: (DrawScope.(DrawPosition.InLine) -> Unit)? = null,
    val drawBehindBlockQuote: (DrawScope.(Int, DrawPosition.Block) -> Unit)? = { _, pos ->
        val rect = pos.rect
        drawRoundRect(
            defaultForegroundColor,
            topLeft = if (pos.isRtl) {
                rect.topRight + Offset(12f * density, 0f)
            } else {
                rect.topLeft - Offset(12f * density, 0f)
            },
            size = Size(4f * density, rect.height),
            cornerRadius = CornerRadius(4f * density, 4f * density),
        )
    },
    val drawBehindInlineCode: (DrawScope.(DrawPosition.InLine) -> Unit)? = { pos ->
        val rect = pos.rect
        drawRoundRect(
            Color.Black,
            topLeft = rect.topLeft,
            size = rect.size,
            cornerRadius = CornerRadius(4f * density, 4f * density),
        )
    },
    val drawBehindBlockCode: (DrawScope.(DrawPosition.Block) -> Unit)? = { pos ->
        val rect = pos.rect
        val horizontalPadding = 4f * density
        val verticalPadding = 1f * density
        drawRoundRect(
            Color.Black,
            topLeft = rect.topLeft - Offset(horizontalPadding, verticalPadding),
            size = Size(rect.size.width + horizontalPadding * 2, rect.size.height + verticalPadding * 2),
            cornerRadius = CornerRadius(4f * density, 4f * density),
        )
    },
    val drawBehindSpan: (DrawScope.(SpanAttributes, DrawPosition.InLine, MatrixFormatInteractionState) -> Unit)? = { attributes, pos, _ ->
        if (attributes.bgColor != null) {
            val rect = pos.rect
            drawRect(
                Color(attributes.bgColor),
                topLeft = rect.topLeft,
                size = rect.size,
            )
        }
    },
    val drawAboveSpan: (DrawScope.(SpanAttributes, DrawPosition.InLine, MatrixFormatInteractionState) -> Unit)? = { attributes, pos, state ->
        if (attributes.isSpoiler && attributes.revealId !in state.expandedItems.value) {
            val rect = pos.rect
            drawRoundRect(
                defaultForegroundColor,
                topLeft = rect.topLeft,
                size = rect.size,
                cornerRadius = CornerRadius(4f * density, 4f * density)
            )
        }
    },
    val drawBehindDetailsSummary: (DrawScope.(revealId: Int, DrawPosition.Block, MatrixFormatInteractionState) -> Unit)? = null,
    val drawBehindDetailsSummaryFirstLine: (DrawScope.(revealId: Int, DrawPosition.InLine, MatrixFormatInteractionState) -> Unit)? = { revealId, pos, state ->
        val rect = pos.rect
        // Use line height and available width as baseline size for triangle size
        val lineHeight = rect.size.height
        val triangleSideLength = lineHeight / 2f
        // * sqrt(3) / 2
        val triangleHeight = triangleSideLength * 0.8660254f
        val shortSidePadding = (triangleSideLength - triangleHeight) / 2
        val trianglePath = Path().apply {
            if (revealId in state.expandedItems.value) {
                // Already expanded => downward-facing triangle
                moveTo(0f, shortSidePadding)
                lineTo(triangleSideLength / 2, triangleSideLength - shortSidePadding)
                lineTo(triangleSideLength, shortSidePadding)
            } else {
                moveTo(shortSidePadding, 0f)
                lineTo(triangleSideLength - shortSidePadding, triangleSideLength / 2)
                lineTo(shortSidePadding, triangleSideLength)
            }
            close()
            val canvasPadding = (lineHeight - triangleSideLength) / 2
            translate(
                Offset(
                    // Center between text start and
                    if (pos.isRtl) {
                        rect.right - triangleSideLength + canvasPadding
                    } else {
                        rect.left + canvasPadding
                    },
                    // Center in line height
                    rect.top + canvasPadding,
                )
            )
        }
        drawPath(trianglePath, defaultForegroundColor)
    },
    val drawBehindDetailsContent: (DrawScope.(revealId: Int, DrawPosition.Block, MatrixFormatInteractionState) -> Unit)? = null,
)
