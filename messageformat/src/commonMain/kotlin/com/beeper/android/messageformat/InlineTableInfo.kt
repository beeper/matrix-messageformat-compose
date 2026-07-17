package com.beeper.android.messageformat

/**
 * Metadata for rendering tables as inline text content.
 */
data class InlineTableInfo(
    val rows: List<TableRowInfo>,
    val caption: MatrixBodyParseResult? = null,
    val indentionDepth: Int = 0,
)

data class TableRowInfo(
    val cells: List<TableCellInfo>,
)

data class TableCellInfo(
    val isHeader: Boolean,
    val content: MatrixBodyParseResult,
)
