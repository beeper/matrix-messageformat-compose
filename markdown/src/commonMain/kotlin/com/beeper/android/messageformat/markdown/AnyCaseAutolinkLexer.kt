package com.beeper.android.messageformat.markdown

import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.flavours.gfm.lexer._GFMLexer
import org.intellij.markdown.lexer.GeneratedLexer

internal class AnyCaseAutolinkLexer(private val delegate: GeneratedLexer) : GeneratedLexer {

    private var buffer: CharSequence = ""
    private var lowercase: CharSequence = ""
    private var bufferStart = 0
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
        lowercase = String(CharArray(buffer.length) { buffer[it].let { c -> if (c in 'A'..'Z') c + ('a' - 'A') else c } })
        bufferStart = start
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
        if (state != _GFMLexer.AFTER_LINE_START) return type
        if (type == GFMTokenTypes.GFM_AUTOLINK) {
            val end = markerEnd(tokenStart, tokenEnd)
            if (end < tokenEnd) {
                tokenEnd = end
                delegate.reset(buffer, end, bufferEnd, state)
            }
            return type
        }
        if (type != MarkdownTokenTypes.TEXT) return type
        for (start in tokenStart until tokenEnd) {
            if (AUTOLINK_PREFIXES.none { lowercase.startsWith(it, start) }) continue
            if (start > bufferStart && buffer[start - 1].isLetterOrDigit()) continue
            delegate.reset(lowercase, start, bufferEnd, state)
            val isAutolink = delegate.advance() == GFMTokenTypes.GFM_AUTOLINK
            val end = if (isAutolink) markerEnd(start, delegate.tokenEnd) else tokenEnd
            delegate.reset(buffer, end, bufferEnd, state)
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

    private fun markerEnd(start: Int, end: Int): Int {
        if (start <= bufferStart) return end
        val marker = buffer[start - 1]
        if (marker !in MARKERS || (end < bufferEnd && buffer[end] == marker)) return end
        var cut = buffer.lastIndexOf(marker, end - 1)
        if (cut <= start) return end
        while (buffer[cut - 1] == marker) cut--
        return cut
    }

    private companion object {
        val AUTOLINK_PREFIXES = listOf("http://", "https://", "ftp://", "file://", "www.")
        const val MARKERS = "*_~|"
    }
}
