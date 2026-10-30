package com.beeper.android.messageformat.markdown.html

import com.beeper.android.messageformat.markdown.MatrixMarkdownElementTypes
import com.beeper.android.messageformat.markdown.MatrixMarkdownTokenTypes
import com.beeper.android.messageformat.markdown.MarkdownFeature
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.LeafASTNode
import org.intellij.markdown.ast.accept
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.ast.impl.ListCompositeNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.html.GeneratingProvider
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.html.InlineLinkGeneratingProvider
import org.intellij.markdown.html.LinkGeneratingProvider
import org.intellij.markdown.html.SimpleTagProvider
import org.intellij.markdown.html.entities.EntityConverter
import org.intellij.markdown.html.makeXssSafeDestination
import org.intellij.markdown.parser.LinkMap

/** [GeneratingProvider]s for the Matrix HTML subset, with every newline inside a paragraph rendered as `<br>`. */
internal object MatrixHtmlProviders {

    fun create(
        features: Set<MarkdownFeature>,
        unmaskedText: String? = null,
    ): Map<IElementType, GeneratingProvider> = buildMap {
        val blockQuotes = MarkdownFeature.BLOCKQUOTE in features
        val inline = InlineRenderer(blockQuoteMarkersAreSyntax = blockQuotes)
        put(MarkdownElementTypes.PARAGRAPH, InlineTagProvider(inline, "p"))
        put(MarkdownTokenTypes.HARD_LINE_BREAK, RawHtmlProvider(LINE_BREAK))
        put(MarkdownTokenTypes.HTML_TAG, EscapedTextProvider)
        if (!blockQuotes) put(MarkdownTokenTypes.BLOCK_QUOTE, EscapedTextProvider)
        val italic = MarkdownFeature.ITALIC in features
        val bold = MarkdownFeature.BOLD in features
        put(MarkdownElementTypes.EMPH, inline.tagOrLiteral("em", italic, markerTokens = 1))
        put(MarkdownElementTypes.STRONG, inline.tagOrLiteral("strong", bold, markerTokens = 2))
        put(GFMElementTypes.STRIKETHROUGH, InlineTagProvider(inline, "del", markerTokens = 2))
        put(MatrixMarkdownElementTypes.SPOILER, SpoilerProvider(inline))
        put(MarkdownElementTypes.CODE_SPAN, CodeSpanProvider(unmaskedText))
        put(MarkdownElementTypes.CODE_FENCE, CodeFenceProvider(unmaskedText))
        put(MarkdownElementTypes.BLOCK_QUOTE, SimpleTagProvider("blockquote"))
        put(MarkdownElementTypes.UNORDERED_LIST, SimpleTagProvider("ul"))
        put(MarkdownElementTypes.ORDERED_LIST, OrderedListProvider)
        put(MarkdownElementTypes.LIST_ITEM, ListItemProvider(inline))
        put(MarkdownTokenTypes.ATX_CONTENT, InlineTagProvider(inline, tagName = null))
        HEADINGS.forEachIndexed { index, type -> put(type, SimpleTagProvider("h${index + 1}")) }
        put(MarkdownElementTypes.INLINE_LINK, InlineLinkProvider(inline))
        put(MarkdownElementTypes.AUTOLINK, AutolinkProvider)
        put(MarkdownElementTypes.IMAGE, ImageProvider)
        put(MarkdownTokenTypes.HORIZONTAL_RULE, RawHtmlProvider("<hr>"))
        put(GFMElementTypes.TABLE, TableProvider)
        put(GFMTokenTypes.CELL, InlineTagProvider(inline, tagName = null))
    }

    private val HEADINGS = listOf(
        MarkdownElementTypes.ATX_1,
        MarkdownElementTypes.ATX_2,
        MarkdownElementTypes.ATX_3,
        MarkdownElementTypes.ATX_4,
        MarkdownElementTypes.ATX_5,
        MarkdownElementTypes.ATX_6,
    )

    private fun InlineRenderer.tagOrLiteral(tagName: String, enabled: Boolean, markerTokens: Int) =
        if (enabled) InlineTagProvider(this, tagName, markerTokens) else InlineTagProvider(this, tagName = null)
}

private const val LINE_BREAK = "<br>"

internal fun escapeHtml(text: CharSequence): String =
    EntityConverter.replaceEntities(text, processEntities = false, processEscapes = false)

/** Renders inline children with newlines as `<br>`, dropping continuation indentation and surrounding whitespace. */
private class InlineRenderer(private val blockQuoteMarkersAreSyntax: Boolean) {

