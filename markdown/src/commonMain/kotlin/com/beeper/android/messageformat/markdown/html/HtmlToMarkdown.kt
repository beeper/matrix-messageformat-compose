package com.beeper.android.messageformat.markdown.html

import com.beeper.android.messageformat.InlineImageInfo
import com.beeper.android.messageformat.MatrixPatterns
import com.beeper.android.messageformat.MatrixToLink
import com.beeper.android.messageformat.markdown.MarkdownFeature
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import com.beeper.android.messageformat.markdown.MatrixMarkdownContent
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * Converts Matrix HTML into the markdown of [features], with mentions and inline images as entities.
 * Formatting that [features] can't express becomes its plain text. Plain text is escaped where it would read as
 * markdown, unless [escapeText] is off because backslashes stay as typed.
 */
internal class HtmlToMarkdown(private val features: Set<MarkdownFeature>, private val escapeText: Boolean) {

    fun convert(html: String): MatrixMarkdownContent {
        val body = Jsoup.parseBodyFragment(html).body()
        val markdown = blocks(body.childNodes(), BLOCK_SEPARATOR)
        return MatrixMarkdownContent(markdown.text.toString(), markdown.entities)
    }

    private fun has(feature: MarkdownFeature) = feature in features

    /** [nodes] as markdown blocks, joined by [separator]. Runs of inline nodes form one paragraph. */
    private fun blocks(nodes: List<Node>, separator: String): Markdown {
        val result = Markdown()
        val paragraph = mutableListOf<Node>()
        fun addBlock(block: Markdown) {
            if (block.text.isBlank()) return
            if (result.text.isNotEmpty()) result.appendSyntax(separator)
            result.append(block)
        }
        fun flushParagraph() {
            if (paragraph.isEmpty()) return
            addBlock(inline(paragraph).trimmed())
            paragraph.clear()
        }
        for (node in nodes) {
            if (node is Element && node.normalName() in BLOCK_TAGS) {
                flushParagraph()
                addBlock(block(node))
            } else {
                paragraph += node
            }
        }
        flushParagraph()
        return result
    }

    private fun block(element: Element): Markdown = when (element.normalName()) {
        "p", "div", "details", "summary" -> blocks(element.childNodes(), BLOCK_SEPARATOR)
        "h1", "h2", "h3", "h4", "h5", "h6" -> heading(element)
        "blockquote" -> {
            val content = blocks(element.childNodes(), BLOCK_SEPARATOR)
            if (has(MarkdownFeature.BLOCKQUOTE)) content.prefixLines("> ", "> ", emptyLinePrefix = ">") else content
        }
        "pre" -> codeBlock(element)
        "ul" -> list(element, ordered = false)
        "ol" -> list(element, ordered = true)
        "li" -> blocks(element.childNodes(), LINE_SEPARATOR)
        "table" -> table(element)
        "hr" -> Markdown().apply { if (has(MarkdownFeature.HORIZONTAL_RULE)) appendSyntax("---") }
        else -> Markdown()
    }

    private fun heading(element: Element): Markdown {
        val content = inline(element.childNodes(), singleLine = true).trimmed()
        if (!has(MarkdownFeature.HEADING) || content.text.isBlank()) return content
        val level = element.normalName().removePrefix("h").toInt()
        return Markdown().apply {
            appendSyntax("#".repeat(level) + " ")
            append(content)
        }
    }

    private fun codeBlock(element: Element): Markdown {
        val code = element.children().singleOrNull()?.takeIf { it.normalName() == "code" && element.textNodes().all(TextNode::isBlank) }
        val content = (code ?: element).wholeText().removeSuffix("\n")
        if (!has(MarkdownFeature.CODE_BLOCK)) {
            return Markdown().apply { appendText(content, escaper()) }
        }
        val language = code?.classNames()?.firstOrNull { it.startsWith(LANGUAGE_CLASS_PREFIX) }?.removePrefix(LANGUAGE_CLASS_PREFIX)
        val fence = "`".repeat(maxOf(3, content.longestRunOf('`') + 1))
        return Markdown().apply { appendSyntax("$fence${language.orEmpty()}\n$content\n$fence") }
    }

