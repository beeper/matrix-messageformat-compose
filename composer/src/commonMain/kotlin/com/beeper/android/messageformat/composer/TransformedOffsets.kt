package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Maps untransformed text offsets to the transformed text, in which each of [emoteRanges] is one
 * placeholder character.
 */
internal class TransformedOffsets(emoteRanges: List<TextRange>) {
    private val emoteRanges = emoteRanges.sortedBy { it.start }

    fun toTransformed(offset: Int): Int {
        var shift = 0
        for (range in emoteRanges) {
            if (range.end <= offset) shift += range.length - 1
        }
        return offset - shift
    }

    /** [text] as the field lays it out, with its string annotations moved to transformed offsets. */
    fun transform(text: AnnotatedString): AnnotatedString = buildAnnotatedString {
        var from = 0
        for (range in emoteRanges) {
            append(text.text, from, range.start)
            append(EMOTE_PLACEHOLDER)
            from = range.end
        }
        append(text.text, from, text.length)
        text.getStringAnnotations(0, text.length).forEach {
            addStringAnnotation(it.tag, it.item, toTransformed(it.start), toTransformed(it.end))
        }
    }
}