    fun render(visitor: HtmlGenerator.HtmlGeneratingVisitor, children: List<ASTNode>) {
        var from = 0
        var to = children.size
        while (from < to && children[from].type == MarkdownTokenTypes.WHITE_SPACE) from++
        while (to > from && children[to - 1].type == MarkdownTokenTypes.WHITE_SPACE) to--

        var afterLineBreak = false
        for (child in children.subList(from, to)) {
            val isLineBreak = child.type == MarkdownTokenTypes.EOL || child.type == MarkdownTokenTypes.HARD_LINE_BREAK
            val isQuoteMarker = blockQuoteMarkersAreSyntax && child.type == MarkdownTokenTypes.BLOCK_QUOTE
            when {
                isQuoteMarker -> Unit
                isLineBreak && afterLineBreak -> Unit
                isLineBreak -> visitor.consumeHtml(LINE_BREAK)
                afterLineBreak && child.type == MarkdownTokenTypes.WHITE_SPACE -> Unit
                child is LeafASTNode -> visitor.visitLeaf(child)
                else -> child.accept(visitor)
            }
            val continuesLineStart = child.type == MarkdownTokenTypes.WHITE_SPACE || isQuoteMarker
            afterLineBreak = isLineBreak || (afterLineBreak && continuesLineStart)
        }
    }
}

/** Wraps the inline content of a node in [tagName], dropping [markerTokens] delimiter tokens on each side. */
private class InlineTagProvider(
    private val inline: InlineRenderer,
    private val tagName: String?,
    private val markerTokens: Int = 0,
) : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        if (node.children.size <= 2 * markerTokens) return inline.render(visitor, node.children)
        tagName?.let { visitor.consumeTagOpen(node, it) }
        inline.render(visitor, node.children.subList(markerTokens, node.children.size - markerTokens))
        tagName?.let { visitor.consumeTagClose(it) }
    }
}

private class RawHtmlProvider(private val html: String) : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        visitor.consumeHtml(html)
    }
}

private object EscapedTextProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        visitor.consumeHtml(escapeHtml(node.getTextInNode(text)))
    }
}

private class SpoilerProvider(private val inline: InlineRenderer) : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val inner = node.children.subList(2, node.children.size - 2)
        val separator = inner.indexOfFirst { it.type == MatrixMarkdownTokenTypes.PIPE }
        val content = if (separator < 0) inner else inner.subList(separator + 1, inner.size)
        val reason =
            if (separator < 0) "" else text.substring(inner.first().startOffset, inner[separator].startOffset).trim()
        val attribute = if (reason.isEmpty()) "data-mx-spoiler" else "data-mx-spoiler=\"${escapeHtml(reason)}\""
        visitor.consumeTagOpen(node, "span", attribute)
        inline.render(visitor, content)
        visitor.consumeTagClose("span")
    }
}

private class CodeSpanProvider(private val unmaskedText: String?) : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val source = unmaskedText ?: text
        val content = node.children.subList(1, node.children.size - 1)
            .joinToString(separator = "") { HtmlGenerator.leafText(source, it, replaceEscapesAndEntities = false) }
            .trim()
        visitor.consumeTagOpen(node, "code")
        visitor.consumeHtml(content)
        visitor.consumeTagClose("code")
    }
}

private class CodeFenceProvider(private val unmaskedText: String?) : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val source = unmaskedText ?: text
        val indent = node.getTextInNode(text).commonPrefixWith(" ".repeat(10)).length
        val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)
            ?.let { HtmlGenerator.leafText(text, it).toString().trim().substringBefore(' ') }
            ?.takeIf { it.isNotEmpty() }
        visitor.consumeHtml("<pre>")
        visitor.consumeTagOpen(node, "code", language?.let { "class=\"language-$it\"" })
        // The line break ending the opening fence line is not content, every later one is
        var openingLineDone = false
        var lastWasContent = false
        for (child in node.children) {
            when (child.type) {
                MarkdownTokenTypes.CODE_FENCE_CONTENT -> {
                    val line = HtmlGenerator.leafText(source, child, replaceEscapesAndEntities = false)
                    visitor.consumeHtml(HtmlGenerator.trimIndents(line, indent))
                    openingLineDone = true
                    lastWasContent = true
                }
                MarkdownTokenTypes.EOL -> {
                    if (openingLineDone) visitor.consumeHtml("\n")
                    openingLineDone = true
                    lastWasContent = false
                }
            }
        }
        if (lastWasContent) visitor.consumeHtml("\n")
        visitor.consumeTagClose("code")
        visitor.consumeHtml("</pre>")
    }
}

private fun ASTNode.listItemNumber(text: String): Int? =
    findChildOfType(MarkdownTokenTypes.LIST_NUMBER)?.getTextInNode(text)?.toString()?.trim()?.dropLast(1)?.toIntOrNull()

private object OrderedListProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val start = node.findChildOfType(MarkdownElementTypes.LIST_ITEM)?.listItemNumber(text)
        visitor.consumeTagOpen(node, "ol", start?.takeIf { it != 1 }?.let { "start=\"$it\"" })
        node.children.forEach { it.accept(visitor) }
        visitor.consumeTagClose("ol")
    }
}