    private fun list(element: Element, ordered: Boolean): Markdown {
        val items = element.children().filter { it.normalName() == "li" }
        val loose = items.any { item -> item.children().any { it.normalName() == "p" } }
        var number = element.attr("start").toIntOrNull() ?: 1
        val result = Markdown()
        val enabled = has(if (ordered) MarkdownFeature.ORDERED_LIST else MarkdownFeature.UNORDERED_LIST)
        for (item in items) {
            item.attr("value").toIntOrNull()?.let { number = it }
            val marker = if (ordered) "$number. " else "- "
            number++
            val content = blocks(item.childNodes(), if (loose) BLOCK_SEPARATOR else LINE_SEPARATOR)
            if (result.text.isNotEmpty()) result.appendSyntax(if (loose) BLOCK_SEPARATOR else LINE_SEPARATOR)
            if (enabled) {
                result.append(content.prefixLines(marker, " ".repeat(marker.length)))
            } else {
                result.append(content)
            }
        }
        return result
    }

    private fun table(element: Element): Markdown {
        val rows = element.select("tr").filter { it.closest("table") == element }
        val result = Markdown()
        if (rows.isEmpty()) return result
        if (!has(MarkdownFeature.TABLE)) {
            rows.forEach { row ->
                val cells = row.children().filter { it.normalName() in TABLE_CELLS }
                val line = Markdown()
                cells.forEachIndexed { index, cell ->
                    if (index > 0) line.appendText(" ", escaper())
                    line.append(inline(cell.childNodes(), singleLine = true).trimmed())
                }
                if (result.text.isNotEmpty()) result.appendSyntax(LINE_SEPARATOR)
                result.append(line)
            }
            return result
        }
        val columns = rows.maxOf { row -> row.children().count { it.normalName() in TABLE_CELLS } }
        rows.forEachIndexed { index, row ->
            val cells = row.children().filter { it.normalName() in TABLE_CELLS }
            if (index > 0) result.appendSyntax(LINE_SEPARATOR)
            result.appendSyntax("|")
            for (column in 0 until columns) {
                result.appendSyntax(" ")
                cells.getOrNull(column)?.let { result.append(inline(it.childNodes(), singleLine = true, tableCell = true).trimmed()) }
                result.appendSyntax(" |")
            }
            if (index == 0) result.appendSyntax("\n|" + " --- |".repeat(columns))
        }
        return result
    }

    /** [nodes] as inline markdown. A [singleLine] has spaces in place of line breaks. */
    private fun inline(nodes: List<Node>, singleLine: Boolean = false, tableCell: Boolean = false): Markdown {
        val context = InlineContext(singleLine, tableCell, linkLabel = false)
        return Markdown().apply { nodes.forEach { appendInline(it, context) } }
    }

    private data class InlineContext(val singleLine: Boolean, val tableCell: Boolean, val linkLabel: Boolean)

    private fun Markdown.appendInline(node: Node, context: InlineContext) {
        when (node) {
            is TextNode -> appendText(node.wholeText.collapseLineBreaks(), escaper(context))
            is Element -> appendElement(node, context)
        }
    }

    private fun Markdown.appendChildren(element: Element, context: InlineContext) {
        element.childNodes().forEach { appendInline(it, context) }
    }

    private fun Markdown.appendElement(element: Element, context: InlineContext) {
        when (element.normalName()) {
            "b", "strong" -> appendWrapped(element, context, "**".takeIf { has(MarkdownFeature.BOLD) })
            "i", "em" -> appendWrapped(element, context, "*".takeIf { has(MarkdownFeature.ITALIC) })
            "s", "del", "strike" -> appendWrapped(element, context, "~~".takeIf { has(MarkdownFeature.STRIKETHROUGH) })
            "code" -> appendCodeSpan(element, context)
            "span" -> if (element.hasAttr("data-mx-spoiler") && has(MarkdownFeature.SPOILER)) {
                val reason = element.attr("data-mx-spoiler").replace("|", "").collapseLineBreaks().trim()
                val opening = if (reason.isEmpty()) "||" else "||$reason|"
                appendWrapped(element, context, opening, closing = "||")
            } else {
                appendChildren(element, context)
            }
            "a" -> appendLink(element, context)
            "img" -> appendImage(element, context)
            "br" -> if (context.singleLine) appendText(" ", escaper(context)) else appendSyntax(LINE_SEPARATOR)
            "mx-reply" -> Unit
            // Blocks nested in inline content, which only foreign HTML has
            in BLOCK_TAGS -> {
                if (!context.singleLine && text.isNotEmpty() && !isAtLineStart()) appendSyntax(LINE_SEPARATOR)
                appendChildren(element, context)
            }
            else -> appendChildren(element, context)
        }
    }

