package com.beeper.android.messageformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MatrixHtmlParserTableTest {
    private val parser = MatrixHtmlParser()
    private val richTableFallbackStyle = MatrixBodyPreFormatStyle(
        formatTableFallback = { table ->
            val rows = table.rows.joinToString(separator = "\n") { row ->
                row.cells.joinToString(separator = "\t") { it.content.text.text }
            }
            listOfNotNull(
                table.caption?.text?.text?.takeIf(String::isNotEmpty),
                rows.takeIf(String::isNotEmpty),
            ).joinToString(separator = "\n").ifEmpty { " " }
        },
    )

    @Test
    fun parsesTableFallbackAndMetadata() {
        val result = parser.parseHtml(
            input = "<table><thead><tr><th><b>Head</b></th></tr></thead><tbody><tr><td>Cell</td></tr></tbody></table>",
            style = richTableFallbackStyle,
            allowRoomMention = true,
        )

        assertEquals("\u200BHead\nCell", result.text.text)

        val tableAnnotation = result.text.getStringAnnotations(MatrixBodyAnnotations.TABLE, 0, result.text.length).single()
        assertEquals(0, tableAnnotation.start)
        assertEquals(result.text.length, tableAnnotation.end)
        assertEquals("0", tableAnnotation.item)

        val table = assertNotNull(result.inlineTables[MatrixBodyAnnotations.INLINE_TABLE_PREFIX + "0"])
        assertEquals(2, table.rows.size)
        assertEquals(0, table.indentionDepth)
        assertEquals(true, table.rows[0].cells.single().isHeader)
        assertEquals(false, table.rows[1].cells.single().isHeader)
        assertEquals("Head", table.rows[0].cells.single().content.text.text)
        assertEquals("Cell", table.rows[1].cells.single().content.text.text)
    }

    @Test
    fun tracksDepthInBlockQuote() {
        val result = parser.parseHtml(
            input = "<blockquote><table><tr><td>Cell</td></tr></table></blockquote>",
            style = MatrixBodyPreFormatStyle(),
            allowRoomMention = true,
        )

        val table = assertNotNull(result.inlineTables[MatrixBodyAnnotations.INLINE_TABLE_PREFIX + "0"])
        assertEquals(1, table.indentionDepth)

        val tableAnnotation = result.text.getStringAnnotations(MatrixBodyAnnotations.TABLE, 0, result.text.length).single()
        assertEquals(table.indentionDepth.toString(), tableAnnotation.item)
    }

    @Test
    fun tracksDepthInNestedList() {
        val result = parser.parseHtml(
            input = "<ul><li>Item<ul><li><table><tr><td>Cell</td></tr></table></li></ul></li></ul>",
            style = MatrixBodyPreFormatStyle(),
            allowRoomMention = true,
        )

        val table = assertNotNull(result.inlineTables[MatrixBodyAnnotations.INLINE_TABLE_PREFIX + "0"])
        assertEquals(2, table.indentionDepth)

        val tableAnnotation = result.text.getStringAnnotations(MatrixBodyAnnotations.TABLE, 0, result.text.length).single()
        assertEquals(table.indentionDepth.toString(), tableAnnotation.item)
    }

    @Test
    fun plaintextFormatterHasNoTableIndention() {
        assertEquals(0f, MatrixBodyToPlaintextFormatter().tableIndention(3).value)
    }

    @Test
    fun preservesFormattedCellContent() {
        val result = parser.parseHtml(
            input = "<table><tr><th><a href='https://example.com'>Head</a></th><td><code>Cell</code></td></tr></table>",
            style = richTableFallbackStyle,
            allowRoomMention = true,
        )

        val table = assertNotNull(result.inlineTables[MatrixBodyAnnotations.INLINE_TABLE_PREFIX + "0"])
        val headerCell = table.rows.single().cells[0].content.text
        val bodyCell = table.rows.single().cells[1].content.text

        assertEquals("\u200BHead\tCell", result.text.text)
        assertTrue(headerCell.getStringAnnotations(MatrixBodyAnnotations.WEB_LINK, 0, headerCell.length).isNotEmpty())
        assertTrue(bodyCell.getStringAnnotations(MatrixBodyAnnotations.INLINE_CODE, 0, bodyCell.length).isNotEmpty())
    }

    @Test
    fun preservesFormattedCaptionContent() {
        val result = parser.parseHtml(
            input = "<table><caption><b>Monthly</b> <a href='https://example.com'>report</a></caption><tr><td>Cell</td></tr></table>",
            style = richTableFallbackStyle,
            allowRoomMention = true,
        )

        val table = assertNotNull(result.inlineTables[MatrixBodyAnnotations.INLINE_TABLE_PREFIX + "0"])
        val caption = assertNotNull(table.caption).text

        assertEquals("\u200BMonthly report\nCell", result.text.text)
        assertEquals("Monthly report", caption.text)
        assertTrue(caption.getStringAnnotations(MatrixBodyAnnotations.WEB_LINK, 0, caption.length).isNotEmpty())
    }

    @Test
    fun usesCustomFallbackWithCompleteTableInfo() {
        var callbackTable: InlineTableInfo? = null
        val result = parser.parseHtml(
            input = "<table><caption>Caption</caption><tr><th>Head</th><td>Cell</td></tr></table>",
            style = MatrixBodyPreFormatStyle(
                formatTableFallback = {
                    callbackTable = it
                    "Custom table"
                },
            ),
            allowRoomMention = true,
        )

        assertEquals("\u200BCustom table", result.text.text)
        val table = assertNotNull(callbackTable)
        assertEquals("Caption", table.caption?.text?.text)
        assertEquals(1, table.rows.size)
        assertEquals(true, table.rows.single().cells[0].isHeader)
        assertEquals("Cell", table.rows.single().cells[1].content.text.text)
        assertEquals(table, result.inlineTables[MatrixBodyAnnotations.INLINE_TABLE_PREFIX + "0"])
    }

    @Test
    fun replacesEmptyCustomFallbackWithSpace() {
        val result = parser.parseHtml(
            input = "<table><tr><td>Cell</td></tr></table>",
            style = MatrixBodyPreFormatStyle(formatTableFallback = { "" }),
            allowRoomMention = true,
        )

        assertEquals("\u200B ", result.text.text)
    }
}
