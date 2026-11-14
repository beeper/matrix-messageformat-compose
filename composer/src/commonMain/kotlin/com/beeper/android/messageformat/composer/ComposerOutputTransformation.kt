package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer

/**
 * Applies [render] of the text: its markdown formatting, and an [EMOTE_PLACEHOLDER] in place of each emote's text.
 * Styling the output instead of the edited text keeps the formatting out of the undo history.
 */
internal class ComposerOutputTransformation(
    private val render: (CharSequence) -> ComposerRender,
) : OutputTransformation {

    override fun TextFieldBuffer.transformOutput() {
        val render = render(asCharSequence())
        // Replacing from the back keeps earlier offsets valid
        for (emote in render.emotes.asReversed()) {
            replace(emote.range.start, emote.range.end, EMOTE_PLACEHOLDER.toString())
        }
        render.spanStyles.forEach {
            addStyle(it.item, render.toDisplayOffset(it.start), render.toDisplayOffset(it.end))
        }
        for (emote in render.emotes) {
            val start = render.toDisplayOffset(emote.range.start)
            addStyle(emote.style, start, start + 1)
        }
    }
}
