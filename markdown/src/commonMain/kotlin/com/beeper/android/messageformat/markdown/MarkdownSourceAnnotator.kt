package com.beeper.android.messageformat.markdown

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.beeper.android.messageformat.MatrixBodyAnnotations
import com.beeper.android.messageformat.MatrixBodyParseResult
import com.beeper.android.messageformat.SpanAttributes
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
 * Annotate markdown text for inline formatting.
 */
internal class MarkdownSourceAnnotator(private val features: Set<MarkdownFeature>) {

    fun annotate(markdown: String, tree: ASTNode): MatrixBodyParseResult {
        val expandableItems = mutableSetOf<Int>()
        val text = buildAnnotatedString {
            append(markdown)
            Visitor(this, markdown, expandableItems).visit(tree)
        }
        return MatrixBodyParseResult(text = text, expandableItems = expandableItems)
    }

    private inner class Visitor(
        private val builder: AnnotatedString.Builder,
        private val source: String,
        private val expandableItems: MutableSet<Int>,
    ) {
        fun visit(node: ASTNode) {
            when (node.type) {
                MarkdownElementTypes.STRONG -> emphasis(node, MarkdownFeature.BOLD, BOLD, markerTokens = 2)
                MarkdownElementTypes.EMPH -> emphasis(node, MarkdownFeature.ITALIC, ITALIC, markerTokens = 1)
                GFMElementTypes.STRIKETHROUGH ->
                    emphasis(node, MarkdownFeature.STRIKETHROUGH, STRIKETHROUGH, markerTokens = 2)
                MatrixMarkdownElementTypes.SPOILER -> spoiler(node)
                MarkdownElementTypes.CODE_SPAN -> codeSpan(node)
                MarkdownElementTypes.CODE_FENCE -> codeFence(node)
                in HEADINGS -> heading(node, HEADINGS.indexOf(node.type) + 1)
                MarkdownElementTypes.INLINE_LINK -> inlineLink(node)
                MarkdownElementTypes.AUTOLINK -> autolink(node)
                MarkdownElementTypes.IMAGE -> image(node)
                MarkdownTokenTypes.BLOCK_QUOTE -> if (MarkdownFeature.BLOCKQUOTE in features) marker(node)
                in MARKER_TOKENS -> marker(node)
                else -> visitChildren(node.children)
            }
        }

        private fun visitChildren(children: List<ASTNode>) = children.forEach { visit(it) }

        private fun emphasis(node: ASTNode, feature: MarkdownFeature, style: SpanStyle, markerTokens: Int) {
            // Unbalanced delimiter runs can produce a node without content between its markers
            if (node.children.size <= 2 * markerTokens) return visitChildren(node.children)
            val content = node.children.subList(markerTokens, node.children.size - markerTokens)
            if (feature in features) {
                markerRange(node.startOffset, content.first().startOffset)
                markerRange(content.last().endOffset, node.endOffset)
                builder.addStyle(style, content.first().startOffset, content.last().endOffset)
            }
            visitChildren(content)
        }

        private fun spoiler(node: ASTNode) {
            val inner = node.children.subList(2, node.children.size - 2)
            val separator = inner.indexOfFirst { it.type == MatrixMarkdownTokenTypes.PIPE }
            val content = if (separator < 0) inner else inner.subList(separator + 1, inner.size)
            markerRange(node.startOffset, inner.firstOrNull()?.startOffset ?: node.endOffset)
            markerRange(inner.lastOrNull()?.endOffset ?: node.startOffset, node.endOffset)
            if (separator >= 0) markerRange(inner.first().startOffset, inner[separator].endOffset)
            if (content.isNotEmpty()) {
                val start = content.first().startOffset
                val attributes = SpanAttributes(fgColor = null, bgColor = null, isSpoiler = true, revealId = start)
                expandableItems.add(start)
                annotate(MatrixBodyAnnotations.SPAN, Json.encodeToString(attributes), start, content.last().endOffset)
            }
            visitChildren(content)
        }

        private fun codeSpan(node: ASTNode) {
            marker(node.children.first())
            marker(node.children.last())
            val content = node.children.subList(1, node.children.size - 1)
            if (content.isNotEmpty()) {
                annotate(MatrixBodyAnnotations.INLINE_CODE, "", content.first().startOffset, content.last().endOffset)
            }
        }

        private fun codeFence(node: ASTNode) {
            node.children.filter { it.type in FENCE_MARKER_TOKENS }.forEach { marker(it) }
            val content = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
            if (content.isNotEmpty()) {
                annotate(MatrixBodyAnnotations.BLOCK_CODE, "", content.first().startOffset, content.last().endOffset)
            }
        }

        // The heading style spans the markers too, so the whole line reads as one heading
        private fun heading(node: ASTNode, level: Int) {
            node.children.filter { it.type == MarkdownTokenTypes.ATX_HEADER }.forEach { marker(it) }
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
            markerRange(node.startOffset, label.startOffset + 1)
            markerRange(label.endOffset - 1, node.endOffset)
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
            markerRange(node.startOffset, node.startOffset + 1)
            markerRange(node.endOffset - 1, node.endOffset)
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
                ?: return markerRange(node.startOffset, node.endOffset)
            markerRange(node.startOffset, label.startOffset + 1)
            markerRange(label.endOffset - 1, node.endOffset)
        }

        private fun marker(node: ASTNode) = markerRange(node.startOffset, node.endOffset)

        private fun markerRange(start: Int, end: Int) = annotate(MatrixBodyAnnotations.SYNTAX_MARKER, "", start, end)

        private fun annotate(tag: String, value: String, start: Int, end: Int) {
            if (start < end) builder.addStringAnnotation(tag, value, start, end)
        }
    }

    private companion object {
        val BOLD = SpanStyle(fontWeight = FontWeight.Bold)
        val ITALIC = SpanStyle(fontStyle = FontStyle.Italic)
        val STRIKETHROUGH = SpanStyle(textDecoration = TextDecoration.LineThrough)

        val HEADINGS: List<IElementType> = listOf(
            MarkdownElementTypes.ATX_1,
            MarkdownElementTypes.ATX_2,
            MarkdownElementTypes.ATX_3,
            MarkdownElementTypes.ATX_4,
            MarkdownElementTypes.ATX_5,
            MarkdownElementTypes.ATX_6,
        )

        val MARKER_TOKENS: Set<IElementType> = setOf(
            MarkdownTokenTypes.LIST_BULLET,
            MarkdownTokenTypes.LIST_NUMBER,
            MarkdownTokenTypes.HORIZONTAL_RULE,
            GFMTokenTypes.TABLE_SEPARATOR,
        )

        val FENCE_MARKER_TOKENS: Set<IElementType> = setOf(
            MarkdownTokenTypes.CODE_FENCE_START,
            MarkdownTokenTypes.FENCE_LANG,
            MarkdownTokenTypes.CODE_FENCE_END,
        )
    }
}
