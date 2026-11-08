package com.beeper.android.messageformat

/**
 * Metadata for rendering code blocks line by line as inline text content, with [lines] holding the inline content ids
 * of all non-empty lines, which are also the values of their [MatrixBodyAnnotations.BLOCK_CODE_LINE] annotations.
 */
data class InlineCodeBlockInfo(
    val lines: List<String>,
)
