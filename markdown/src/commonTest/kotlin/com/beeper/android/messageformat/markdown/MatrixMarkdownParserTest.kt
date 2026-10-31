package com.beeper.android.messageformat.markdown

import kotlin.test.Test
import kotlin.test.assertEquals

class MatrixMarkdownParserTest {

    private fun html(markdown: String, features: Set<MarkdownFeature> = MarkdownFeature.ALL): String =
        MatrixMarkdownParser(features).toHtml(markdown)

    @Test
    fun plainTextStaysUnwrapped() {
        assertEquals("hello world", html("hello world"))
    }

    @Test
    fun paragraphsAreWrappedOnlyWhenThereAreSeveral() {
        assertEquals("<p>one</p><p>two</p>", html("one\n\ntwo"))
    }

    @Test
    fun newlinesBecomeLineBreaks() {
        assertEquals("one<br>two", html("one\ntwo"))
        assertEquals("one<br>two", html("one  \ntwo"))
        assertEquals("<strong>one<br>two</strong>", html("**one\ntwo**"))
    }

    @Test
    fun emphasis() {
        assertEquals("<strong>bold</strong> <em>italic</em>", html("**bold** _italic_"))
        assertEquals("**bold** <em>italic</em>", html("**bold** _italic_", setOf(MarkdownFeature.ITALIC)))
        assertEquals("<strong>bold</strong> _italic_", html("**bold** _italic_", setOf(MarkdownFeature.BOLD)))
        assertEquals("**bold** _italic_", html("**bold** _italic_", emptySet()))
    }

    @Test
    fun strikethrough() {
        assertEquals("a ~~~~ and ****", html("a ~~~~ and ****"))
        assertEquals("~~a ~~~~", html("~~a ~~~~"))
        assertEquals("<del>gone</del>", html("~~gone~~"))
        assertEquals("~~gone~~", html("~~gone~~", emptySet()))
    }

    @Test
    fun spoilers() {
        assertEquals("<span data-mx-spoiler>secret</span>", html("||secret||"))
        assertEquals("a <span data-mx-spoiler>secret</span> b", html("a ||secret|| b"))
        assertEquals("<span data-mx-spoiler=\"why\">secret</span>", html("||why|secret||"))
        assertEquals("<span data-mx-spoiler>a <strong>b</strong></span>", html("||a **b**||"))
        assertEquals("|not a spoiler|", html("|not a spoiler|"))
        assertEquals("|||three|||", html("|||three|||"))
        assertEquals("||secret||", html("||secret||", emptySet()))
    }

    @Test
    fun inlineCodeKeepsMarkupLiteral() {
        assertEquals("<code>**x**</code>", html("`**x**`"))
        assertEquals("`x`", html("`x`", emptySet()))
    }

    @Test
    fun codeBlocks() {
        assertEquals("<pre><code class=\"language-kotlin\">val x = 1\n</code></pre>", html("```kotlin\nval x = 1\n```"))
        assertEquals("<pre><code>a\n\nb\n</code></pre>", html("```\na\n\nb\n```"))
        assertEquals("```<br>x<br>```", html("```\nx\n```", emptySet()))
    }

    @Test
    fun relaxedBacktickFences() {
        assertEquals("<pre><code>Chemistry:\nH2O\n</code></pre>", html("```Chemistry:\nH2O\n```"))
        assertEquals("<pre><code>python extra\nx\n</code></pre>", html("```python extra\nx\n```"))
        assertEquals("<pre><code class=\"language-foo\">bar\n</code></pre>", html("```foo\nbar```"))
        assertEquals("<pre><code>a\nb \n</code></pre>", html("```\na\nb ```"))
        assertEquals("```<br>unclosed", html("```\nunclosed"))
        assertEquals("<pre><code>unclosed\n</code></pre>", html("~~~\nunclosed"))
        assertEquals("<blockquote><pre><code>x\n</code></pre></blockquote>", html("> ```\n> x\n> ```"))
    }

