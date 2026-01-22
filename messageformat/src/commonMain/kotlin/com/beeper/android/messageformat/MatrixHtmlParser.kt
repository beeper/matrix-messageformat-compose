package com.beeper.android.messageformat

import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.AnnotatedString.Builder
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withAnnotation
import androidx.compose.ui.text.withStyle
import co.touchlab.kermit.Logger
import com.googlecode.htmlcompressor.compressor.HtmlCompressor
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.serialization.json.Json
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

internal const val VERBOSE_DBG = false

const val MENTION_ROOM = "@room"

typealias Lookahead = List<Node>

/**
 * Parse `formatted_body` Matrix HTML into [AnnotatedString] for rendering, first step without any styling that may be
 * theme- or density-related. Second step is handled by [MatrixBodyStyledFormatter].
 * Supported tags as per Matrix spec: https://spec.matrix.org/unstable/client-server-api/#mroommessage-msgtypes
 */
class MatrixHtmlParser(
    private val newlineDbg: Boolean = false,
) {
    private val log = Logger.withTag("MatrixBodyParser")
    private val htmlCompressor = HtmlCompressor()

    // Note, some fields in here may actually mutable (mostly for numbered lists)
    private data class RenderContext(
        val style: MatrixBodyPreFormatStyle,
        val allowRoomMention: Boolean,
        val unorderedListScope: UnorderedListScope? = null,
        val orderedListScope: OrderedListScope? = null,
        val preFormattedText: Boolean = false, // <pre> element
        val linkUrl: String? = null,
        val pendingBlankText: String? = null,
        val indentedBlockDepth: Int = 0,
    ) {
        val shouldIgnoreWhitespace: Boolean
            get() = !preFormattedText && (unorderedListScope != null || orderedListScope != null)
    }

    private data class RenderResultMeta(
        val inlineImages: MutableMap<String, InlineImageInfo> = mutableMapOf(),
        val expandableItems: MutableSet<Int> = mutableSetOf(),
    ) {
        fun toResult(text: AnnotatedString) = MatrixBodyParseResult(
            text = text,
            inlineImages = inlineImages.toPersistentMap(),
            expandableItems = expandableItems.toPersistentSet(),
        )
    }

    private data class PreviousRenderedInfo(
        val nextShouldTrimBlank: Boolean = false,
        // If MatrixBodyStyledFormatter will add a paragraph style, it will ensure a newline for us,
        // in which case we sometimes may want to omit explicit newlines.
        val hasImplicitNewline: Boolean = false,
    )

    private data class OrderedListScope(
        var nextNumber: Int = 1,
    )

    data class UnorderedListScope(
        val bullet: String,
    )

    /** Parse a `formatted_body` assuming `org.matrix.custom.html` format into a pre-processed annotated string, ready for further styling. */
    fun parseHtml(
        input: String,
        style: MatrixBodyPreFormatStyle,
        allowRoomMention: Boolean,
    ): MatrixBodyParseResult {
        val ts = System.currentTimeMillis()
        if (input.isBlank()) return MatrixBodyParseResult(AnnotatedString(""))
        val compressed = htmlCompressor.compress(input)
            .replace("<br> ", "<br>")
            .replace("<br/> ", "<br/>")
            .replace("<p> ", "<p>")
        val doc = Jsoup.parseBodyFragment(compressed)
        val body = doc.body()
        val jsoupTs = System.currentTimeMillis()
        val resultMeta = RenderResultMeta()
        val annotatedString = buildAnnotatedString {
            val ctx = RenderContext(style, allowRoomMention)
            appendNodes(body.childNodes(), ctx, resultMeta)
        }.also {
            if (VERBOSE_DBG) {
                val now = System.currentTimeMillis()
                log.v("Parsed in ${now - ts} (${jsoupTs - ts} + ${now - jsoupTs}) ms; len=${input.length}->${it.length}; thread ${Thread.currentThread().name}")
            }
        }
        return resultMeta.toResult(annotatedString)
    }

    /** Similar to [parseHtml] but for `body` plaintext rendering. Still useful for auto-linkification and highlighting @room mentions. */
    fun parsePlaintext(
        input: String,
        style: MatrixBodyPreFormatStyle,
        allowRoomMention: Boolean,
    ): MatrixBodyParseResult {
        val annotatedString = buildAnnotatedString {
            appendTextContent(input, RenderContext(style, allowRoomMention))
        }
        return MatrixBodyParseResult(annotatedString)
    }

    private fun Builder.appendNodes(
        nodes: List<Node>,
        ctx: RenderContext,
        resultMeta: RenderResultMeta,
        firstPreviousRenderedInfo: PreviousRenderedInfo? = null,
    ): PreviousRenderedInfo? {
        var previousRenderedInfo: PreviousRenderedInfo? = firstPreviousRenderedInfo
        nodes.forEachIndexed { index, node ->
            previousRenderedInfo = appendNode(node, nodes.subList(index+1, nodes.size), previousRenderedInfo, ctx, resultMeta)
                ?: previousRenderedInfo
        }
        return previousRenderedInfo
    }

    private fun Builder.appendNode(
        node: Node,
        lookahead: Lookahead,
        previousRenderedInfo: PreviousRenderedInfo?,
        ctx: RenderContext,
        resultMeta: RenderResultMeta,
    ): PreviousRenderedInfo? {
        return when (node) {
            is TextNode -> appendText(node, lookahead, previousRenderedInfo, ctx)
            is Element -> appendElement(node, lookahead, previousRenderedInfo, ctx, resultMeta)
            else -> null
        }
    }

    private fun Builder.appendText(
        node: TextNode,
        lookahead: Lookahead,
        previous: PreviousRenderedInfo?,
        ctx: RenderContext,
    ): PreviousRenderedInfo? {
        if (ctx.shouldIgnoreWhitespace && node.isBlank) {
            return null
        }
        if (!ctx.preFormattedText && node.isBlank && (lookahead.shouldTrimEncompassingWhitespace() || previous?.nextShouldTrimBlank != false)) {
            return null
        }
        val text = if (ctx.preFormattedText) {
            if (lookahead.isEmpty()) {
                node.wholeText.removeSuffix("\n")
            } else {
                node.wholeText
            }
        } else if (previous?.nextShouldTrimBlank == true) {
            node.text().trimStart()
        } else {
            node.text()
        }.let {
            if (newlineDbg) {
                it.replace("\n", "[T]\n")
            } else {
                it
            }
        }
        appendTextContent(text, ctx)
        return PreviousRenderedInfo()
    }

    private fun Builder.appendTextContent(
        text: String,
        ctx: RenderContext,
    ) {
        if (ctx.allowRoomMention) {
            // Replace any occurrence of "@room" in the text with style.formatRoomMention()
            var currentIndex = 0
            var roomMentionIndex = text.indexOf(MENTION_ROOM)
            while (roomMentionIndex >= 0) {
                appendTextWithAutoLinkify(text.substring(currentIndex, roomMentionIndex), ctx)
                withAnnotation(MatrixBodyAnnotations.ROOM_MENTION, "") {
                    append(ctx.style.formatRoomMention())
                }
                currentIndex = roomMentionIndex + MENTION_ROOM.length
                roomMentionIndex = text.indexOf(MENTION_ROOM, currentIndex)
            }
            appendTextWithAutoLinkify(text.substring(currentIndex, text.length), ctx)
        } else {
            appendTextWithAutoLinkify(text, ctx)
        }
    }

    private fun Builder.appendTextWithAutoLinkify(text: String, ctx: RenderContext) {
        val appendStart = length
        append(text)
        if (ctx.linkUrl == null && ctx.style.autoLinkUrlPattern != null) {
            val matcher = ctx.style.autoLinkUrlPattern.matcher(text)
            while (matcher.find()) {
                val startInText = matcher.start()
                val endInText = matcher.end()
                val url = text.substring(startInText, endInText)
                // Handle matrix.to links specifically for user mentions and room / message links
                val matrixLink = MatrixPatterns.parseMatrixToUrl(url)
                // Custom formatting of auto-linked url contents disabled for now - let's call it a feature, maybe it wasn't supposed to be a link?
                val tag = when (matrixLink) {
                    is MatrixToLink.UserMention -> MatrixBodyAnnotations.USER_MENTION
                    is MatrixToLink.RoomLink -> MatrixBodyAnnotations.ROOM_LINK
                    is MatrixToLink.MessageLink -> MatrixBodyAnnotations.MESSAGE_LINK
                    null -> MatrixBodyAnnotations.WEB_LINK
                }
                addStringAnnotation(
                    tag,
                    matrixLink?.let { Json.encodeToString(it) } ?: url,
                    appendStart + startInText,
                    appendStart + endInText,
                )
            }
        }
    }

    private fun Builder.appendHeading(
        el: Element,
        ctx: RenderContext,
        resultMeta: RenderResultMeta,
    ): PreviousRenderedInfo {
        // No need to add additional newlines when adding a paragraph style!
        withAnnotation(MatrixBodyAnnotations.HEADING, el.normalName()) {
            appendNodes(el.childNodes(), ctx, resultMeta) ?: PreviousRenderedInfo(nextShouldTrimBlank = true)
        }
        return PreviousRenderedInfo(nextShouldTrimBlank = true)
    }

    private fun Builder.appendElement(
        el: Element,
        lookahead: Lookahead,
        previousRenderedInfo: PreviousRenderedInfo?,
        ctx: RenderContext,
        resultMeta: RenderResultMeta,
    ): PreviousRenderedInfo? {
        return when (val normalName = el.normalName()) {
            "h1",
            "h2",
            "h3",
            "h4",
            "h5",
            "h6" -> appendHeading(el, ctx, resultMeta)

            // Styling
            "b", "strong" -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                appendNodes(el.childNodes(), ctx, resultMeta) ?: PreviousRenderedInfo()
            }
            "i", "em" -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                appendNodes(el.childNodes(), ctx, resultMeta) ?: PreviousRenderedInfo()
            }
            "code" -> {
                val annotation = if (ctx.preFormattedText) MatrixBodyAnnotations.BLOCK_CODE else MatrixBodyAnnotations.INLINE_CODE
                val nextPrevRenderInfo = withAnnotation(annotation, "") {
                    appendNodes(el.childNodes(), ctx, resultMeta) ?: PreviousRenderedInfo()
                }
                if (ctx.preFormattedText) {
                    // Block quote, already handled in paragraph style
                    PreviousRenderedInfo(nextShouldTrimBlank = true)
                } else {
                    nextPrevRenderInfo
                }
            }
            "s", "del" -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                appendNodes(el.childNodes(), ctx, resultMeta) ?: PreviousRenderedInfo()
            }
            "font", // font is deprecated in the spec but easy enough to support the same way as spans
            "span" -> {
                val attributes = SpanAttributes(
                    fgColor = el.attr("data-mx-color").tryParseHexColor() ?: el.attr("color").tryParseHexColor(),
                    bgColor = el.attr("data-mx-bg-color").tryParseHexColor(),
                    isSpoiler = el.hasAttr("data-mx-spoiler"),
                    revealId = length,
                )
                if (attributes.isSpoiler) {
                    resultMeta.expandableItems.add(attributes.revealId)
                }
                withAnnotation(MatrixBodyAnnotations.SPAN, Json.encodeToString(attributes)) {
                    appendNodes(el.childNodes(), ctx, resultMeta) ?: PreviousRenderedInfo()
                }
            }

            // Block containers
            "p", "div" -> {
                if (previousRenderedInfo?.nextShouldTrimBlank == false) {
                    ensureNewlineSeparation("p1", null)
                }
                val start = length
                appendNodes(el.childNodes(), ctx, resultMeta)
                val end = length
                if (lookahead.shouldTrimEncompassingWhitespace() || start == end) {
                    PreviousRenderedInfo()
                } else {
                    if (!lookahead.hasImplicitNewline()) {
                        appendNewline("p2")
                        if (normalName == "p" && lookahead.firstNotEmpty()?.normalName() == "p") {
                            appendNewline("p3")
                        }
                    }
                    PreviousRenderedInfo(nextShouldTrimBlank = true)
                }
            }
            "blockquote" -> {
                val blockDepth = ctx.indentedBlockDepth + 1
                val innerCtx = ctx.copy(indentedBlockDepth = blockDepth)
                withAnnotation(MatrixBodyAnnotations.BLOCK_QUOTE, blockDepth.toString()) {
                    appendNodes(el.childNodes(), innerCtx, resultMeta) ?: PreviousRenderedInfo(nextShouldTrimBlank = true)
                }
                PreviousRenderedInfo(nextShouldTrimBlank = true, hasImplicitNewline = true)
            }
            "pre" -> {
                if (previousRenderedInfo?.nextShouldTrimBlank == false) {
                    ensureNewlineSeparation("pre1", null)
                }
                appendNodes(el.childNodes(), ctx.copy(preFormattedText = true), resultMeta) ?: PreviousRenderedInfo(nextShouldTrimBlank = true)
                if (lookahead.shouldTrimEncompassingWhitespace()) {
                    PreviousRenderedInfo()
                } else {
                    ensureNewlineSeparation("pre2", lookahead)
                    PreviousRenderedInfo(nextShouldTrimBlank = true)
                }
            }
            "br" -> {
                if (!lookahead.hasImplicitNewline()) {
                    appendNewline("br")
                } else if (previousRenderedInfo?.hasImplicitNewline == true) {
                    // Somehow two implicit newlines after each other with nothing in between
                    // cancel one of them out, unless we put *anything* in between?
                    // Note that if we added an actual newline here, we would get two out of that.
                    // Test case: (list, br, list) should have a visual separation between both
                    // lists (one empty newline, not two)
                    append(" ")
                }
                PreviousRenderedInfo(nextShouldTrimBlank = true)
            }

            // Horizontal divider line
            "hr" -> {
                ensureNewlineSeparation("hr1", null)
                withAnnotation(MatrixBodyAnnotations.HORIZONTAL_RULE, "") {
                    // Ensure we get a bounding box via non-breakable space
                    append("\u00A0")
                }
                if (!lookahead.hasImplicitNewline()) {
                    appendNewline("hr2")
                }
                PreviousRenderedInfo(nextShouldTrimBlank = true)
            }

            // Links
            "a" -> {
                val href = el.attr("href")
                // Handle matrix.to links specifically for user mentions and room / message links
                val matrixLink = MatrixPatterns.parseMatrixToUrl(href)
                if (matrixLink == null) {
                    withAnnotation(MatrixBodyAnnotations.WEB_LINK, href) {
                        appendNodes(el.childNodes(), ctx.copy(linkUrl = href), resultMeta) ?: PreviousRenderedInfo()
                    }
                } else {
                    val (annotation, format) = when (matrixLink) {
                        is MatrixToLink.UserMention -> {
                            Pair(MatrixBodyAnnotations.USER_MENTION) { content: AnnotatedString ->
                                ctx.style.formatUserMention(matrixLink.userId, content)
                            }
                        }
                        is MatrixToLink.RoomLink -> {
                            Pair(MatrixBodyAnnotations.ROOM_LINK) { content: AnnotatedString ->
                                ctx.style.formatRoomLink(matrixLink.roomId, matrixLink.via, content)
                            }
                        }
                        is MatrixToLink.MessageLink -> {
                            Pair(MatrixBodyAnnotations.MESSAGE_LINK) { content: AnnotatedString ->
                                ctx.style.formatMessageLink(matrixLink.roomId, matrixLink.messageId, matrixLink.via, content)
                            }
                        }
                    }
                    withAnnotation(annotation, Json.encodeToString(matrixLink)) {
                        var previousRenderedInfo = PreviousRenderedInfo()
                        val content = buildAnnotatedString {
                            previousRenderedInfo = appendNodes(el.childNodes(), ctx.copy(linkUrl = href), resultMeta) ?: previousRenderedInfo
                        }
                        append(format(content))
                        previousRenderedInfo
                    }
                }
            }

            // Lists
            "ul" -> {
                // There's withBulletList too for unordered lists, but it is working unreliably with nested indention in combination with blockquotes,
                // and it cannot render numbers, so for simplicity only do one implementation of lists that does not use withBulletList.
                val bullet = ctx.style.listBulletForDepth(ctx.indentedBlockDepth)
                withAnnotation(MatrixBodyAnnotations.UNORDERED_LIST, ctx.indentedBlockDepth.toString()) {
                    appendNodes(
                        el.childNodes(), ctx.copy(
                            unorderedListScope = UnorderedListScope(
                                bullet = bullet,
                            ),
                            orderedListScope = null,
                        ), resultMeta
                    ) ?: PreviousRenderedInfo(nextShouldTrimBlank = true)
                }
                PreviousRenderedInfo(nextShouldTrimBlank = true, hasImplicitNewline = true)
            }
            "ol" -> {
                withAnnotation(MatrixBodyAnnotations.ORDERED_LIST, ctx.indentedBlockDepth.toString()) {
                    appendNodes(
                        el.childNodes(), ctx.copy(
                            orderedListScope = OrderedListScope(
                                nextNumber = el.getIntAttrOrNull("start") ?: 1,
                            ),
                            unorderedListScope = null
                        ), resultMeta
                    ) ?: PreviousRenderedInfo(nextShouldTrimBlank = true)
                }
                PreviousRenderedInfo(nextShouldTrimBlank = true, hasImplicitNewline = true)
            }
            "li" -> {
                val innerCtx = ctx.copy(indentedBlockDepth = ctx.indentedBlockDepth + 1)
                val (bullet, annotation) = if (ctx.orderedListScope != null) {
                    val number = el.getIntAttrOrNull("value")
                        ?: ctx.orderedListScope.nextNumber
                    ctx.orderedListScope.nextNumber = number + 1
                    Pair("${number}. ", MatrixBodyAnnotations.ORDERED_LIST_ITEM)
                } else if (ctx.unorderedListScope != null) {
                    // Determine if this <li> has any non-list content (text or non-list elements)
                    val hasNonListContent = el.childNodes().any { node ->
                        when (node) {
                            is TextNode -> node.text().isNotBlank()
                            is Element -> node.normalName() !in setOf("ul", "ol") && node.text().isNotBlank()
                            else -> false
                        }
                    }
                    if (hasNonListContent) {
                        Pair(
                            ctx.unorderedListScope.bullet,
                            MatrixBodyAnnotations.UNORDERED_LIST_ITEM,
                        )
                    } else {
                        Pair("", null)
                    }
                } else {
                    Pair("", null)
                }
                if (annotation == null) {
                    // li outside of a list, sounds like broken HTML, just render contents
                    appendNodes(el.childNodes(), ctx, resultMeta)
                } else {
                    withAnnotation(annotation, ctx.indentedBlockDepth.toString()) {
                        append(bullet)
                        appendNodes(el.childNodes(), innerCtx, resultMeta)
                            ?: PreviousRenderedInfo(nextShouldTrimBlank = true)
                    }
                }
            }

            // Inline images
            "img" -> {
                val uri = el.attr("src").takeIf(MatrixPatterns::isValidMatrixUri)
                if (uri == null) {
                    // Not a valid inline image
                    appendNodes(el.childNodes(), ctx, resultMeta)
                } else {
                    val title = el.attr("title").takeIf(String::isNotBlank)
                    val alt = el.attr("alt").takeIf(String::isNotBlank)
                    val id = "${MatrixBodyAnnotations.INLINE_IMAGE_PREFIX}$uri"
                    val inlineImageInfo = InlineImageInfo(
                        uri = uri,
                        isEmote = el.hasAttr("data-mx-emoticon"),
                        width = el.getIntAttrOrNull("width"),
                        height = el.getIntAttrOrNull("height"),
                        title = title,
                        alt = alt,
                    )
                    appendInlineContent(id, ctx.style.formatInlineImageFallback(inlineImageInfo))
                    resultMeta.inlineImages[id] = inlineImageInfo
                }
                PreviousRenderedInfo()
            }

            // Expandable details tags
            "details" -> {
                val childNodes = el.childNodes()
                val preSummary = childNodes.takeWhile { it.normalName() != "summary" }
                if (preSummary.size >= childNodes.size) {
                    // No summary found, do not treat as details tag
                    appendNodes(childNodes, ctx, resultMeta)
                } else {
                    val summary = childNodes[preSummary.size]
                    val postSummary = if (preSummary.size + 1 < childNodes.size) {
                        childNodes.subList(preSummary.size + 1, childNodes.size)
                    } else {
                        emptyList()
                    }
                    if (preSummary.isEmpty() && postSummary.isEmpty()) {
                        // Nothing to expand found, treat as normal content
                        appendNodes(childNodes, ctx, resultMeta, previousRenderedInfo)
                    } else {
                        val revealId = length
                        resultMeta.expandableItems.add(revealId)
                        var innerPrevRenderInfo: PreviousRenderedInfo? = previousRenderedInfo
                        if (preSummary.isNotEmpty()) {
                            withAnnotation(MatrixBodyAnnotations.DETAILS_CONTENT, revealId.toString()) {
                                innerPrevRenderInfo = appendNodes(preSummary, ctx, resultMeta, innerPrevRenderInfo)
                                innerPrevRenderInfo ?: PreviousRenderedInfo()
                            }
                        }
                        withAnnotation(MatrixBodyAnnotations.DETAILS_SUMMARY, revealId.toString()) {
                            append(ctx.style.detailsSummaryIndicatorPlaceholder)
                            innerPrevRenderInfo = appendNodes(listOf(summary), ctx, resultMeta, innerPrevRenderInfo)
                            innerPrevRenderInfo ?: PreviousRenderedInfo()
                        }
                        // Details summary is commonly handled via paragraph style, so trim blanks
                        innerPrevRenderInfo = PreviousRenderedInfo(nextShouldTrimBlank = true)
                        if (postSummary.isNotEmpty()) {
                            withAnnotation(MatrixBodyAnnotations.DETAILS_CONTENT, revealId.toString()) {
                                innerPrevRenderInfo = appendNodes(postSummary, ctx, resultMeta, innerPrevRenderInfo)
                                innerPrevRenderInfo ?: PreviousRenderedInfo()
                            }
                        }
                        // Details content is commonly handled via paragraph style, so trim blanks
                        PreviousRenderedInfo(nextShouldTrimBlank = true)
                    }
                }
            }

            else -> {
                // Unknown tags are ignored but children are parsed to keep text
                appendNodes(el.childNodes(), ctx, resultMeta)
            }
        }
    }

    private fun Lookahead.shouldTrimEncompassingWhitespace(): Boolean {
        return when (val it = firstOrNull()) {
            is TextNode -> it.isBlank && subList(1, size).shouldTrimEncompassingWhitespace()
            is Element -> when (it.normalName()) {
                "b", "strong",
                "i", "em",
                "code",
                "s", "del",
                "font", "span",
                "br",
                "a",
                "p",
                "img" -> false
                else -> true
            }
            null -> true
            else -> false
        }
    }

    /**
     * If an item has an **implicit** newline, that usually means [MatrixBodyStyledFormatter]
     * will render it with a paragraph style (that implies a newline).
     * This does *not* include *explicit* newlines e.g. via <br> or <p> tags.
     */
    private fun Lookahead.hasImplicitNewline(): Boolean {
        return when (val it = firstOrNull()) {
            is TextNode -> it.isBlank && subList(1, size).hasImplicitNewline()
            is Element -> when (it.normalName()) {
                "ul",
                "ol",
                 "blockquote" -> true
                else -> false
            }
            else -> false
        }
    }

    // First item that is not a blank text, in order to look ahead to the next rendered item.
    private fun List<Node>.firstNotEmpty(): Node? {
        forEach {
            when (it) {
                is TextNode -> {
                    if (!it.isBlank) {
                        return it
                    }
                }
                is Element -> return it
            }
        }
        return null
    }

    // Append a newline if the builder does not already end with one and is not empty
    private fun Builder.ensureNewlineSeparation(debugStr: String, lookahead: Lookahead?) {
        val current = toAnnotatedString()
        if (current.paragraphStyles.any { it.end == current.length }) {
            // Just finished a paragraph, no need to add a newline
            return
        }
        if (lookahead?.hasImplicitNewline() == true) {
            return
        }
        if (current.isNotEmpty() && !current.text.endsWith('\n')) appendNewline(debugStr)
    }

    private fun Builder.appendNewline(debugStr: String) {
        if (newlineDbg) {
            append("[")
            append(debugStr)
            append("]")
        }
        append("\n")
    }
}

private fun Element.getIntAttrOrNull(attributeKey: String) = attr(attributeKey).takeIf(String::isNotEmpty)?.toIntOrNull()

private fun String.tryParseHexColor() = removePrefix("#").takeIf { it.length == 6 }?.toLongOrNull(16)?.or(0xff000000)?.toInt()

inline fun <R : Any> Builder.withStyleOrDirect(style: SpanStyle?, block: Builder.() -> R): R {
    return if (style == null) {
        block()
    } else {
        withStyle(style, block)
    }
}
