package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

/** Replaces each inline image's text with [INLINE_IMAGE_PLACEHOLDER]. */
internal class ComposerOutputTransformation(
    private val images: () -> List<ComposerInlineImage>,
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        for (image in images().sortedByDescending { it.range.start }) {
            if (!image.matches(asCharSequence())) continue
            val start = image.range.start
            replace(start, image.range.end, INLINE_IMAGE_PLACEHOLDER.toString())
            addStyle(image.style, start, start + 1)
        }
    }
}