private class ListItemProvider(private val inline: InlineRenderer) : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val list = node.parent
        val tight = (list as? ListCompositeNode)?.loose == false
        visitor.consumeTagOpen(node, "li", jumpValueAttribute(list, node, text))
        for (child in node.children) {
            if (tight && child.type == MarkdownElementTypes.PARAGRAPH) {
                inline.render(visitor, child.children)
            } else {
                child.accept(visitor)
            }
        }
        visitor.consumeTagClose("li")
    }

    // Matrix clients display jumps in list numbering, which CommonMark would discard
    private fun jumpValueAttribute(list: ASTNode?, item: ASTNode, text: String): String? {
        if (list?.type != MarkdownElementTypes.ORDERED_LIST) return null
        val items = list.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
        val index = items.indexOf(item)
        if (index <= 0) return null
        val number = item.listItemNumber(text) ?: return null
        val previous = items[index - 1].listItemNumber(text) ?: return null
        return if (number == previous + 1) null else "value=\"$number\""
    }
}

private class InlineLinkProvider(private val inline: InlineRenderer) : InlineLinkGeneratingProvider(baseURI = null) {
    override fun renderLink(
        visitor: HtmlGenerator.HtmlGeneratingVisitor,
        text: String,
        node: ASTNode,
        info: LinkGeneratingProvider.RenderInfo,
    ) {
        val destination = makeXssSafeDestination(makeAbsoluteUrl(info.destination))
        visitor.consumeTagOpen(node, "a", "href=\"$destination\"", info.title?.let { "title=\"$it\"" })
        inline.render(visitor, info.label.children.subList(1, info.label.children.size - 1))
        visitor.consumeTagClose("a")
    }
}

private object AutolinkProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val linkText = node.getTextInNode(text)
        val label = EntityConverter.replaceEntities(
            linkText.subSequence(1, linkText.length - 1),
            processEntities = true,
            processEscapes = false,
        )
        val isEmail = node.findChildOfType(MarkdownTokenTypes.EMAIL_AUTOLINK) != null
        val destination =
            if (isEmail) "mailto:$label" else makeXssSafeDestination(LinkMap.normalizeDestination(linkText, false))
        visitor.consumeTagOpen(node, "a", "href=\"$destination\"")
        visitor.consumeHtml(label)
        visitor.consumeTagClose("a")
    }
}

private object TableProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val header = node.findChildOfType(GFMElementTypes.HEADER)
        val columns = header?.children?.count { it.type == GFMTokenTypes.CELL } ?: 0
        visitor.consumeTagOpen(node, "table")
        var bodyOpen = false
        for (child in node.children) {
            when (child.type) {
                GFMElementTypes.HEADER -> {
                    visitor.consumeHtml("<thead>")
                    renderRow(visitor, child, "th", columns)
                    visitor.consumeHtml("</thead>")
                }
                GFMElementTypes.ROW -> {
                    if (!bodyOpen) {
                        visitor.consumeHtml("<tbody>")
                        bodyOpen = true
                    }
                    renderRow(visitor, child, "td", columns)
                }
            }
        }
        if (bodyOpen) visitor.consumeHtml("</tbody>")
        visitor.consumeTagClose("table")
    }

    private fun renderRow(visitor: HtmlGenerator.HtmlGeneratingVisitor, row: ASTNode, cellTag: String, columns: Int) {
        visitor.consumeTagOpen(row, "tr")
        val cells = row.children.filter { it.type == GFMTokenTypes.CELL }
        for (cell in cells.take(columns)) {
            visitor.consumeTagOpen(cell, cellTag)
            visitor.visitNode(cell)
            visitor.consumeTagClose(cellTag)
        }
        repeat(columns - cells.size) { visitor.consumeHtml("<$cellTag></$cellTag>") }
        visitor.consumeTagClose("tr")
    }
}

private object ImageProvider : GeneratingProvider {
    override fun processNode(visitor: HtmlGenerator.HtmlGeneratingVisitor, text: String, node: ASTNode) {
        val link = node.findChildOfType(MarkdownElementTypes.INLINE_LINK)
            ?: return visitor.consumeHtml(escapeHtml(node.getTextInNode(text)))
        val label = link.findChildOfType(MarkdownElementTypes.LINK_TEXT)?.getTextInNode(text)
            ?.let { it.subSequence(1, it.length - 1) }?.toString() ?: ""
        val source = link.findChildOfType(MarkdownElementTypes.LINK_DESTINATION)?.getTextInNode(text)
            ?.let { makeXssSafeDestination(LinkMap.normalizeDestination(it, true)) } ?: ""
        val title = link.findChildOfType(MarkdownElementTypes.LINK_TITLE)?.getTextInNode(text)
            ?.let { LinkMap.normalizeTitle(it).toString() }
        if (title != null && title.startsWith(EMOTE_TITLE_PREFIX)) {
            // The mautrix convention for custom emotes in markdown: `![:shortcode:](mxc "Emoji: body")`
            val body = title.removePrefix(EMOTE_TITLE_PREFIX)
            val shortcode = label.removeSurrounding(":")
            val alt = body.takeIf { it.isNotBlank() && it != label } ?: shortcode
            return visitor.consumeHtml(customEmoteHtml(source, shortcode, alt))
        }
        val titleAttribute = title?.let { "title=\"${escapeHtml(it)}\"" }
        visitor.consumeTagOpen(node, "img", "src=\"$source\"", "alt=\"${escapeHtml(label)}\"", titleAttribute)
    }

    private const val EMOTE_TITLE_PREFIX = "Emoji: "
}
