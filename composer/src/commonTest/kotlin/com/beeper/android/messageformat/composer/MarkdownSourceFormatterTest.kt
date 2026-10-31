package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import com.beeper.android.messageformat.MatrixToLink
import com.beeper.android.messageformat.SpanAttributes
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser
import com.beeper.android.messageformat.markdown.MarkdownFeature
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarkdownSourceFormatterTest {

    private val marker = SpanStyle(color = Color.Gray)
    private val mention = SpanStyle(fontWeight = FontWeight.SemiBold)
    private val spoiler = SpanStyle(color = Color.Red)
    private val link = SpanStyle(color = Color.Blue)

    private val formatter = object : NoStyleFormatter() {
        override fun formatSyntaxMarker(context: FormatContext) = listOf(marker)
        override fun formatUserMention(mention: MatrixToLink.UserMention, context: FormatContext) =
            listOf(this@MarkdownSourceFormatterTest.mention)
        override fun formatSpan(attributes: SpanAttributes, context: FormatContext) =
            if (attributes.isSpoiler) listOf(spoiler) else null
        override fun formatWebLink(href: String, context: FormatContext) =
            listOf(LinkAnnotation.Url(href, TextLinkStyles(style = link)))
    }

    private val sourceFormatter = MarkdownSourceFormatter(MatrixMarkdownParser(MarkdownFeature.ALL), formatter)

    private fun TextFieldState.rangesStyled(style: SpanStyle): List<TextRange> {
        var ranges = emptyList<TextRange>()
        edit {
            ranges = getSpanStyles(TextRange(0, length))
                .filter { it.spanStyle == style }
                .map { it.textRange }
                .sortedBy { it.start }
        }
        return ranges
    }

    @Test
    fun appliesSourceStylesAndFormatterStylesToTheBuffer() {
        val state = TextFieldState("**hi** @Alice")
        val alice = MatrixInlineEntity.UserMention(7, 13, "@alice:example.org")
        state.edit { sourceFormatter.applyTo(this, listOf(alice)) }
        assertEquals(listOf(TextRange(2, 4)), state.rangesStyled(SpanStyle(fontWeight = FontWeight.Bold)))
        assertEquals(listOf(TextRange(0, 2), TextRange(4, 6)), state.rangesStyled(marker))
        assertEquals(listOf(TextRange(7, 13)), state.rangesStyled(mention))
        assertEquals("**hi** @Alice", state.text.toString())
    }

    @Test
    fun neverAppliesParagraphStyles() {
        val state = TextFieldState("## Title\n\ntext")
        state.edit {
            sourceFormatter.applyTo(this, emptyList())
            assertTrue(getParagraphStyles(TextRange(0, length)).isEmpty())
        }
        assertEquals(listOf(TextRange(0, 2)), state.rangesStyled(marker))
    }

    @Test
    fun stylesSpoilersThroughTheFormatter() {
        val state = TextFieldState("a ||secret|| b")
        state.edit { sourceFormatter.applyTo(this, emptyList()) }
        assertEquals(listOf(TextRange(4, 10)), state.rangesStyled(spoiler))
    }

    @Test
    fun carriesLinkStylesIntoTheBuffer() {
        val state = TextFieldState("[site](https://x.org)")
        state.edit { sourceFormatter.applyTo(this, emptyList()) }
        assertEquals(listOf(TextRange(1, 5)), state.rangesStyled(link))
    }

    @Test
    fun reusesTheParseForUnchangedInput() {
        val first = sourceFormatter.annotate("**a**", emptyList())
        assertSame(first, sourceFormatter.annotate("**a**", emptyList()))
        assertTrue(first !== sourceFormatter.annotate("**b**", emptyList()))
    }

    @Test
    fun keepsTextUntouched() {
        assertEquals("# heading", sourceFormatter.annotate("# heading", emptyList()).text.text)
    }
}
