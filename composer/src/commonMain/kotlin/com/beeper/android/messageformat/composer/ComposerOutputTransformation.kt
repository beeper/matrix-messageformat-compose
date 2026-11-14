package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import com.beeper.android.messageformat.markdown.MatrixMarkdownContent

/**
 * Formats the markdown and replaces each inline image's text with [INLINE_IMAGE_PLACEHOLDER].
 * Styling the output instead of the edited text keeps the formatting out of the undo history.
 */
internal class ComposerOutputTransformation(
    private val sourceFormatter: MarkdownSourceFormatter,
    private val contentFor: (CharSequence) -> MatrixMarkdownContent,
    private val images: () -> List<ComposerInlineImage>,
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        val styles = sourceFormatter.spanStyles(contentFor(asCharSequence()))
        val shownImages = images().filter { it.matches(asCharSequence()) }
        for (image in shownImages.sortedByDescending { it.range.start }) {
            replace(image.range.start, image.range.end, INLINE_IMAGE_PLACEHOLDER.toString())
        }
        val offsets = TransformedOffsets(shownImages)
        styles.forEach { addStyle(it.item, offsets.toTransformed(it.start), offsets.toTransformed(it.end)) }
        for (image in shownImages) {
            val start = offsets.toTransformed(image.range.start)
            addStyle(image.style, start, start + 1)
        }
    }
}