    @Test
    fun rawHtmlIsEscaped() {
        assertEquals("&lt;b&gt;x&lt;/b&gt; &amp; &lt;script&gt;", html("<b>x</b> & <script>"))
    }

    @Test
    fun links() {
        assertEquals("<a href=\"https://example.org/\">site</a>", html("[site](https://example.org/)"))
        assertEquals("<a href=\"https://example.org/\">https://example.org/</a>", html("<https://example.org/>"))
        assertEquals("[site](https://example.org/)", html("[site](https://example.org/)", emptySet()))
        assertEquals("<a href=\"mailto:mail@example.org\">mail@example.org</a>", html("<mail@example.org>"))
        assertEquals("&lt;https://example.org/&gt;", html("<https://example.org/>", emptySet()))
    }

    @Test
    fun imagesAndCustomEmotes() {
        assertEquals("<img src=\"https://x.org/a.png\" alt=\"alt\">", html("![alt](https://x.org/a.png)"))
        assertEquals(
            "<img src=\"mxc://x.org/abc\" alt=\"meow\" title=\"meow\" data-mx-emoticon height=\"32\">",
            html("![:meow:](mxc://x.org/abc \"Emoji: meow\")"),
        )
        assertEquals(
            "<img src=\"mxc://x.org/abc\" alt=\"a cat\" title=\"meow\" data-mx-emoticon height=\"32\">",
            html("![:meow:](mxc://x.org/abc \"Emoji: a cat\")"),
        )
        assertEquals("![alt](https://x.org/a.png)", html("![alt](https://x.org/a.png)", emptySet()))
    }

    @Test
    fun horizontalRules() {
        assertEquals("<p>a</p><hr><p>b</p>", html("a\n\n---\n\nb"))
        assertEquals("<p>title</p><hr>", html("title\n---"))
        assertEquals("a<br>---", html("a\n---", emptySet()))
    }

    @Test
    fun escapedPipesAreLiteral() {
        assertEquals("a ||test||", html("a \\||test||"))
    }

    @Test
    fun blockquotes() {
        assertEquals("<blockquote><p>quoted<br>more</p></blockquote>", html("> quoted\n> more"))
        assertEquals("&gt; quoted", html("> quoted", emptySet()))
        assertEquals("a &gt; b", html("a > b"))
        assertEquals("a<br>&gt; b", html("a\n> b", emptySet()))
    }

    @Test
    fun headings() {
        assertEquals("<h2>title</h2>", html("## title"))
        assertEquals("## title", html("## title", emptySet()))
    }

    @Test
    fun unorderedLists() {
        assertEquals("<ul><li>a</li><li>b</li></ul>", html("- a\n- b"))
        assertEquals("<ul><li>a</li><li>b</li></ul>", html("* a\n* b"))
        assertEquals("+ a<br>+ b", html("+ a\n+ b"))
        assertEquals("- a<br>- b", html("- a\n- b", setOf(MarkdownFeature.ORDERED_LIST)))
    }

    @Test
    fun orderedLists() {
        assertEquals("<ol><li>a</li><li>b</li></ol>", html("1. a\n2. b"))
        assertEquals("<ol start=\"3\"><li>a</li><li>b</li></ol>", html("3. a\n4. b"))
        assertEquals("<ol><li>a</li><li value=\"5\">b</li><li>c</li></ol>", html("1. a\n5. b\n6. c"))
        assertEquals("1. a<br>2. b", html("1. a\n2. b", setOf(MarkdownFeature.UNORDERED_LIST)))
    }

    @Test
    fun looseListsKeepParagraphs() {
        assertEquals("<ul><li><p>a</p></li><li><p>b</p></li></ul>", html("- a\n\n- b"))
    }

    @Test
    fun tables() {
        assertEquals(
            "<table><thead><tr><th>a</th><th>b</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>",
            html("| a | b |\n|---|---|\n| 1 | 2 |"),
        )
    }

    @Test
    fun noIndentedCodeOrSetextHeadings() {
        assertEquals("indented", html("    indented"))
        assertEquals("title<br>===", html("title\n==="))
    }
}
