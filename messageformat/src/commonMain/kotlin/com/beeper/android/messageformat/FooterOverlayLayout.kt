package com.beeper.android.messageformat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A custom layout that inputs a [TextLayoutResult] for its main content,
 * and overlays a footer at the bottom end, using existing free space if possible, or extending
 * the bounds of the composable if necessary.
 */
@Composable
fun FooterOverlayLayout(
    textLayoutResult: TextLayoutResult,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    content: @Composable () -> Unit,
    overlay: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    forceWrapWidth: Boolean = false,
) {
    val density = LocalDensity.current
    val horizontalPaddingPx = density.run { horizontalPadding.roundToPx() }
    val verticalPaddingPx = density.run { verticalPadding.roundToPx() }
    Layout(
        modifier = modifier,
        content = {
            content()
            overlay?.invoke()
        }
    ) { measurables, constraints ->
        val textMeasurable = measurables.firstOrNull() ?: return@Layout layout(0, 0) {}
        val overlayMeasurable = measurables.getOrNull(1)

        // Measure main text
        val textPlaceable = textMeasurable.measure(constraints)
        val overlayPlaceable = overlayMeasurable?.measure(constraints)

        val lastLineWidth = if (textLayoutResult.lineCount > 0) {
            textLayoutResult.getLineRight(textLayoutResult.lineCount-1).roundToInt()
        } else {
            0
        }
        val maxAvailableWidth = constraints.maxWidth

        val lastLineWidthWithHorizontalOverlay = lastLineWidth +
                (overlayPlaceable?.width?.takeIf { it > 0 }?.let { it + horizontalPaddingPx } ?: 0)

        val textMaxWidth = if (forceWrapWidth && textLayoutResult.lineCount > 0) {
            (0..<textLayoutResult.lineCount).maxOf { line ->
                textLayoutResult.getLineRight(line).roundToInt()
            }
        } else {
            textPlaceable.width
        }

        if (maxAvailableWidth >= lastLineWidthWithHorizontalOverlay) {
            // Fits into the last line
            val width = max(textMaxWidth, lastLineWidthWithHorizontalOverlay)
            val height = max(textPlaceable.height, overlayPlaceable?.height ?: 0)
            layout(width, height) {
                textPlaceable.place(0, 0)
                overlayPlaceable?.place(
                    x = width - overlayPlaceable.width,
                    y = textPlaceable.height - overlayPlaceable.height
                )
            }
        } else {
            // Needs a new line
            val width = max(textMaxWidth, overlayPlaceable?.width ?: 0)
            val height = textPlaceable.height +
                    (overlayPlaceable?.height?.takeIf { it > 0 }?.let { it + verticalPaddingPx } ?: 0)
            layout(width, height) {
                textPlaceable.place(0, 0)
                overlayPlaceable?.place(
                    x = width - overlayPlaceable.width,
                    y = textPlaceable.height + verticalPaddingPx
                )
            }
        }
    }
}
