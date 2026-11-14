package com.beeper.android.messageformat.composer

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import com.beeper.android.messageformat.MatrixToLink
import com.beeper.android.messageformat.SpanAttributes
import com.beeper.android.messageformat.markdown.MatrixMarkdownContent
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser
import com.beeper.android.messageformat.markdown.MarkdownFeature
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

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

    private fun rangesStyled(
        text: String,
        style: SpanStyle,
        entities: List<MatrixInlineEntity> = emptyList(),
    ): List<TextRange> = sourceFormatter.spanStyles(MatrixMarkdownContent(text, entities))
        .filter { it.item == style }
        .map { TextRange(it.start, it.end) }
        .sortedBy { it.start }

    @Test
    fun stylesSourceAndFormatterStyles() {
        val text = "**hi** @Alice"
        val alice = MatrixInlineEntity.UserMention(7, 13, "@alice:example.org")
        assertEquals(listOf(TextRange(2, 4)), rangesStyled(text, SpanStyle(fontWeight = FontWeight.Bold)))
        assertEquals(listOf(TextRange(0, 2), TextRange(4, 6)), rangesStyled(text, marker))
        assertEquals(listOf(TextRange(7, 13)), rangesStyled(text, mention, listOf(alice)))
    }

    @Test
    fun stylesSpoilersThroughTheFormatter() {
        assertEquals(listOf(TextRange(4, 10)), rangesStyled("a ||secret|| b", spoiler))
    }

    @Test
    fun carriesLinkStyles() {
        assertEquals(listOf(TextRange(1, 5)), rangesStyled("[site](https://x.org)", link))
    }

    @Test
    fun reusesTheParseForUnchangedInput() {
        val first = sourceFormatter.annotate(MatrixMarkdownContent("**a**"))
        assertSame(first, sourceFormatter.annotate(MatrixMarkdownContent("**a**")))
        assertNotSame(first, sourceFormatter.annotate(MatrixMarkdownContent("**b**")))
    }
}
