package com.beeper.android.messageformat.markdown

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.beeper.android.messageformat.MatrixBodyAnnotations
import com.beeper.android.messageformat.MatrixBodyParseResult
import com.beeper.android.messageformat.MatrixBodyPreFormatStyle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarkdownSourceAnnotatorTest {

    private fun annotate(markdown: String, features: Set<MarkdownFeature> = MarkdownFeature.ALL) =
        MatrixMarkdownParser(features).annotateSource(markdown)

    private fun MatrixBodyParseResult.annotated(tag: String): List<Pair<String, String>> =
        text.getStringAnnotations(tag, 0, text.length).map { text.text.substring(it.start, it.end) to it.item }

    private fun MatrixBodyParseResult.markers(): List<String> =
        annotated(MatrixBodyAnnotations.SYNTAX_MARKER).map { it.first.trimEnd() }

    private fun MatrixBodyParseResult.styled(predicate: (SpanStyle) -> Boolean): List<String> =
        text.spanStyles.filter { predicate(it.item) }.map { text.text.substring(it.start, it.end) }

    @Test
    fun textStaysUntouched() {
        val source = "# Title\n\n**bold** `code` ||secret|| [link](https://x.org) ~~gone~~\n> quote\n- item"
        assertEquals(source, annotate(source).text.text)
    }

    @Test
    fun emphasisStylesContentAndMarksDelimiters() {
        val result = annotate("**bold** _it_ ~~gone~~")
        assertEquals(listOf("bold"), result.styled { it.fontWeight == FontWeight.Bold })
        assertEquals(listOf("it"), result.styled { it.fontStyle == FontStyle.Italic })
        assertEquals(listOf("gone"), result.styled { it.textDecoration == TextDecoration.LineThrough })
        assertEquals(listOf("**", "**", "_", "_", "~~", "~~"), result.markers())
    }

    @Test
    fun singleTildesAreNotStrikethrough() {
        val result = annotate("~~typo, sorry~ ~~gone~~")
        assertEquals(listOf("gone"), result.styled { it.textDecoration == TextDecoration.LineThrough })
        assertEquals(listOf("~~", "~~"), result.markers())
    }

    @Test
    fun unbalancedDelimitersDoNotCrash() {
        listOf("~~~~", "****", "~~a ~~~~", "**a*", "~~", "**").forEach { assertEquals(it, annotate(it).text.text) }
    }

    @Test
    fun disabledEmphasisIsLiteral() {
        val result = annotate("**bold** _it_", setOf(MarkdownFeature.ITALIC))
        assertEquals(emptyList(), result.styled { it.fontWeight == FontWeight.Bold })
        assertEquals(listOf("it"), result.styled { it.fontStyle == FontStyle.Italic })
        assertEquals(listOf("_", "_"), result.markers())
    }

    @Test
    fun nestedContentInsideLiteralEmphasisIsStillAnnotated() {
        val result = annotate("**a `c` b**", setOf(MarkdownFeature.INLINE_CODE, MarkdownFeature.ITALIC))
        assertEquals(listOf("c" to ""), result.annotated(MatrixBodyAnnotations.INLINE_CODE))
        assertEquals(listOf("`", "`"), result.markers())
    }

    @Test
    fun code() {
        val inline = annotate("say `hi`")
        assertEquals(listOf("hi" to ""), inline.annotated(MatrixBodyAnnotations.INLINE_CODE))
        assertEquals(listOf("`", "`"), inline.markers())

        val block = annotate("```kt\nval x\nval y\n```")
        assertEquals(listOf("val x\nval y" to ""), block.annotated(MatrixBodyAnnotations.BLOCK_CODE))
        assertEquals(listOf("```", "kt", "```"), block.markers())
    }

    @Test
    fun headings() {
        val result = annotate("##  Title  ")
        assertEquals(listOf("##  Title" to "h2"), result.annotated(MatrixBodyAnnotations.HEADING))
        assertEquals(listOf("##"), result.markers())
    }

    @Test
    fun links() {
        val inline = annotate("[site](https://x.org)")
        assertEquals(listOf("site" to "https://x.org"), inline.annotated(MatrixBodyAnnotations.WEB_LINK))
        assertEquals(listOf("[", "](https://x.org)"), inline.markers())

        val auto = annotate("<https://x.org>")
        assertEquals(listOf("https://x.org" to "https://x.org"), auto.annotated(MatrixBodyAnnotations.WEB_LINK))
        assertEquals(listOf("<", ">"), auto.markers())
    }

    @Test
    fun plainLinks() {
        val plain = annotate("see https://x.org/a, or mail me@x.org")
        assertEquals(listOf("https://x.org/a" to "https://x.org/a"), plain.annotated(MatrixBodyAnnotations.WEB_LINK))
        assertEquals(listOf("me@x.org" to "me@x.org"), plain.annotated(MatrixBodyAnnotations.MAIL_ADDRESS))

        val emphasized = annotate("**https://x.org/a** and *https://y.org/b*")
        assertEquals(
            listOf("https://x.org/a", "https://y.org/b"),
            emphasized.annotated(MatrixBodyAnnotations.WEB_LINK).map { it.first },
        )

        val alias = annotate("join #room:x.org")
        assertEquals(listOf("#room:x.org"), alias.annotated(MatrixBodyAnnotations.ROOM_LINK).map { it.first })
    }

    @Test
    fun plainLinksAreNotSearchedInLinksAndCode() {
        val code = annotate("`https://x.org` ![https://i.org](mxc://x.org/i)\n```\nhttps://y.org\n```")
        assertEquals(emptyList(), code.annotated(MatrixBodyAnnotations.WEB_LINK))

        val links = annotate("[https://a.org](https://x.org) <https://y.org>")
        assertEquals(
            listOf("https://a.org" to "https://x.org", "https://y.org" to "https://y.org"),
            links.annotated(MatrixBodyAnnotations.WEB_LINK),
        )
    }

    @Test
    fun plainLinksAreNotSearchedInEntities() {
        val mention = MatrixInlineEntity.UserMention(0, 13, "@alice:x.org")
        val result = MatrixMarkdownParser(MarkdownFeature.ALL).annotateSource("https://x.org https://y.org", listOf(mention))
        assertEquals(listOf("https://y.org"), result.annotated(MatrixBodyAnnotations.WEB_LINK).map { it.first })
    }

    @Test
    fun plainLinksFollowThePreFormatStyle() {
        val style = MatrixBodyPreFormatStyle(autoLinkUrlPattern = null)
        val result = MatrixMarkdownParser(MarkdownFeature.ALL, style).annotateSource("https://x.org me@x.org")
        assertEquals(emptyList(), result.annotated(MatrixBodyAnnotations.WEB_LINK))
        assertEquals(listOf("me@x.org"), result.annotated(MatrixBodyAnnotations.MAIL_ADDRESS).map { it.first })
    }

    @Test
    fun addressLikeUrlPathStaysPartOfTheUrl() {
        val url = "https://mastodon.social/@alice@example.org/115555"
        val result = annotate("$url and bob@example.org")
        assertEquals(listOf(url), result.annotated(MatrixBodyAnnotations.WEB_LINK).map { it.first })
        assertEquals(listOf("bob@example.org"), result.annotated(MatrixBodyAnnotations.MAIL_ADDRESS).map { it.first })
    }

    @Test
    fun spoilers() {
        val plain = annotate("||secret||")
        assertEquals(listOf("secret"), plain.annotated(MatrixBodyAnnotations.SPAN).map { it.first })
        assertTrue(plain.annotated(MatrixBodyAnnotations.SPAN).single().second.contains("\"isSpoiler\":true"))
        assertEquals(listOf("||", "||"), plain.markers())
        assertEquals(setOf(2), plain.expandableItems)

        val withReason = annotate("||why|secret||")
        assertEquals(listOf("secret"), withReason.annotated(MatrixBodyAnnotations.SPAN).map { it.first })
        assertEquals(listOf("||", "||", "why|"), withReason.markers())
    }

    @Test
    fun quoteMarkersAreNotSyntaxWhenQuotesAreDisabled() {
        assertEquals(emptyList(), annotate("> q", emptySet()).markers())
    }

    @Test
    fun blockMarkersOnlyMarkTheirSyntax() {
        val result = annotate("> quote\n- item\n1. first\n| a |\n|---|\n| b |")
        assertEquals(listOf(">", "-", "1.", "|", "|", "|---|", "|", "|"), result.markers())
        assertEquals(emptyList(), result.annotated(MatrixBodyAnnotations.BLOCK_QUOTE))
        assertEquals(emptyList(), result.annotated(MatrixBodyAnnotations.UNORDERED_LIST_ITEM))
        assertEquals(emptyList(), result.annotated(MatrixBodyAnnotations.ORDERED_LIST_ITEM))
    }

    @Test
    fun markersCarryTheirFeature() {
        val result = annotate(
            "# h\n\n**b** _i_ ~~s~~ ||x|| `c` [l](https://x.org) <https://y.org> ![a](mxc://x.org/i)\n\n---\n\n" +
                "> q\n- u\n1. o\n| a |\n|---|\n| b |\n\n```\ncode\n```",
        )
        val markers = result.annotated(MatrixBodyAnnotations.SYNTAX_MARKER)
            .map { (text, feature) -> text.trimEnd() to MarkdownFeature.valueOf(feature) }
        assertEquals(MarkdownFeature.ALL, markers.map { it.second }.toSet())
        assertEquals(
            listOf(
                "#" to MarkdownFeature.HEADING,
                "**" to MarkdownFeature.BOLD,
                "**" to MarkdownFeature.BOLD,
                "_" to MarkdownFeature.ITALIC,
                "_" to MarkdownFeature.ITALIC,
                "~~" to MarkdownFeature.STRIKETHROUGH,
                "~~" to MarkdownFeature.STRIKETHROUGH,
                "||" to MarkdownFeature.SPOILER,
                "||" to MarkdownFeature.SPOILER,
                "`" to MarkdownFeature.INLINE_CODE,
                "`" to MarkdownFeature.INLINE_CODE,
                "[" to MarkdownFeature.INLINE_LINK,
                "](https://x.org)" to MarkdownFeature.INLINE_LINK,
                "<" to MarkdownFeature.AUTOLINK,
                ">" to MarkdownFeature.AUTOLINK,
                "![" to MarkdownFeature.IMAGE,
                "](mxc://x.org/i)" to MarkdownFeature.IMAGE,
                "---" to MarkdownFeature.HORIZONTAL_RULE,
                ">" to MarkdownFeature.BLOCKQUOTE,
                "-" to MarkdownFeature.UNORDERED_LIST,
                "1." to MarkdownFeature.ORDERED_LIST,
                "|" to MarkdownFeature.TABLE,
                "|" to MarkdownFeature.TABLE,
                "|---|" to MarkdownFeature.TABLE,
                "|" to MarkdownFeature.TABLE,
                "|" to MarkdownFeature.TABLE,
                "```" to MarkdownFeature.CODE_BLOCK,
                "```" to MarkdownFeature.CODE_BLOCK,
            ),
            markers,
        )
    }
}
