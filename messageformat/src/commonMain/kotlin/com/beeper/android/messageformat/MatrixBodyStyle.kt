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
    val drawBehindUserMention: (DrawScope.(String, Rect) -> Unit)? = { _, position ->
        drawRoundRect(Color.Blue, topLeft = position.topLeft, size = position.size, cornerRadius = CornerRadius(4f * density, 4f * density))
    },
    val drawBehindRoomMention: (DrawScope.(Rect) -> Unit)? = null,
    val drawBehindBlockQuote: (DrawScope.(Int, Rect) -> Unit)? = { _, position ->
        drawRoundRect(
            defaultForegroundColor,
            topLeft = position.topLeft - Offset(12f * density, 0f),
            size = Size(4f * density, position.height),
            cornerRadius = CornerRadius(4f * density, 4f * density),
        )
    },
    val drawBehindInlineCode: (DrawScope.(Rect) -> Unit)? = {
        drawRoundRect(Color.Black, topLeft = it.topLeft, size = it.size, cornerRadius = CornerRadius(4f * density, 4f * density))
    },
    val drawBehindBlockCode: (DrawScope.(Rect) -> Unit)? = {
        val horizontalPadding = 4f * density
        val verticalPadding = 1f * density
        drawRoundRect(
            Color.Black,
            topLeft = it.topLeft - Offset(horizontalPadding, verticalPadding),
            size = Size(it.size.width + horizontalPadding * 2, it.size.height + verticalPadding * 2),
            cornerRadius = CornerRadius(4f * density, 4f * density)
        )
    },
    val drawBehindSpan: (DrawScope.(SpanAttributes, Rect, MatrixFormatInteractionState) -> Unit)? = { attributes, position, _ ->
        if (attributes.bgColor != null) {
            drawRect(
                Color(attributes.bgColor),
                topLeft = position.topLeft,
                size = position.size,
            )
        }
    },
    val drawAboveSpan: (DrawScope.(SpanAttributes, Rect, MatrixFormatInteractionState) -> Unit)? = { attributes, position, state ->
        if (attributes.isSpoiler && attributes.revealId !in state.expandedItems.value) {
            drawRoundRect(
                defaultForegroundColor,
                topLeft = position.topLeft,
                size = position.size,
                cornerRadius = CornerRadius(4f * density, 4f * density)
            )
        }
    },
    val drawBehindDetailsSummary: (DrawScope.(revealId: Int, Rect, MatrixFormatInteractionState) -> Unit)? = null,
    val drawBehindDetailsSummaryFirstLine: (DrawScope.(revealId: Int, Rect, MatrixFormatInteractionState) -> Unit)? = { revealId, position, state ->
        // Use line height and available width as baseline size for triangle size
        val lineHeight = position.size.height
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
                    position.left + canvasPadding,
                    // Center in line height
                    position.top + canvasPadding,
                )
            )
        }
        drawPath(trianglePath, defaultForegroundColor)
    },
    val drawBehindDetailsContent: (DrawScope.(revealId: Int, Rect, MatrixFormatInteractionState) -> Unit)? = null,
)
