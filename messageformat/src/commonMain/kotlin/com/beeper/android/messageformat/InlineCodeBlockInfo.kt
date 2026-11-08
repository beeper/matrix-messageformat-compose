package com.beeper.android.messageformat

/**
 * Metadata for rendering code blocks line by line as inline text content, with [lines] holding the inline content ids
 * of all non-empty lines, which are also the values of their [MatrixBodyAnnotations.BLOCK_CODE_LINE] annotations.
 * [lineEnd] is the inline content id of the zero-width character following each of these lines, which reserves
 * room for the ellipsis when a scrolling line gets truncated by max lines.
 */
data class InlineCodeBlockInfo(
    val lines: List<String>,
    val lineEnd: String,
)
