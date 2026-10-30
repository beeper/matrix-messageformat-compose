package com.beeper.android.messageformat.markdown.fence

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.parser.LookaheadText
import org.intellij.markdown.parser.ProductionHolder
import org.intellij.markdown.parser.constraints.MarkdownConstraints
import org.intellij.markdown.parser.constraints.eatItselfFromString
import org.intellij.markdown.parser.constraints.extendsPrev
import org.intellij.markdown.parser.constraints.getCharsEaten
import org.intellij.markdown.parser.markerblocks.MarkerBlock
import org.intellij.markdown.parser.markerblocks.MarkerBlockImpl
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import kotlin.math.min

/** The CommonMark code fence block, except that a backtick fence also closes at the end of a content line. */
internal class MatrixCodeFenceMarkerBlock(
    constraints: MarkdownConstraints,
    private val productionHolder: ProductionHolder,
    private val delimiter: String,
) : MarkerBlockImpl(constraints, productionHolder.mark()) {

    private var realInterestingOffset = -1

    override fun allowsSubBlocks(): Boolean = false

    override fun isInterestingOffset(pos: LookaheadText.Position): Boolean = true

    override fun calcNextInterestingOffset(pos: LookaheadText.Position): Int = pos.nextLineOrEofOffset

    override fun getDefaultAction(): MarkerBlock.ClosingAction = MarkerBlock.ClosingAction.DONE

    override fun getDefaultNodeType(): IElementType = MarkdownElementTypes.CODE_FENCE

    override fun doProcessToken(
        pos: LookaheadText.Position,
        currentConstraints: MarkdownConstraints,
    ): MarkerBlock.ProcessingResult {
        if (pos.offset < realInterestingOffset || pos.offsetInCurrentLine != -1) {
            return MarkerBlock.ProcessingResult.CANCEL
        }
        val nextLineConstraints = constraints.applyToNextLine(pos)
        if (!nextLineConstraints.extendsPrev(constraints)) {
            return MarkerBlock.ProcessingResult.DEFAULT
        }
        val nextLineOffset = pos.nextLineOrEofOffset
        realInterestingOffset = nextLineOffset
        val line = nextLineConstraints.eatItselfFromString(pos.currentLine)
        val contentStart = min(pos.offset + 1 + nextLineConstraints.getCharsEaten(pos.currentLine), nextLineOffset)
        val closingContentLength = MatrixCodeFence.closingContentLength(line, delimiter)
        if (closingContentLength == null) {
            if (contentStart < nextLineOffset) {
                addNode(contentStart..nextLineOffset, MarkdownTokenTypes.CODE_FENCE_CONTENT)
            }
            return MarkerBlock.ProcessingResult.CANCEL
        }
        val contentEnd = contentStart + closingContentLength
        if (closingContentLength > 0) {
            addNode(contentStart..contentEnd, MarkdownTokenTypes.CODE_FENCE_CONTENT)
            addNode(contentEnd..nextLineOffset, MarkdownTokenTypes.CODE_FENCE_END)
        } else {
            addNode(pos.offset + 1..nextLineOffset, MarkdownTokenTypes.CODE_FENCE_END)
        }
        scheduleProcessingResult(nextLineOffset, MarkerBlock.ProcessingResult.DEFAULT)
        return MarkerBlock.ProcessingResult.CANCEL
    }

    private fun addNode(range: IntRange, type: IElementType) {
        productionHolder.addProduction(listOf(SequentialParser.Node(range, type)))
    }
}
