package com.beeper.android.messageformat.markdown

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.beeper.android.messageformat.MatrixBodyAnnotations
import com.beeper.android.messageformat.MatrixBodyParseResult
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
}
