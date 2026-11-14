package com.beeper.android.messageformat.markdown.fence

/**
 * Code fence rules shared by [MatrixCodeFenceProvider] and [MatrixCodeFenceMarkerBlock]. Backtick
 * fences relax CommonMark because chat messages rarely put fences on their own lines.
 */
internal object MatrixCodeFence {

    private val OPENING_REGEX = Regex("^ {0,3}(~~~+|```+)([^`]*)$")
    private val LANGUAGE_REGEX = Regex("^[A-Za-z0-9_+#.-]+$")

    class Opening(val delimiter: String, val delimiterEnd: Int, val info: String) {
        val isBacktick: Boolean get() = delimiter[0] == '`'
        val infoIsLanguage: Boolean get() = !isBacktick || LANGUAGE_REGEX.matches(info.trim())
    }

    fun opening(lineFromPosition: CharSequence): Opening? {
        val match = OPENING_REGEX.find(lineFromPosition) ?: return null
        return Opening(match.groupValues[1], match.groups[1]!!.range.last + 1, match.groupValues[2])
    }

    /** Length of the code content preceding the closing fence on [line], or null if the line does not close it. */
    fun closingContentLength(line: CharSequence, delimiter: String): Int? {
        if (isClosingFenceLine(line, delimiter)) return 0
        if (delimiter[0] != '`') return null
        val trimmed = line.trimEnd()
        if (!trimmed.endsWith(delimiter)) return null
        return trimmed.length - delimiter.length
    }

    private fun isClosingFenceLine(line: CharSequence, delimiter: String): Boolean {
        val indented = line.take(3).takeWhile { it == ' ' }.length
        val fenceEnd = (indented until line.length).firstOrNull { line[it] != delimiter[0] } ?: line.length
        return fenceEnd - indented >= delimiter.length && line.subSequence(fenceEnd, line.length).isBlank()
    }
}
