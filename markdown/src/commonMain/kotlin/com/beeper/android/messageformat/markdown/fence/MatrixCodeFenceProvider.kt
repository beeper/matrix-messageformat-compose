package com.beeper.android.messageformat.markdown.fence

import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.MarkerProcessor
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.constraints.eatItselfFromString
import org.intellij.markdown.parser.constraints.extendsPrev
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockProvider
import org.intellij.markdown.parser.sequentialparsers.SequentialParser

/**
 * Opens code fences. Text after a backtick fence is a language only when it is a single word,
 * otherwise it is the first line of code, and an unclosed backtick fence is not a code block.
 */
internal class MatrixCodeFenceProvider : MarkerBlockProvider<MarkerProcessor.StateInfo> {

    override fun createMarkerBlocks(
        pos: LookaheadText.Position,
        productionHolder: ProductionHolder,
        stateInfo: MarkerProcessor.StateInfo,
    ): List<MarkerBlock> {
        val opening = opening(pos, stateInfo.currentConstraints) ?: return emptyList()
        addOpeningNodes(pos, opening, productionHolder)
        return listOf(MatrixCodeFenceMarkerBlock(stateInfo.currentConstraints, productionHolder, opening.delimiter))
    }

    override fun interruptsParagraph(pos: LookaheadText.Position, constraints: MarkdownConstraints): Boolean =
        opening(pos, constraints) != null

    private fun opening(pos: LookaheadText.Position, constraints: MarkdownConstraints): MatrixCodeFence.Opening? {
        if (!MarkerBlockProvider.isStartOfLineWithConstraints(pos, constraints)) return null
        val opening = MatrixCodeFence.opening(pos.currentLineFromPosition) ?: return null
        if (opening.isBacktick && !hasClosingFence(pos, constraints, opening.delimiter)) return null
        return opening
    }

    private fun hasClosingFence(
        pos: LookaheadText.Position,
        constraints: MarkdownConstraints,
        delimiter: String,
    ): Boolean {
        var current = pos.nextLinePosition()
        while (current != null) {
            val lineConstraints = constraints.applyToNextLine(current)
            if (!lineConstraints.extendsPrev(constraints)) return false
            val line = lineConstraints.eatItselfFromString(current.currentLine)
            if (MatrixCodeFence.closingContentLength(line, delimiter) != null) return true
            current = current.nextLinePosition()
        }
        return false
    }

    private fun addOpeningNodes(
        pos: LookaheadText.Position,
        opening: MatrixCodeFence.Opening,
        productionHolder: ProductionHolder,
    ) {
        val delimiterEnd = pos.offset + opening.delimiterEnd
        val lineEnd = pos.nextLineOrEofOffset
        val fenceStart = SequentialParser.Node(pos.offset..delimiterEnd, MarkdownTokenTypes.CODE_FENCE_START)
        productionHolder.addProduction(listOf(fenceStart))
        if (delimiterEnd == lineEnd) return
        val infoType =
            if (opening.infoIsLanguage) MarkdownTokenTypes.FENCE_LANG else MarkdownTokenTypes.CODE_FENCE_CONTENT
        productionHolder.addProduction(listOf(SequentialParser.Node(delimiterEnd..lineEnd, infoType)))
    }
}
