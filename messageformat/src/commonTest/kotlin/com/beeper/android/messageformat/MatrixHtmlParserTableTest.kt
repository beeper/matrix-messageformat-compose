package com.beeper.android.messageformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MatrixHtmlParserTableTest {
    private val parser = MatrixHtmlParser()

    @Test
    fun parsesTableFallbackAndMetadata() {
        val result = parser.parseHtml(
            input = "<table><thead><tr><th><b>Head</b></th></tr></thead><tbody><tr><td>Cell</td></tr></tbody></table>",
            style = MatrixBodyPreFormatStyle(),
            allowRoomMention = true,
        )

        assertEquals("Head\nCell", result.text.text)

        val tableAnnotation = result.text.getStringAnnotations(MatrixBodyAnnotations.TABLE, 0, result.text.length).single()
        assertEquals(0, tableAnnotation.start)
        assertEquals(result.text.length, tableAnnotation.end)
        assertEquals("0", tableAnnotation.item)

        val table = assertNotNull(result.inlineTables[MatrixBodyAnnotations.INLINE_TABLE_PREFIX + "0"])
        assertEquals(2, table.rows.size)
        assertEquals(true, table.rows[0].cells.single().isHeader)
        assertEquals(false, table.rows[1].cells.single().isHeader)
        assertEquals("Head", table.rows[0].cells.single().content.text.text)
        assertEquals("Cell", table.rows[1].cells.single().content.text.text)
    }

    @Test
    fun preservesFormattedCellContent() {
        val result = parser.parseHtml(
            input = "<table><tr><th><a href='https://example.com'>Head</a></th><td><code>Cell</code></td></tr></table>",
            style = MatrixBodyPreFormatStyle(),
            allowRoomMention = true,
        )

        val table = assertNotNull(result.inlineTables[MatrixBodyAnnotations.INLINE_TABLE_PREFIX + "0"])
        val headerCell = table.rows.single().cells[0].content.text
        val bodyCell = table.rows.single().cells[1].content.text

        assertEquals("Head\tCell", result.text.text)
        assertTrue(headerCell.getStringAnnotations(MatrixBodyAnnotations.WEB_LINK, 0, headerCell.length).isNotEmpty())
        assertTrue(bodyCell.getStringAnnotations(MatrixBodyAnnotations.INLINE_CODE, 0, bodyCell.length).isNotEmpty())
    }

    @Test
    fun preservesFormattedCaptionContent() {
        val result = parser.parseHtml(
            input = "<table><caption><b>Monthly</b> <a href='https://example.com'>report</a></caption><tr><td>Cell</td></tr></table>",
            style = MatrixBodyPreFormatStyle(),
            allowRoomMention = true,
        )

        val table = assertNotNull(result.inlineTables[MatrixBodyAnnotations.INLINE_TABLE_PREFIX + "0"])
        val caption = assertNotNull(table.caption).text

        assertEquals("Monthly report\nCell", result.text.text)
        assertEquals("Monthly report", caption.text)
        assertTrue(caption.getStringAnnotations(MatrixBodyAnnotations.WEB_LINK, 0, caption.length).isNotEmpty())
    }
}
