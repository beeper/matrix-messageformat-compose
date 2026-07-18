package com.beeper.android.messageformat

internal actual fun defaultTableFallback(table: InlineTableInfo): String {
    val rowFallback = table.rows.joinToString(separator = "\n") { row ->
        row.cells.joinToString(separator = "\t") { cell ->
            cell.content.text.text
        }
    }
    return listOfNotNull(
        table.caption?.text?.text?.takeIf(String::isNotEmpty),
        rowFallback.takeIf(String::isNotEmpty),
    ).joinToString(separator = "\n").ifEmpty { " " }
}
