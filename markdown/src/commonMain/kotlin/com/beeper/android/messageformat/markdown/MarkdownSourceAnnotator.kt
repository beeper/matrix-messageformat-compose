package com.beeper.android.messageformat.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.beeper.android.messageformat.MatrixBodyAnnotations
import com.beeper.android.messageformat.MatrixBodyParseResult
import com.beeper.android.messageformat.MatrixBodyPreFormatStyle
import com.beeper.android.messageformat.addAutoLinkAnnotations
import com.beeper.android.messageformat.SpanAttributes
import com.beeper.android.messageformat.markdown.html.hasMarkers
import kotlinx.serialization.json.Json
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.ast.getTextInNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.LinkMap

/**
 * Annotates markdown source with its formatting, keeping every character in place.
 */
internal class MarkdownSourceAnnotator(
    private val features: Set<MarkdownFeature>,
    private val preFormatStyle: MatrixBodyPreFormatStyle,
) {

    /** Annotates [markdown] by [tree], which was parsed from [parsed], a copy with [entities] masked. */
    fun annotate(
        markdown: String,
        tree: ASTNode,
        parsed: String = markdown,
        entities: List<MatrixInlineEntity> = emptyList(),
    ): MatrixBodyParseResult {
        val expandableItems = mutableSetOf<Int>()
        val text = buildAnnotatedString {
            append(markdown)
            val visitor = Visitor(this, parsed, expandableItems)
            visitor.visit(tree)
            val autoLinkExcluded = visitor.autoLinkExcluded + entities.map { TextRange(it.start, it.end) }
            autoLinkRuns(markdown.length, autoLinkExcluded).forEach { run ->
                addAutoLinkAnnotations(markdown.substring(run.start, run.end), run.start, preFormatStyle)
            }
        }
        return MatrixBodyParseResult(text = text, expandableItems = expandableItems)
    }

    private fun autoLinkRuns(length: Int, autoLinkExcluded: List<TextRange>): List<TextRange> = buildList {
        var runStart = 0
        for (range in autoLinkExcluded.sortedBy { it.start }) {
            if (range.start > runStart) add(TextRange(runStart, range.start))
            runStart = maxOf(runStart, range.end)
        }
        if (runStart < length) add(TextRange(runStart, length))
    }

    private inner class Visitor(
        private val builder: AnnotatedString.Builder,
        private val source: String,
        private val expandableItems: MutableSet<Int>,
    ) {
        val autoLinkExcluded = mutableListOf<TextRange>()

        fun visit(node: ASTNode) {
            if (node.type in AUTO_LINK_EXCLUDED) autoLinkExcluded += TextRange(node.startOffset, node.endOffset)
            when (node.type) {
                MarkdownElementTypes.STRONG -> emphasis(node, MarkdownFeature.BOLD, BOLD, markerTokens = 2)
                MarkdownElementTypes.EMPH -> emphasis(node, MarkdownFeature.ITALIC, ITALIC, markerTokens = 1)
                GFMElementTypes.STRIKETHROUGH ->
                    emphasis(node, MarkdownFeature.STRIKETHROUGH, STRIKETHROUGH, markerTokens = 2, GFMTokenTypes.TILDE)
                MatrixMarkdownElementTypes.SPOILER -> spoiler(node)
                MarkdownElementTypes.CODE_SPAN -> codeSpan(node)
                MarkdownElementTypes.CODE_FENCE -> codeFence(node)
                in ATX_HEADINGS -> heading(node, ATX_HEADINGS.indexOf(node.type) + 1)
                MarkdownElementTypes.INLINE_LINK -> inlineLink(node)
                MarkdownElementTypes.AUTOLINK -> autolink(node)
                MarkdownElementTypes.IMAGE -> image(node)
                MarkdownTokenTypes.BLOCK_QUOTE ->
                    if (MarkdownFeature.BLOCKQUOTE in features) marker(node, MarkdownFeature.BLOCKQUOTE)
                in MARKER_TOKENS -> marker(node, MARKER_TOKENS.getValue(node.type))
                else -> visitChildren(node.children)
            }
        }

        private fun visitChildren(children: List<ASTNode>) = children.forEach { visit(it) }

        private fun emphasis(
            node: ASTNode,
            feature: MarkdownFeature,
            style: SpanStyle,
            markerTokens: Int,
            marker: IElementType? = null,
        ) {
            // Unbalanced delimiter runs can produce a node without content between its markers
            if (!node.hasMarkers(markerTokens, marker)) return visitChildren(node.children)
            val content = node.children.subList(markerTokens, node.children.size - markerTokens)
            if (feature in features) {
                markerRange(node.startOffset, content.first().startOffset, feature)
                markerRange(content.last().endOffset, node.endOffset, feature)
                builder.addStyle(style, content.first().startOffset, content.last().endOffset)
            }
            visitChildren(content)
        }

        private fun spoiler(node: ASTNode) {
            val inner = node.children.subList(2, node.children.size - 2)
            val separator = inner.indexOfFirst { it.type == MatrixMarkdownTokenTypes.PIPE }
            val content = if (separator < 0) inner else inner.subList(separator + 1, inner.size)
            val feature = MarkdownFeature.SPOILER
            markerRange(node.startOffset, inner.firstOrNull()?.startOffset ?: node.endOffset, feature)
            markerRange(inner.lastOrNull()?.endOffset ?: node.startOffset, node.endOffset, feature)
            if (separator >= 0) markerRange(inner.first().startOffset, inner[separator].endOffset, feature)
            if (content.isNotEmpty()) {
                val start = content.first().startOffset
                val attributes = SpanAttributes(fgColor = null, bgColor = null, isSpoiler = true, revealId = start)
                expandableItems.add(start)
                annotate(MatrixBodyAnnotations.SPAN, Json.encodeToString(attributes), start, content.last().endOffset)
            }
            visitChildren(content)
        }

        private fun codeSpan(node: ASTNode) {
            marker(node.children.first(), MarkdownFeature.INLINE_CODE)
            marker(node.children.last(), MarkdownFeature.INLINE_CODE)
            val content = node.children.subList(1, node.children.size - 1)
            if (content.isNotEmpty()) {
                annotate(MatrixBodyAnnotations.INLINE_CODE, "", content.first().startOffset, content.last().endOffset)
            }
        }

        private fun codeFence(node: ASTNode) {
            node.children.filter { it.type in FENCE_MARKER_TOKENS }.forEach { marker(it, MarkdownFeature.CODE_BLOCK) }
            val content = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
            if (content.isNotEmpty()) {
                annotate(MatrixBodyAnnotations.BLOCK_CODE, "", content.first().startOffset, content.last().endOffset)
            }
        }

        // The heading style spans the markers too, so the whole line reads as one heading
        private fun heading(node: ASTNode, level: Int) {
            node.children.filter { it.type == MarkdownTokenTypes.ATX_HEADER }.forEach { marker(it, MarkdownFeature.HEADING) }
            val text = node.getTextInNode(source)
            val end = node.startOffset + text.indexOfLast { !it.isWhitespace() } + 1
            annotate(MatrixBodyAnnotations.HEADING, "h$level", node.startOffset, end)
            node.children.firstOrNull { it.type == MarkdownTokenTypes.ATX_CONTENT }?.let { visitChildren(it.children) }
        }

        private fun inlineLink(node: ASTNode) {
            val label = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                ?: return visitChildren(node.children)
            val destination = node.children.firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                ?.let { LinkMap.normalizeDestination(it.getTextInNode(source), true).toString() }
                ?: return visitChildren(node.children)
            markerRange(node.startOffset, label.startOffset + 1, MarkdownFeature.INLINE_LINK)
            markerRange(label.endOffset - 1, node.endOffset, MarkdownFeature.INLINE_LINK)
            val content = label.children.subList(1, label.children.size - 1)
            if (content.isNotEmpty()) {
                annotate(
                    MatrixBodyAnnotations.WEB_LINK,
                    destination,
                    content.first().startOffset,
                    content.last().endOffset,
                )
            }
            visitChildren(content)
        }

        private fun autolink(node: ASTNode) {
            val url = node.getTextInNode(source)
            if (url.length < 3) return
            markerRange(node.startOffset, node.startOffset + 1, MarkdownFeature.AUTOLINK)
            markerRange(node.endOffset - 1, node.endOffset, MarkdownFeature.AUTOLINK)
            val address = url.subSequence(1, url.length - 1).toString()
            if (node.children.any { it.type == MarkdownTokenTypes.EMAIL_AUTOLINK }) {
                annotate(MatrixBodyAnnotations.MAIL_ADDRESS, address, node.startOffset + 1, node.endOffset - 1)
            } else {
                val destination = LinkMap.normalizeDestination(url, false).toString()
                annotate(MatrixBodyAnnotations.WEB_LINK, destination, node.startOffset + 1, node.endOffset - 1)
            }
        }

        private fun image(node: ASTNode) {
            val label = node.children.firstOrNull { it.type == MarkdownElementTypes.INLINE_LINK }
                ?.children?.firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
                ?: return markerRange(node.startOffset, node.endOffset, MarkdownFeature.IMAGE)
            markerRange(node.startOffset, label.startOffset + 1, MarkdownFeature.IMAGE)
            markerRange(label.endOffset - 1, node.endOffset, MarkdownFeature.IMAGE)
        }

        private fun marker(node: ASTNode, feature: MarkdownFeature) = markerRange(node.startOffset, node.endOffset, feature)

        private fun markerRange(start: Int, end: Int, feature: MarkdownFeature) {
            if (start < end) autoLinkExcluded += TextRange(start, end)
            annotate(MatrixBodyAnnotations.SYNTAX_MARKER, feature.name, start, end)
        }

        private fun annotate(tag: String, value: String, start: Int, end: Int) {
            if (start < end) builder.addStringAnnotation(tag, value, start, end)
        }
    }

    private companion object {
        val BOLD = SpanStyle(fontWeight = FontWeight.Bold)
        val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)
        val STRIKETHROUGH = SpanStyle(textDecoration = TextDecoration.LineThrough)

        val MARKER_TOKENS: Map<IElementType, MarkdownFeature> = mapOf(
            MarkdownTokenTypes.LIST_BULLET to MarkdownFeature.UNORDERED_LIST,
            MarkdownTokenTypes.LIST_NUMBER to MarkdownFeature.ORDERED_LIST,
            MarkdownTokenTypes.HORIZONTAL_RULE to MarkdownFeature.HORIZONTAL_RULE,
            GFMTokenTypes.TABLE_SEPARATOR to MarkdownFeature.TABLE,
        )

        val AUTO_LINK_EXCLUDED: Set<IElementType> = setOf(
            MarkdownElementTypes.INLINE_LINK,
            MarkdownElementTypes.AUTOLINK,
            MarkdownElementTypes.IMAGE,
            MarkdownElementTypes.CODE_SPAN,
            MarkdownElementTypes.CODE_FENCE,
        )

        val FENCE_MARKER_TOKENS: Set<IElementType> = setOf(
            MarkdownTokenTypes.CODE_FENCE_START,
            MarkdownTokenTypes.FENCE_LANG,
            MarkdownTokenTypes.CODE_FENCE_END,
        )
    }
}
