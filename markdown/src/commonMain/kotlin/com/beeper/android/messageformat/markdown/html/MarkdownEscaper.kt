package com.beeper.android.messageformat.markdown.html

import com.beeper.android.messageformat.markdown.MarkdownFeature

/** Escapes the characters of plain text that would otherwise be read as markdown of [features]. */
internal class MarkdownEscaper(
    private val features: Set<MarkdownFeature>,
    private val tableCell: Boolean = false,
    private val linkLabel: Boolean = false,
) {
    private fun has(feature: MarkdownFeature) = feature in features

    private val emphasis = has(MarkdownFeature.BOLD) || has(MarkdownFeature.ITALIC)
    private val lists = has(MarkdownFeature.UNORDERED_LIST) || has(MarkdownFeature.ORDERED_LIST)
    private val links = has(MarkdownFeature.INLINE_LINK) || has(MarkdownFeature.IMAGE)

    /** [text] escaped where it follows [previous], or starts a line if that is null. */
    fun escape(text: String, previous: Char?): String {
        if (features.isEmpty()) return text
        val result = StringBuilder(text.length)
        var lineStart = previous == null || previous == '\n'
        var blockEscapeAt = -1
        for (index in text.indices) {
            val char = text[index]
            if (lineStart && char != ' ') blockEscapeAt = blockSyntaxEscapeIndex(text, index)
            val before = if (index == 0) previous else text[index - 1]
            if (index == blockEscapeAt || escapesInline(text, index, before, text.getOrNull(index + 1))) result.append('\\')
            result.append(char)
            lineStart = char == '\n' || (lineStart && char == ' ')
        }
        return result.toString()
    }

    /** Where to escape the line starting at [index] so it doesn't start a block, or -1 if it doesn't. */
    private fun blockSyntaxEscapeIndex(text: String, index: Int): Int {
        val line = text.substring(index).substringBefore('\n')
        val char = line[0]
        val escapesFirst = when {
            has(MarkdownFeature.HEADING) && char == '#' -> HEADING_START.matchesAt(line, 0)
            has(MarkdownFeature.BLOCKQUOTE) && char == '>' -> true
            has(MarkdownFeature.HORIZONTAL_RULE) && THEMATIC_BREAK.matches(line) -> true
            lists && char in "-*+" -> line.length == 1 || line[1] == ' ' || line[1] == '\t'
            has(MarkdownFeature.CODE_BLOCK) && (line.startsWith("```") || line.startsWith("~~~")) -> true
            else -> false
        }
        if (escapesFirst) return index
        // A backslash before the digits would stay literal, so the delimiter is escaped instead
        val orderedList = if (lists) ORDERED_LIST_START.matchAt(line, 0) else null
        return orderedList?.let { index + it.groups[1]!!.range.first } ?: -1
    }

    private fun escapesInline(text: String, index: Int, before: Char?, after: Char?): Boolean = when (text[index]) {
        '\\' -> after == null || after == '\n' || after in ASCII_PUNCTUATION
        // Delimiter runs between whitespace can't open or close emphasis, nor can underscores within a word
        '*' -> emphasis && !(before.isBlank() && after.isKnownBlank())
        '_' -> emphasis && !(before.isBlank() && after.isKnownBlank()) &&
            !(before?.isLetterOrDigit() == true && after?.isLetterOrDigit() == true)
        '`' -> has(MarkdownFeature.INLINE_CODE) || has(MarkdownFeature.CODE_BLOCK)
        '~' -> has(MarkdownFeature.STRIKETHROUGH) && (before == '~' || after == '~')
        '|' -> tableCell || (has(MarkdownFeature.SPOILER) && (before == '|' || after == '|'))
        '[' -> linkLabel
        ']' -> linkLabel || (links && after == '(')
        '<' -> has(MarkdownFeature.AUTOLINK) && startsAutolink(text, index)
        else -> false
    }

    private fun startsAutolink(text: String, index: Int): Boolean {
        val end = text.indexOf('>', index + 1)
        if (end <= index + 1) return false
        return text.substring(index + 1, end).none { it.isWhitespace() || it == '<' }
    }

    private fun Char?.isBlank() = this == null || isWhitespace()

    // Markdown syntax may follow the end of the text
    private fun Char?.isKnownBlank() = this != null && isWhitespace()

    private companion object {
        const val ASCII_PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"
        val HEADING_START = Regex("""#{1,6}(?:[ \t]|$)""")
        val THEMATIC_BREAK = Regex("""([-*_])(?:[ \t]*\1){2,}[ \t]*""")
        val ORDERED_LIST_START = Regex("""\d{1,9}([.)])(?:[ \t]|$)""")
    }
}
