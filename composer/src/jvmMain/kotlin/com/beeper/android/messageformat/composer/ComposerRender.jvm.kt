package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

// Skiko ignores TextGeometricTransform, but applies letter spacing in full
internal actual fun widenGlyph(advancePx: Float, widthPx: Float, textStyle: TextStyle, density: Density): SpanStyle =
    SpanStyle(letterSpacing = ((widthPx - advancePx) / (density.density * density.fontScale)).sp)
