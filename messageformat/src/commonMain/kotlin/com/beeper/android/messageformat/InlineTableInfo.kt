package com.beeper.android.messageformat

/**
 * Metadata of expected inline tables.
 */
data class InlineTableInfo(
    val rows: List<TableRowInfo>,
    val caption: MatrixBodyParseResult? = null,
)

data class TableRowInfo(
    val cells: List<TableCellInfo>,
)

data class TableCellInfo(
    val isHeader: Boolean,
    val content: MatrixBodyParseResult,
)
