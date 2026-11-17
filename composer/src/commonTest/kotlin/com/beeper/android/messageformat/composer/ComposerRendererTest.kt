package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.beeper.android.messageformat.MatrixBodyAnnotations
import com.beeper.android.messageformat.MatrixToLink
import com.beeper.android.messageformat.SpanAttributes
import com.beeper.android.messageformat.markdown.MarkdownFeature
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import com.beeper.android.messageformat.markdown.MatrixMarkdownContent
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class ComposerRendererTest {

    private val marker = SpanStyle(color = Color.Gray)
    private val mention = SpanStyle(fontWeight = FontWeight.SemiBold)
    private val spoiler = SpanStyle(color = Color.Red)
    private val link = SpanStyle(color = Color.Blue)
    private val emoteStyle = SpanStyle(fontSize = 20.sp)

    private val formatter = object : NoStyleFormatter() {
        override fun formatSyntaxMarker(context: FormatContext) = listOf(marker)
        override fun formatUserMention(mention: MatrixToLink.UserMention, context: FormatContext) =
            listOf(this@ComposerRendererTest.mention)
        override fun formatSpan(attributes: SpanAttributes, context: FormatContext) =
            if (attributes.isSpoiler) listOf(spoiler) else null
        override fun formatWebLink(href: String, context: FormatContext) =
            listOf(LinkAnnotation.Url(href, TextLinkStyles(style = link)))
    }

    private var shownEmoteStyle: SpanStyle? = emoteStyle
    private val renderer = ComposerRenderer(MatrixMarkdownParser(MarkdownFeature.ALL), formatter) { shownEmoteStyle }

    private fun emote(start: Int, end: Int) = MatrixInlineEntity.CustomEmote(start, end, "cat", "mxc://x/cat")

    private fun render(text: String, vararg entities: MatrixInlineEntity) =
        renderer.render(MatrixMarkdownContent(text, entities.toList()))

    private fun rangesStyled(text: String, style: SpanStyle, vararg entities: MatrixInlineEntity): List<TextRange> =
        render(text, *entities).spanStyles
            .filter { it.item == style }
            .map { TextRange(it.start, it.end) }
            .sortedBy { it.start }

    private fun transformed(text: String, vararg entities: MatrixInlineEntity, check: (String, List<TextRange>) -> Unit) {
        val transformation = ComposerOutputTransformation { render(it.toString(), *entities) }
        TextFieldState(text).edit {
            with(transformation) { transformOutput() }
            val emoteRanges = getSpanStyles(TextRange(0, length))
                .filter { it.spanStyle == emoteStyle }
                .map { it.textRange }
                .sortedBy { it.start }
            check(asCharSequence().toString(), emoteRanges)
        }
    }

    @Test
    fun stylesSourceAndFormatterStyles() {
        val text = "**hi** @Alice"
        val alice = MatrixInlineEntity.UserMention(7, 13, "@alice:example.org")
        assertEquals(listOf(TextRange(2, 4)), rangesStyled(text, SpanStyle(fontWeight = FontWeight.Bold)))
        assertEquals(listOf(TextRange(0, 2), TextRange(4, 6)), rangesStyled(text, marker))
        assertEquals(listOf(TextRange(7, 13)), rangesStyled(text, mention, alice))
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
    fun reusesTheRenderForUnchangedInput() {
        val first = render(":cat: **a**", emote(0, 5))
        assertSame(first, render(":cat: **a**", emote(0, 5)))
        assertNotSame(first, render(":cat: **b**", emote(0, 5)))
    }

    @Test
    fun rendersAgainWhenTheEmoteStyleChanges() {
        val first = render(":cat:", emote(0, 5))
        shownEmoteStyle = SpanStyle(fontSize = 30.sp)
        val second = render(":cat:", emote(0, 5))
        assertNotSame(first, second)
        assertEquals(SpanStyle(fontSize = 30.sp), second.emotes.single().style)
    }

    @Test
    fun showsEmotesWithoutAStyleAsText() {
        shownEmoteStyle = null
        val render = render("hi :cat:", emote(3, 8))
        assertEquals(emptyList(), render.emotes)
        assertEquals("hi :cat:", render.displayText.text)
    }

    @Test
    fun displayTextCollapsesEmotesAndMovesAnnotations() {
        val alice = MatrixInlineEntity.UserMention(9, 15, "@alice:example.org")
        val render = render("hi :cat: @Alice", emote(3, 8), alice)
        assertEquals("hi   @Alice", render.displayText.text)
        val mentions = render.displayText.getStringAnnotations(MatrixBodyAnnotations.USER_MENTION, 0, 12)
        assertEquals(listOf(5 to 11), mentions.map { it.start to it.end })
        assertEquals(3, render.toDisplayOffset(3))
        assertEquals(4, render.toDisplayOffset(8))
    }

    @Test
    fun transformationFormatsMarkdownAroundPlaceholders() {
        val transformation = ComposerOutputTransformation { render(it.toString(), emote(0, 5)) }
        TextFieldState(":cat: **hi**").edit {
            with(transformation) { transformOutput() }
            val bold = getSpanStyles(TextRange(0, length))
                .filter { it.spanStyle.fontWeight == FontWeight.Bold }
                .map { it.textRange }
            assertEquals(listOf(TextRange(4, 6)), bold)
        }
    }

    @Test
    fun transformationReplacesEmotesWithStyledPlaceholders() {
        transformed("hi :cat: and :cat: there", emote(3, 8), emote(13, 18)) { text, emoteRanges ->
            assertEquals("hi   and   there", text)
            assertEquals(listOf(TextRange(3, 4), TextRange(9, 10)), emoteRanges)
        }
    }

    @Test
    fun transformedOffsetsCollapseEachEmoteToOneCharacter() {
        val offsets = TransformedOffsets(listOf(TextRange(3, 8), TextRange(13, 19)))
        assertEquals(0, offsets.toTransformed(0))
        assertEquals(3, offsets.toTransformed(3))
        assertEquals(4, offsets.toTransformed(8))
        assertEquals(9, offsets.toTransformed(13))
        assertEquals(10, offsets.toTransformed(19))
        assertEquals(12, offsets.toTransformed(21))
    }

    @Test
    fun transformCollapsesEmotesAndMovesAnnotations() {
        val source = buildAnnotatedString {
            append("hi :cat: and :dog!: there")
            addStringAnnotation("tag", "a", 3, 8)
            addStringAnnotation("tag", "b", 9, 21)
        }
        val transformed = TransformedOffsets(listOf(TextRange(3, 8), TextRange(13, 19))).transform(source)
        assertEquals("hi   and   there", transformed.text)
        val ranges = transformed.getStringAnnotations("tag", 0, transformed.length).map { it.start to it.end }
        assertEquals(listOf(3 to 4, 5 to 12), ranges)
    }
}
