package com.beeper.android.messageformat.markdown

import com.beeper.android.messageformat.InlineImageInfo
import kotlin.test.Test
import kotlin.test.assertEquals

class HtmlToMarkdownTest {

    private val parser = MatrixMarkdownParser(MarkdownFeature.ALL)

    /** Asserts that [markdown] is what the composer gets back for the HTML it sends for [markdown]. */
    private fun assertRoundTrip(markdown: String, vararg entities: MatrixInlineEntity, parser: MatrixMarkdownParser = this.parser) {
        val content = MatrixMarkdownContent(markdown, entities.toList())
        val html = parser.toHtml(content)
        val parsed = parser.fromHtml(html)
        assertEquals(content, parsed, "HTML: $html")
        assertEquals(html, parser.toHtml(parsed))
    }

    private fun markdown(html: String, parser: MatrixMarkdownParser = this.parser) = parser.fromHtml(html).text

    @Test
    fun literalMarkdownCharactersStayLiteral() {
        assertEquals("**Note:** 2\\*3\\*4 = 24", markdown("<strong>Note:</strong> 2*3*4 = 24"))
        assertRoundTrip("**Note:** 2\\*3\\*4 = 24")
        assertRoundTrip("snake_case and \\_\\_init\\_\\_ and a \\`tick\\`")
        assertRoundTrip("\\~\\~not struck\\~\\~ and \\|\\|not hidden\\|\\|")
        assertRoundTrip("[not a link\\](https://example.org) and \\<https://example.org>")
        assertRoundTrip("a backslash \\\\\\* before a star, C:\\Users stays")
    }

    @Test
    fun charactersThatCantFormatStayUnescaped() {
        assertRoundTrip("2 * 3 * 4 and a_b_c and [brackets] and ~5€ and a | b and a < b > c")
    }

    @Test
    fun lineStartsThatWouldStartBlocksAreEscaped() {
        assertRoundTrip("\\# not a heading")
        assertRoundTrip("\\> not a quote")
        assertRoundTrip("\\- not a list")
        assertRoundTrip("1\\. not a list")
        assertRoundTrip("\\---")
        assertRoundTrip("line\n\\- not a list either")
        assertRoundTrip("#hashtag and 2024. was a year")
    }

    @Test
    fun formatting() {
        assertRoundTrip("**bold** *italic* ~~struck~~ `code` ||spoiler|| ||reason|spoiler||")
        assertRoundTrip("**bold *and italic***")
        assertRoundTrip("one\ntwo\n\nnew paragraph")
        assertRoundTrip("``code with ` tick``")
    }

    @Test
    fun whitespaceStaysOutsideOfFormatting() {
        assertEquals("a **b** c", markdown("a<strong> b </strong>c"))
    }

    @Test
    fun links() {
        assertRoundTrip("[text](https://example.org/a_b \"title\")")
        assertRoundTrip("[parens](https://example.org/a_\\(b\\)%20c)")
        assertRoundTrip("[**bold** link](https://example.org)")
        assertRoundTrip("[room](https://matrix.to/#/#room:example.org)")
        assertEquals("see https://example.org/a_b", markdown("see <a href=\"https://example.org/a_b\">https://example.org/a_b</a>"))
    }

    @Test
    fun blocks() {
        assertRoundTrip("# Heading\n\n### Smaller *heading*")
        assertRoundTrip("> quote\n> lines\n>\n> - with a list")
        assertRoundTrip("- one\n- two\n  - nested\n- three")
        assertRoundTrip("3. three\n4. four\n7. seven")
        assertRoundTrip("- loose\n\n- list")
        assertRoundTrip("```kotlin\nval a = \"**not bold**\"\n```")
        assertRoundTrip("````\n```\n````")
        assertRoundTrip("before\n\n---\n\nafter")
        assertRoundTrip("| a | b\\|c |\n| --- | --- |\n| **1** |  |")
    }

    @Test
    fun mentionsAndImagesBecomeEntities() {
        assertRoundTrip("hi Bob [away] ping", MatrixInlineEntity.UserMention(3, 13, "@bob:example.org"))
        assertRoundTrip("**Alice**", MatrixInlineEntity.UserMention(2, 7, "@alice:example.org"))
        assertRoundTrip(
            "an :cat: emote",
            // The sent size keeps the image's aspect ratio at the sent height
            MatrixInlineEntity.InlineImage.customEmote(3, 8, "cat", "mxc://x/cat", body = "A cat", width = 64, height = 32),
        )
        val image = InlineImageInfo("mxc://x/photo", isEmote = false, width = 300, height = 200, title = null, alt = "photo")
        assertRoundTrip("a photo", MatrixInlineEntity.InlineImage(2, 7, image))
    }

    @Test
    fun webImagesStayMarkdownImages() {
        assertRoundTrip("a ![web *image*](https://example.org/a.png \"title\")")
    }

    @Test
    fun mautrixEmotes() {
        val content = parser.fromHtml("<img src=\"mxc://x/cat\" alt=\":cat:\" title=\"Emoji: A cat\" data-mx-emoticon height=\"32\">")
        assertEquals(":cat:", content.text)
        val emote = content.entities.single() as MatrixInlineEntity.InlineImage
        assertEquals("cat", emote.image.emoteShortcode)
    }

    @Test
    fun formattingWithoutMarkdownBecomesText() {
        assertEquals("under x2 red", markdown("<u>under</u> x<sup>2</sup> <font color=\"red\">red</font>"))
        assertEquals("Summary\n\nhidden", markdown("<details><summary>Summary</summary>hidden</details>"))
        val noImages = MatrixMarkdownParser(MarkdownFeature.ALL - MarkdownFeature.IMAGE)
        assertEquals("a web image", markdown("a <img src=\"https://example.org/a.png\" alt=\"web image\">", noImages))
    }

    @Test
    fun foreignHtml() {
        assertEquals("reply", markdown("<mx-reply><blockquote>quoted</blockquote></mx-reply>reply"))
        assertEquals("a\n\nb", markdown("<p>a</p>\n<p>b</p>\n"))
        assertEquals("one two", markdown("one\n  two"))
        assertEquals("| a | b |\n| --- | --- |\n| c | d |", markdown("<table><tr><td>a</td><td>b</td></tr><tr><td>c</td><td>d</td></tr></table>"))
    }

    @Test
    fun disabledFeaturesStayText() {
        val plain = MatrixMarkdownParser(setOf(MarkdownFeature.BOLD))
        assertEquals("**bold** # a\\*b\\*c code", markdown("<strong>bold</strong> # a*b*c <code>code</code>", plain))
        assertEquals("heading", markdown("<h1>heading</h1>", plain))
        val none = MatrixMarkdownParser(emptySet())
        assertEquals("a*b* \\# x", markdown("a*b* \\# x", none))
    }
}
