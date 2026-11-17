package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.ui.text.AnnotatedString

/**
 * A range of the text of a [MatrixMarkdownTextField] that the app points out, with whatever the app needs to
 * draw it and to react to a click on it as its item. A mark is not part of the content: it changes neither
 * the text nor its formatting.
 *
 * Marks follow the text through edits. An edit before a mark moves it along with its text, and an edit of
 * its own text removes it. Text typed or deleted at the edge of a mark leaves it on the same text, for the
 * app to decide whether it still applies.
 */
typealias ComposerTextMark<T> = AnnotatedString.Range<T>

/** Updates marks after an edit of a [TextFieldBuffer], as [ComposerTextMark] describes. */
internal object MarkReconciler {

    fun TextFieldBuffer.reconcileMarks(marks: List<ComposerTextMark<*>>): List<ComposerTextMark<*>> {
        if (marks.isEmpty() || changes.changeCount == 0) return marks
        val after = asCharSequence()
        val recorded = (0 until changes.changeCount).map { TextChange(changes.getOriginalRange(it), changes.getRange(it)) }
        // A keyboard may rewrite more text than it changes, which can be read in two ways, like "AA" from "A"
        return listOf(true, false)
            .map { prefixFirst -> recorded.mapNotNull { it.narrowed(originalText, after, prefixFirst) } }
            .map { narrowed -> marks.mapNotNull { it.after(narrowed) } }
            .maxBy { it.size }
    }

    private fun <T> ComposerTextMark<T>.after(changes: List<TextChange>): ComposerTextMark<T>? {
        var shift = 0
        for (change in changes) {
            val original = change.original
            when {
                original.start < end && original.end > start -> return null
                original.end <= start -> shift += change.updated.length - original.length
            }
        }
        return copy(start = start + shift, end = end + shift)
    }
}
