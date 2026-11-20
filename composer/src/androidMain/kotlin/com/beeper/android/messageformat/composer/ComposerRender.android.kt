package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

// Letter spacing would come out short: Android drops some of it at the edges of a run,
// and the placeholder is its own run
internal actual fun glyphWidthStyle(
    naturalWidthPx: Float,
    targetWidthPx: Float,
    textStyle: TextStyle,
    density: Density,
): SpanStyle {
    // ScaleXSpan multiplies the paint's scale, which already has the style's
    val scaledWidth = naturalWidthPx * (textStyle.textGeometricTransform?.scaleX ?: 1f)
    val scaleX = if (scaledWidth > 0f) targetWidthPx / scaledWidth else 1f
    return SpanStyle(letterSpacing = 0.sp, textGeometricTransform = TextGeometricTransform(scaleX = scaleX))
}
