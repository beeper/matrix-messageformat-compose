package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import com.beeper.android.messageformat.composer.EntityReconciler.reconcile
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import com.beeper.android.messageformat.markdown.MatrixMarkdownContent

/** The text of a [MatrixMarkdownTextField] and the [MatrixInlineEntity]s in it, kept aligned through every edit. */
class MatrixMarkdownTextFieldState(
    initialText: String = "",
    initialEntities: List<MatrixInlineEntity> = emptyList(),
) {

    val textFieldState = TextFieldState(initialText)

    private val history = ContentHistory()

    /** The content as of the last edit that went through this class. */
    private var aligned by mutableStateOf(MatrixMarkdownContent(initialText, initialEntities).also(history::record))

    /** [aligned] caught up with an edit that bypassed this class, such as undo. Only a cache for [contentFor]. */
    private var realigned: MatrixMarkdownContent? = null

    /** The text with its entities, which always fit that text. */
    val content: MatrixMarkdownContent get() = contentFor(textFieldState.text)

    val text: CharSequence get() = textFieldState.text

    val entities: List<MatrixInlineEntity> get() = content.entities

    internal val inputTransformation: InputTransformation = InputTransformation { update() }

    fun edit(block: TextFieldBuffer.() -> Unit) {
        textFieldState.edit {
            block()
            update()
        }
    }

    /**
     * The content for [text], the current text of [textFieldState]. After an edit that bypassed this class,
     * a text seen before gets its entities back, and any other text keeps the entities the edit left intact.
     */
    internal fun contentFor(text: CharSequence): MatrixMarkdownContent {
        val observed = aligned
        val latest = realigned ?: observed
        if (text.contentEquals(latest.text)) return latest
        val string = text.toString()
        val content = history[string]
            ?: MatrixMarkdownContent(string, EntityReconciler.reconcileUnobserved(latest.entities, latest.text, string))
        realigned = content
        return content
    }

    /**
     * Replaces [replaced] with [visibleText] plus a space, tracked as the entity [entity] builds
     * for that range.
     */
    fun insertEntity(
        replaced: TextRange,
        visibleText: String,
        entity: (start: Int, end: Int) -> MatrixInlineEntity,
    ) {
        textFieldState.edit {
            val entities = contentFor(originalText).entities
            delete(replaced.min, replaced.max)
            selection = TextRange(replaced.min)
            // Reconciling may delete what is left of a cut entity, moving the cursor to the insertion point
            val kept = reconcile(entities)
            val start = selection.start
            val inserted = "$visibleText "
            replace(start, start, inserted)
            val shifted = kept.map {
                if (it.start >= start) it.moved(it.start + inserted.length, it.end + inserted.length) else it
            }
            align(shifted + entity(start, start + visibleText.length), asCharSequence())
            selection = TextRange(start + inserted.length)
        }
    }

    fun insertText(text: String) {
        edit { replace(selection.min, selection.max, text) }
    }

    fun setContent(
        text: String,
        entities: List<MatrixInlineEntity> = emptyList(),
        selection: TextRange = TextRange(text.length),
    ) {
        textFieldState.edit {
            replace(0, length, text)
            this.selection = TextRange(
                selection.start.coerceIn(0, text.length),
                selection.end.coerceIn(0, text.length),
            )
            align(entities, text)
        }
    }

    private fun TextFieldBuffer.update() = align(reconcile(contentFor(originalText).entities), asCharSequence())

    private fun align(entities: List<MatrixInlineEntity>, text: CharSequence) {
        val content = MatrixMarkdownContent(text.toString(), entities)
        aligned = content
        realigned = null
        history.record(content)
    }
}

/** The latest contents by their text, which give a text its entities back when undo or redo returns to it. */
private class ContentHistory {
    private val contents = LinkedHashMap<String, MatrixMarkdownContent>()

    operator fun get(text: String): MatrixMarkdownContent? = contents[text]

    fun record(content: MatrixMarkdownContent) {
        contents.remove(content.text)
        contents[content.text] = content
        if (contents.size > CAPACITY) contents.remove(contents.keys.first())
    }

    private companion object {
        const val CAPACITY = 100
    }
}
