package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp

// Skiko ignores TextGeometricTransform, but applies letter spacing in full
internal actual fun glyphWidthStyle(
    naturalWidthPx: Float,
    targetWidthPx: Float,
    textStyle: TextStyle,
    density: Density,
): SpanStyle = SpanStyle(letterSpacing = ((targetWidthPx - naturalWidthPx) / (density.density * density.fontScale)).sp)
