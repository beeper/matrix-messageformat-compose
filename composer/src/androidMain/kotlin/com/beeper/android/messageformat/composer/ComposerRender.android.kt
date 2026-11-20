package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

// Letter spacing would come out short: Android drops some of it at the edges of a run, and the placeholder is its own run
internal actual fun widenGlyph(advancePx: Float, widthPx: Float, textStyle: TextStyle, density: Density): SpanStyle {
    // ScaleXSpan multiplies the paint's scale, which already has the style's
    val scaledAdvance = advancePx * (textStyle.textGeometricTransform?.scaleX ?: 1f)
    val scaleX = if (scaledAdvance > 0f) widthPx / scaledAdvance else 1f
    return SpanStyle(letterSpacing = 0.sp, textGeometricTransform = TextGeometricTransform(scaleX = scaleX))
}
