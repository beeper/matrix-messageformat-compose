package com.beeper.android.messageformat.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.lexer._GFMLexer
import org.intellij.markdown.lexer.GeneratedLexer

internal class AnyCaseAutolinkLexer(private val delegate: GeneratedLexer) : GeneratedLexer {

    private var buffer: CharSequence = ""
    private var lowercase: CharSequence = ""
    private var bufferEnd = 0
    private var autolinkEnd = -1

    override var tokenStart: Int = 0
        private set
    override var tokenEnd: Int = 0
        private set
    override val state: Int
        get() = delegate.state

    override fun reset(buffer: CharSequence, start: Int, end: Int, initialState: Int) {
        this.buffer = buffer
        lowercase = String(CharArray(buffer.length) { buffer[it].lowercaseChar() })
        bufferEnd = end
        autolinkEnd = -1
        tokenStart = start
        tokenEnd = start
        delegate.reset(buffer, start, end, initialState)
    }

    override fun advance(): IElementType? {
        if (autolinkEnd >= 0) {
            tokenStart = tokenEnd
            tokenEnd = autolinkEnd
            autolinkEnd = -1
            return GFMTokenTypes.GFM_AUTOLINK
        }
        val type = delegate.advance()
        tokenStart = delegate.tokenStart
        tokenEnd = delegate.tokenEnd
        val state = delegate.state
        if (type != MarkdownTokenTypes.TEXT || state != _GFMLexer.AFTER_LINE_START) return type
        for (start in tokenStart until tokenEnd) {
            if (AUTOLINK_PREFIXES.none { lowercase.startsWith(it, start) }) continue
            delegate.reset(lowercase, start, bufferEnd, state)
            val isAutolink = delegate.advance() == GFMTokenTypes.GFM_AUTOLINK
            val end = delegate.tokenEnd
            delegate.reset(buffer, if (isAutolink) end else tokenEnd, bufferEnd, state)
            if (!isAutolink) continue
            if (start == tokenStart) {
                tokenEnd = end
                return GFMTokenTypes.GFM_AUTOLINK
            }
            autolinkEnd = end
            tokenEnd = start
            return type
        }
        return type
    }

    private companion object {
        val AUTOLINK_PREFIXES = listOf("http://", "https://", "ftp://", "file://", "www.")
    }
}
