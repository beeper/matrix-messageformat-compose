package com.beeper.android.messageformat.markdown

import androidx.compose.ui.text.font.FontWeight
import com.beeper.android.messageformat.MatrixBodyAnnotations
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatrixInlineEntityTest {

    private val parser = MatrixMarkdownParser(MarkdownFeature.ALL)
    private val alice = "https://matrix.to/#/@alice:example.org"

    @Test
    fun mentionsBecomeLinksAndEmotesBecomeImages() {
        val text = "hi @Alice and :catjam: and @room"
        val entities = listOf(
            MatrixInlineEntity.UserMention(3, 9, "@alice:example.org"),
            MatrixInlineEntity.CustomEmote(14, 22, "catjam", "mxc://x/cat"),
            MatrixInlineEntity.RoomMention(27, 32),
        )
        assertEquals(
            "hi <a href=\"$alice\">@Alice</a> and <img src=\"mxc://x/cat\" alt=\":catjam:\" title=\"catjam\"" +
                " data-mx-emoticon height=\"32\"> and @room",
            parser.toHtml(text, entities),
        )
    }

    @Test
    fun entityTextIsNeverParsedAsMarkdown() {
        val text = "**@_bob_** and @*star*"
        val entities = listOf(
            MatrixInlineEntity.UserMention(2, 8, "@bob:example.org"),
            MatrixInlineEntity.UserMention(15, 22, "@star:example.org"),
        )
        assertEquals(
            "<strong><a href=\"https://matrix.to/#/@bob:example.org\">@_bob_</a></strong> and " +
                "<a href=\"https://matrix.to/#/@star:example.org\">@*star*</a>",
            parser.toHtml(text, entities),
        )
    }

    @Test
    fun entitiesInsideCodeRenderLiterally() {
        val text = "`@Alice` and\n```\n@Alice\n```"
        val entities = listOf(
            MatrixInlineEntity.UserMention(1, 7, "@alice:example.org"),
            MatrixInlineEntity.UserMention(17, 23, "@alice:example.org"),
        )
        assertEquals("<p><code>@Alice</code> and</p><pre><code>@Alice\n</code></pre>", parser.toHtml(text, entities))
    }

    @Test
    fun contentTextOverridesLinkText() {
        val mention = MatrixInlineEntity.UserMention(0, 6, "@alice:example.org", contentText = "Alice Liddell")
        val html = parser.toHtml("@Alice", listOf(mention))
        assertEquals("<a href=\"$alice\">Alice Liddell</a>", html)
    }

    @Test
    fun invalidEntitiesAreIgnored() {
        val text = "@Alice"
        val entities = listOf(
            MatrixInlineEntity.UserMention(0, 6, "@alice:example.org"),
            MatrixInlineEntity.UserMention(3, 9, "@overlap:example.org"),
            MatrixInlineEntity.UserMention(20, 25, "@outside:example.org"),
        )
        assertEquals("<a href=\"$alice\">@Alice</a>", parser.toHtml(text, entities))
    }

    @Test
    fun sourceAnnotationDescribesCustomEmotesAsInlineImages() {
        val text = "hi :cat:"
        val result = parser.annotateSource(text, listOf(MatrixInlineEntity.CustomEmote(3, 8, "cat", "mxc://x/cat")))
        val annotation = result.text.getStringAnnotations(MatrixBodyAnnotations.INLINE_IMAGE, 0, text.length).single()
        assertEquals(3 to 8, annotation.start to annotation.end)
        val image = result.inlineImages.getValue(annotation.item)
        assertEquals("mxc://x/cat", image.uri)
        assertEquals("cat", image.title)
        assertEquals(":cat:", image.alt)
        assertTrue(image.isEmote)
    }

    @Test
    fun privateUseCharactersInTheTextSurviveMasking() {
        val text = "\uE000 @Alice \uE001"
        val html = parser.toHtml(text, listOf(MatrixInlineEntity.UserMention(2, 8, "@alice:example.org")))
        assertEquals("\uE000 <a href=\"$alice\">@Alice</a> \uE001", html)
    }

    @Test
    fun draftStaysPlainWithoutFormatting() {
        assertEquals(null, parser.toDraft("a & b < c").formattedBody)
        val mention = MatrixInlineEntity.UserMention(3, 9, "@alice:example.org")
        val content = parser.toDraft("hi @Alice", listOf(mention))
        assertEquals("hi <a href=\"$alice\">@Alice</a>", content.formattedBody)
        assertEquals(listOf("@alice:example.org"), content.mentionedUserIds)
    }

    @Test
    fun sourceAnnotationKeepsTextAndMarksMentions() {
        val text = "**@_bob_** hi"
        val result = parser.annotateSource(text, listOf(MatrixInlineEntity.UserMention(2, 8, "@bob:example.org")))
        assertEquals(text, result.text.text)
        val bold = result.text.spanStyles.filter { it.item.fontWeight == FontWeight.Bold }
        assertEquals(listOf("@_bob_"), bold.map { text.substring(it.start, it.end) })
        val mention = result.text.getStringAnnotations(MatrixBodyAnnotations.USER_MENTION, 0, text.length).single()
        assertEquals(2 to 8, mention.start to mention.end)
        assertTrue(mention.item.contains("@bob:example.org"))
    }
}