    /** Wraps [element]'s content in [opening] and [closing], keeping surrounding whitespace outside the markers. */
    private fun Markdown.appendWrapped(element: Element, context: InlineContext, opening: String?, closing: String? = opening) {
        if (opening == null || closing == null) return appendChildren(element, context)
        val content = Markdown(preceding = opening.last()).apply { appendChildren(element, context) }
        if (content.text.isBlank()) return append(content)
        val leading = content.text.takeWhile(Char::isWhitespace).length
        val trailing = content.text.takeLastWhile(Char::isWhitespace).length
        append(content.slice(0, leading))
        appendSyntax(opening)
        append(content.slice(leading, content.text.length - trailing))
        appendSyntax(closing)
        append(content.slice(content.text.length - trailing, content.text.length))
    }

    private fun Markdown.appendCodeSpan(element: Element, context: InlineContext) {
        val code = element.wholeText().let { if (context.singleLine) it.replace('\n', ' ') else it }
        if (!has(MarkdownFeature.INLINE_CODE) || code.isBlank()) return appendText(code, escaper(context))
        val fence = "`".repeat(code.longestRunOf('`') + 1)
        val padding = if (code.startsWith('`') || code.endsWith('`')) " " else ""
        appendSyntax("$fence$padding$code$padding$fence")
    }

    private fun Markdown.appendLink(element: Element, context: InlineContext) {
        val href = element.attr("href")
        val label = Markdown(preceding = '[').apply { appendChildren(element, context.copy(linkLabel = true)) }
        val labelText = label.text.toString()
        val matrixLink = MatrixPatterns.parseMatrixLink(href, isAutoLink = false)
        if (matrixLink is MatrixToLink.UserMention && labelText.isNotBlank() && '\n' !in labelText) {
            val plainLabel = element.text().collapseLineBreaks()
            return appendEntity(plainLabel) { start, end -> MatrixInlineEntity.UserMention(start, end, matrixLink.userId) }
        }
        val isBareUrl = href == element.text() || href == "https://${element.text()}" || href == "http://${element.text()}"
        if (context.linkLabel || !has(MarkdownFeature.INLINE_LINK) || href.isBlank() || isBareUrl) {
            return appendChildren(element, context)
        }
        appendSyntax("[")
        append(label)
        appendSyntax("](${linkDestination(href)}${element.attr("title").takeIf(String::isNotEmpty)?.let(::linkTitle).orEmpty()})")
    }

    private fun Markdown.appendImage(element: Element, context: InlineContext) {
        val src = element.attr("src")
        val alt = element.attr("alt").takeIf(String::isNotBlank)?.collapseLineBreaks()
        val title = element.attr("title").takeIf(String::isNotBlank)?.collapseLineBreaks()
        if (!src.startsWith(MXC_SCHEME)) {
            // Image alt texts are sent as typed, so brackets in them can't be escaped
            val markdownAlt = alt.orEmpty().takeIf { '[' !in it && ']' !in it }
            if (!has(MarkdownFeature.IMAGE) || context.linkLabel || src.isBlank() || markdownAlt == null) {
                return alt?.let { appendText(it, escaper(context)) } ?: Unit
            }
            return appendSyntax("![$markdownAlt](${linkDestination(src)}${title?.let(::linkTitle).orEmpty()})")
        }
        val image = InlineImageInfo(
            uri = src,
            isEmote = element.hasAttr("data-mx-emoticon"),
            width = element.attr("width").toIntOrNull(),
            height = element.attr("height").toIntOrNull(),
            title = title,
            alt = alt,
        )
        val visibleText = image.emoteFallbackText ?: alt ?: title ?: src
        appendEntity(visibleText) { start, end -> MatrixInlineEntity.InlineImage(start, end, image) }
    }

