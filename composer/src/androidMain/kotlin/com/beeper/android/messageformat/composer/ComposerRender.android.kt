package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.TextStyle

// ScaleXSpan multiplies the paint's scale, which already has the style's
internal actual fun spanScaleXBase(textStyle: TextStyle): Float = textStyle.textGeometricTransform?.scaleX ?: 1f
