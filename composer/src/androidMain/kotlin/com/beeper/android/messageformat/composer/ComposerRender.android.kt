package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.TextStyle

// The style's scale is on the base paint, and a span's ScaleXSpan multiplies it
internal actual fun spanScaleXBase(textStyle: TextStyle): Float = textStyle.textGeometricTransform?.scaleX ?: 1f
