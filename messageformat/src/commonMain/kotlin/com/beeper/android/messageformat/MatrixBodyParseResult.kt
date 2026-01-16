package com.beeper.android.messageformat

import androidx.compose.ui.text.AnnotatedString

data class MatrixBodyParseResult(
    val text: AnnotatedString,
    val inlineImages: Map<String, InlineImageInfo> = emptyMap(),
    val expandableItems: Set<Int> = emptySet(),
) {
    constructor(text: String) : this(AnnotatedString(text))
}
