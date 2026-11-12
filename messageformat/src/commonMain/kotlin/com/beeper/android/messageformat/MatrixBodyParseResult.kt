package com.beeper.android.messageformat

import androidx.compose.ui.text.AnnotatedString

data class MatrixBodyParseResult(
    val text: AnnotatedString,
    val inlineImages: Map<String, InlineImageInfo> = emptyMap(),
    val inlineTables: Map<String, InlineTableInfo> = emptyMap(),
    val inlineCodeBlocks: Map<String, InlineCodeBlockInfo> = emptyMap(),
    val expandableItems: Set<Int> = emptySet(),
) {
    constructor(text: String) : this(AnnotatedString(text))
}
