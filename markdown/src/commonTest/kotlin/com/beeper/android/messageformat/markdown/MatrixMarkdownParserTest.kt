package com.beeper.android.messageformat.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class MatrixMarkdownParserTest {

    private fun html(markdown: String, features: Set<MarkdownFeature> = MarkdownFeature.ALL): String =
        MatrixMarkdownParser(features).toHtml(markdown)

    @Test
    fun newlinesBecomeLineBreaksInsideEmphasis() {
        assertEquals("<strong>one<br>two</strong>", html("**one\ntwo**"))
    }

    @Test
    fun emphasis() {
        assertEquals("**bold** <em>italic</em>", html("**bold** _italic_", setOf(MarkdownFeature.ITALIC)))
        assertEquals("<strong>bold</strong> _italic_", html("**bold** _italic_", setOf(MarkdownFeature.BOLD)))
        assertEquals("**bold** _italic_", html("**bold** _italic_", emptySet()))
    }

    @Test
    fun strikethrough() {
        assertEquals("a ~~~~ and ****", html("a ~~~~ and ****"))
        assertEquals("~~a ~~~~", html("~~a ~~~~"))
        assertEquals("~~gone~~", html("~~gone~~", emptySet()))
    }

    @Test
    fun spoilers() {
        assertEquals("||secret||", html("||secret||", emptySet()))
    }

    @Test
    fun inlineCode() {
        assertEquals("`x`", html("`x`", emptySet()))
    }

    @Test
    fun codeBlocks() {
        assertEquals("```<br>x<br>```", html("```\nx\n```", emptySet()))
    }

    @Test
    fun links() {
        assertEquals("[site](https://example.org/)", html("[site](https://example.org/)", emptySet()))
        assertEquals("&lt;https://example.org/&gt;", html("<https://example.org/>", emptySet()))
    }

    @Test
    fun imagesAndCustomEmotes() {
        assertEquals(
            "<img src=\"mxc://x.org/abc\" alt=\"a cat\" title=\"meow\" data-mx-emoticon height=\"32\">",
            html("![:meow:](mxc://x.org/abc \"Emoji: a cat\")"),
        )
        assertEquals("![alt](https://x.org/a.png)", html("![alt](https://x.org/a.png)", emptySet()))
    }

    @Test
    fun imageAttributesAreEscapedOnce() {
        assertEquals(
            "<img src=\"mxc://a/b\" alt=\"a &amp; b\" title=\"c &amp; d\">",
            html("![a & b](mxc://a/b \"c & d\")"),
        )
        assertEquals(
            "<img src=\"mxc://a/b?c&amp;d\" alt=\"a &amp; b\" title=\"x\" data-mx-emoticon height=\"32\">",
            html("![:x:](mxc://a/b?c&d \"Emoji: a & b\")"),
        )
    }

    @Test
    fun horizontalRules() {
        assertEquals("a<br>---", html("a\n---", emptySet()))
    }

    @Test
    fun escapedBackslashKeepsSpoiler() {
        assertEquals("a \\<span data-mx-spoiler>test</span>", html("a \\\\||test||"))
    }

    @Test
    fun blockquotes() {
        assertEquals("&gt; quoted", html("> quoted", emptySet()))
        assertEquals("a<br>&gt; b", html("a\n> b", emptySet()))
    }

    @Test
    fun headings() {
        assertEquals("## title", html("## title", emptySet()))
    }

    @Test
    fun listsFollowTheirOwnFeature() {
        assertEquals("- a<br>- b", html("- a\n- b", setOf(MarkdownFeature.ORDERED_LIST)))
        assertEquals("1. a<br>2. b", html("1. a\n2. b", setOf(MarkdownFeature.UNORDERED_LIST)))
    }
}