    private fun linkDestination(href: String): String = buildString {
        for (char in href) {
            when {
                char == '\\' || char == '(' || char == ')' -> append('\\').append(char)
                char == '<' || char == '>' || char.isWhitespace() -> append("%" + char.code.toString(16).uppercase().padStart(2, '0'))
                else -> append(char)
            }
        }
    }

    private fun linkTitle(title: String) = " \"" + title.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun escaper(context: InlineContext? = null) =
        MarkdownEscaper(
            if (escapeText) features else emptySet(),
            tableCell = context?.tableCell == true,
            linkLabel = context?.linkLabel == true,
        )

    private companion object {
        const val BLOCK_SEPARATOR = "\n\n"
        const val LINE_SEPARATOR = "\n"
        const val LANGUAGE_CLASS_PREFIX = "language-"
        const val MXC_SCHEME = "mxc://"
        val TABLE_CELLS = setOf("th", "td")
        val BLOCK_TAGS = setOf(
            "p", "div", "h1", "h2", "h3", "h4", "h5", "h6", "blockquote", "pre", "ul", "ol", "li", "table", "hr",
            "details", "summary",
        )
    }
}

/**
 * Markdown text with the entities in it, in text order. It follows [preceding] markdown syntax, or starts a line
 * if that is null.
 */
private class Markdown(private val preceding: Char? = null) {
    val text = StringBuilder()
    val entities = mutableListOf<MatrixInlineEntity>()

    private val last: Char? get() = text.lastOrNull() ?: preceding

    fun isAtLineStart() = last == null || last == '\n'

    fun appendSyntax(syntax: String) {
        text.append(syntax)
    }

    fun appendText(raw: String, escaper: MarkdownEscaper) {
        text.append(escaper.escape(raw, previous = last))
    }

    fun appendEntity(visibleText: String, create: (start: Int, end: Int) -> MatrixInlineEntity) {
        if (visibleText.isEmpty()) return
        val start = text.length
        text.append(visibleText)
        entities += create(start, text.length)
    }

    fun append(other: Markdown) {
        val offset = text.length
        text.append(other.text)
        other.entities.mapTo(entities) { it.moved(it.start + offset, it.end + offset) }
    }

    fun slice(start: Int, end: Int) = Markdown().also { slice ->
        slice.text.append(text, start, end)
        entities.filter { it.start >= start && it.end <= end }.mapTo(slice.entities) { it.moved(it.start - start, it.end - start) }
    }

    /** Without leading and trailing whitespace. */
    fun trimmed(): Markdown {
        val start = text.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return Markdown()
        val end = text.indexOfLast { !it.isWhitespace() } + 1
        return slice(start, end)
    }

    /** Puts [first] before the first line, and [rest] before every later one that isn't empty. */
    fun prefixLines(first: String, rest: String, emptyLinePrefix: String = ""): Markdown {
        val result = Markdown()
        val lines = text.split('\n')
        var lineStart = 0
        lines.forEachIndexed { index, line ->
            if (index > 0) result.appendSyntax("\n")
            val prefix = when {
                index == 0 -> first
                line.isEmpty() -> emptyLinePrefix
                else -> rest
            }
            result.appendSyntax(prefix)
            result.append(slice(lineStart, lineStart + line.length))
            lineStart += line.length + 1
        }
        return result
    }
}

private fun String.collapseLineBreaks() = replace(LINE_BREAK_WHITESPACE, " ")

private val LINE_BREAK_WHITESPACE = Regex("""[ \t]*[\r\n]+[ \t\r\n]*""")

private fun String.longestRunOf(char: Char): Int {
    var longest = 0
    var current = 0
    for (c in this) {
        current = if (c == char) current + 1 else 0
        longest = maxOf(longest, current)
    }
    return longest
}
