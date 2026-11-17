package com.beeper.android.messageformat.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MatrixMarkdownFlavourAstTest {

    private fun ASTNode.find(type: IElementType): ASTNode? =
        if (this.type == type) this else children.firstNotNullOfOrNull { it.find(type) }

    private fun ASTNode.textOf(source: String): String = source.substring(startOffset, endOffset)

    @Test
    fun nodesKeepSourceOffsets() {
        val source = "say ||hi there|| and ~~bye~~"
        val tree = MatrixMarkdownParser(MarkdownFeature.ALL).parse(source)
        assertEquals("||hi there||", tree.find(MatrixMarkdownElementTypes.SPOILER)?.textOf(source))
        assertEquals("~~bye~~", tree.find(GFMElementTypes.STRIKETHROUGH)?.textOf(source))
    }

    @Test
    fun disabledFeaturesProduceNoNodes() {
        val source = "**b** `c` [l](u) > q"
        val tree = MatrixMarkdownParser(emptySet()).parse(source)
        assertNull(tree.find(MarkdownElementTypes.STRONG))
        assertNull(tree.find(MarkdownElementTypes.CODE_SPAN))
        assertNull(tree.find(MarkdownElementTypes.INLINE_LINK))
        assertNull(tree.find(MarkdownElementTypes.BLOCK_QUOTE))
    }

    @Test
    fun pipeSplittingDoesNotBreakOtherInlineSyntax() {
        val source = "a|b **c|d** `e|f`"
        val tree = MatrixMarkdownParser(MarkdownFeature.ALL).parse(source)
        assertEquals("**c|d**", tree.find(MarkdownElementTypes.STRONG)?.textOf(source))
        assertEquals("`e|f`", tree.find(MarkdownElementTypes.CODE_SPAN)?.textOf(source))
    }
}
