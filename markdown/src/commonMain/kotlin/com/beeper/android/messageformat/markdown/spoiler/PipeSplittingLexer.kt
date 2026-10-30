package com.beeper.android.messageformat.markdown.spoiler

import com.beeper.android.messageformat.markdown.MatrixMarkdownTokenTypes
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.lexer.GeneratedLexer

/**
 * Splits `|` into its own token, since [delegate] lexes pipes as plain text and delimiter parsers
 * only see tokens.
 */
internal class PipeSplittingLexer(private val delegate: GeneratedLexer) : GeneratedLexer {

    private var buffer: CharSequence = ""
    private var splitEnd = -1

    override var tokenStart: Int = 0
        private set
    override var tokenEnd: Int = 0
        private set
    override val state: Int
        get() = delegate.state

    override fun reset(buffer: CharSequence, start: Int, end: Int, initialState: Int) {
        this.buffer = buffer
        splitEnd = -1
        tokenStart = start
        tokenEnd = start
        delegate.reset(buffer, start, end, initialState)
    }

    override fun advance(): IElementType? {
        if (tokenEnd < splitEnd) return nextSplitToken()
        val type = delegate.advance()
        tokenStart = delegate.tokenStart
        tokenEnd = delegate.tokenEnd
        if (type != MarkdownTokenTypes.TEXT || buffer.indexOfPipe(tokenStart, tokenEnd) < 0) return type
        splitEnd = tokenEnd
        tokenEnd = tokenStart
        return nextSplitToken()
    }

    private fun nextSplitToken(): IElementType {
        tokenStart = tokenEnd
        if (isDelimiterPipe(tokenStart)) {
            tokenEnd = tokenStart + 1
            return MatrixMarkdownTokenTypes.PIPE
        }
        val pipeIndex = buffer.indexOfPipe(tokenStart, splitEnd)
        tokenEnd = if (pipeIndex < 0) splitEnd else pipeIndex
        return MarkdownTokenTypes.TEXT
    }

    private fun CharSequence.indexOfPipe(from: Int, to: Int): Int {
        for (index in from until to) {
            if (isDelimiterPipe(index)) return index
        }
        return -1
    }

    // A backslash-escaped pipe is literal text, as CommonMark escapes any punctuation
    private fun isDelimiterPipe(index: Int): Boolean =
        buffer[index] == PIPE && (index == 0 || buffer[index - 1] != '\\')

    private companion object {
        const val PIPE = '|'
    }
}
