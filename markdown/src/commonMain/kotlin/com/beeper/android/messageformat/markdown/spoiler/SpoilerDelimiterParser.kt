package com.beeper.android.messageformat.markdown.spoiler

import com.beeper.android.messageformat.markdown.MatrixMarkdownElementTypes
import com.beeper.android.messageformat.markdown.MatrixMarkdownTokenTypes
import org.intellij.markdown.parser.sequentialparsers.DelimiterParser
import org.intellij.markdown.parser.sequentialparsers.SequentialParser
import org.intellij.markdown.parser.sequentialparsers.TokensCache
import org.intellij.markdown.parser.sequentialparsers.impl.EmphStrongDelimiterParser

/**
 * `||spoiler||` with an optional reason, `||reason|spoiler||`. Modelled on the GFM
 * strikethrough parser, except that only runs of exactly two pipes delimit anything.
 */
internal class SpoilerDelimiterParser : DelimiterParser() {

    override fun scan(tokens: TokensCache, iterator: TokensCache.Iterator, delimiters: MutableList<Info>): Int {
        if (iterator.type != MatrixMarkdownTokenTypes.PIPE) return 0
        var runLength = 1
        var rightIterator = iterator
        while (runLength < maxAdvance && rightIterator.rawLookup(1) == MatrixMarkdownTokenTypes.PIPE) {
            rightIterator = rightIterator.advance()
            runLength++
        }
        if (runLength != DELIMITER_LENGTH) return runLength
        val (canOpen, canClose) = canOpenClose(tokens, iterator, rightIterator, canSplitText = true)
        repeat(runLength) { offset ->
            delimiters.add(
                Info(
                    tokenType = MatrixMarkdownTokenTypes.PIPE,
                    position = iterator.index + offset,
                    length = 0,
                    canOpen = canOpen,
                    canClose = canClose,
                    marker = '|',
                )
            )
        }
        return runLength
    }

    override fun process(
        tokens: TokensCache,
        iterator: TokensCache.Iterator,
        delimiters: MutableList<Info>,
        result: SequentialParser.ParsingResultBuilder,
    ) {
        var index = delimiters.size - 1
        while (index > 0) {
            val opener = delimiters[index]
            if (opener.tokenType != MatrixMarkdownTokenTypes.PIPE || opener.closerIndex < 0) {
                index--
                continue
            }
            var openerIndex = index
            var closerIndex = opener.closerIndex
            var matched = 1
            while (EmphStrongDelimiterParser.areAdjacentSameMarkers(delimiters, openerIndex, closerIndex)) {
                openerIndex--
                closerIndex++
                matched++
            }
            if (matched == DELIMITER_LENGTH) {
                val range = delimiters[openerIndex].position..delimiters[closerIndex].position + 1
                result.withNode(SequentialParser.Node(range, MatrixMarkdownElementTypes.SPOILER))
            }
            index = openerIndex - 1
        }
    }

    private companion object {
        const val DELIMITER_LENGTH = 2
    }
}
