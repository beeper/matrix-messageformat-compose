package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.UndoState
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

    /** Not public, so that undo and redo are the only edits that do not go through this class. */
    internal val textFieldState = TextFieldState(initialText)

    private val history = ContentHistory()

    /** The content as of the last edit other than undo and redo. */
    private var aligned by mutableStateOf(MatrixMarkdownContent(initialText, initialEntities))

    /** What [contentFor] made of [aligned] after undo or redo. */
    private var realigned: MatrixMarkdownContent? = null

    /** The text with its entities, which always fit that text. */
    val content: MatrixMarkdownContent get() = contentFor(textFieldState.text)

    val text: CharSequence get() = textFieldState.text

    val entities: List<MatrixInlineEntity> get() = content.entities

    val selection: TextRange get() = textFieldState.selection

    val undoState: UndoState get() = textFieldState.undoState

    internal val inputTransformation: InputTransformation = InputTransformation { update() }

    fun edit(block: TextFieldBuffer.() -> Unit) {
        textFieldState.edit {
            block()
            update()
        }
    }

    /**
     * The content for [text], the current text of [textFieldState]. After undo or redo, a text that [history]
     * knows gets its entities back, and any other text keeps the entities that the change left intact.
     */
    internal fun contentFor(text: CharSequence): MatrixMarkdownContent {
        // Read even when realigned, so that whoever observes the content sees the next aligned edit
        val observed = aligned
        val latest = realigned ?: observed
        if (text.contentEquals(latest.text)) return latest
        val string = text.toString()
        val content = history[string] ?: MatrixMarkdownContent(string, latest.entitiesAfterChangeTo(string))
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
            val previous = contentFor(originalText)
            delete(replaced.min, replaced.max)
            selection = TextRange(replaced.min)
            // Reconciling may delete what is left of a cut entity, moving the cursor to the insertion point
            val kept = reconcile(previous.entities)
            val start = selection.start
            val inserted = "$visibleText "
            replace(start, start, inserted)
            val shifted = kept.map {
                if (it.start >= start) it.moved(it.start + inserted.length, it.end + inserted.length) else it
            }
            align(previous, shifted + entity(start, start + visibleText.length), asCharSequence())
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
            val previous = contentFor(originalText)
            replace(0, length, text)
            this.selection = TextRange(
                selection.start.coerceIn(0, text.length),
                selection.end.coerceIn(0, text.length),
            )
            align(previous, entities, text)
        }
    }

    private fun TextFieldBuffer.update() {
        val previous = contentFor(originalText)
        align(previous, reconcile(previous.entities), asCharSequence())
    }

    private fun align(previous: MatrixMarkdownContent, entities: List<MatrixInlineEntity>, text: CharSequence) {
        val content = MatrixMarkdownContent(text.toString(), entities)
        aligned = content
        realigned = null
        history.record(previous, content)
    }
}

private fun MatrixMarkdownContent.entitiesAfterChangeTo(text: String): List<MatrixInlineEntity> =
    EntityReconciler.reconcileUnobserved(entities, this.text, text)

/**
 * The contents on both sides of the latest edits whose entities the texts alone do not tell, such as an
 * edit that removed an entity. They give a text its entities back when undo or redo returns to it.
 */
private class ContentHistory {
    private val contents = LinkedHashMap<String, MatrixMarkdownContent>()

    operator fun get(text: String): MatrixMarkdownContent? = contents[text]

    fun record(previous: MatrixMarkdownContent, content: MatrixMarkdownContent) {
        if (previous == content) return
        val entitiesFollowFromTexts = previous.entitiesAfterChangeTo(content.text) == content.entities &&
            content.entitiesAfterChangeTo(previous.text) == previous.entities
        if (!entitiesFollowFromTexts) {
            put(previous)
            put(content)
        } else if (content.text in contents) {
            // The text has other entities now than when it was recorded
            contents[content.text] = content
        }
    }

    private fun put(content: MatrixMarkdownContent) {
        contents.remove(content.text)
        contents[content.text] = content
        if (contents.size > CAPACITY) contents.remove(contents.keys.first())
    }

    private companion object {
        const val CAPACITY = 100
    }
}
