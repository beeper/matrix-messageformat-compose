package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString

/**
 * Maps untransformed text offsets to the transformed text, in which each inline image is one
 * placeholder character.
 */
internal class TransformedOffsets(images: List<ComposerInlineImage>) {
    private val imageRanges = images.map { it.range }.sortedBy { it.start }

    fun toTransformed(offset: Int): Int {
        var shift = 0
        for ((_, start, end) in imageRanges) {
            if (end <= offset) shift += end - start - 1
        }
        return offset - shift
    }

    /** [text] as the field lays it out, with its string annotations moved to transformed offsets. */
    fun transform(text: AnnotatedString): AnnotatedString = buildAnnotatedString {
        var from = 0
        for ((_, start, end) in imageRanges) {
            append(text.text, from, start)
            append(INLINE_IMAGE_PLACEHOLDER)
            from = end
        }
        append(text.text, from, text.length)
        text.getStringAnnotations(0, text.length).forEach {
            addStringAnnotation(it.tag, it.item, toTransformed(it.start), toTransformed(it.end))
        }
    }
}
