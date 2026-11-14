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
import com.beeper.android.messageformat.markdown.validIn

/** The text of a [MatrixMarkdownTextField] and the [MatrixInlineEntity]s in it, kept aligned through every edit. */
class MatrixMarkdownTextFieldState(
    initialText: String = "",
    initialEntities: List<MatrixInlineEntity> = emptyList(),
) {

    val textFieldState = TextFieldState(initialText)

    var entities: List<MatrixInlineEntity> by mutableStateOf(initialEntities.validIn(initialText))
        private set

    /** The text [entities] last matched, to detect edits that bypass [inputTransformation]. */
    private var reconciledText: String = initialText

    val text: CharSequence get() = textFieldState.text

    internal val inputTransformation: InputTransformation = InputTransformation { update() }

    fun edit(block: TextFieldBuffer.() -> Unit) {
        textFieldState.edit {
            block()
            update()
        }
    }

    /** Realigns [entities] after edits that bypass [inputTransformation], such as undo. */
    internal fun reconcileUnobservedEdits() {
        if (text.contentEquals(reconciledText)) return
        align(EntityReconciler.reconcileUnobserved(entities, reconciledText, text), text)
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
            align((shifted + entity(start, start + visibleText.length)).sortedBy { it.start }, asCharSequence())
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
        align(entities.validIn(text), text)
        textFieldState.edit {
            replace(0, length, text)
            this.selection = TextRange(
                selection.start.coerceIn(0, text.length),
                selection.end.coerceIn(0, text.length),
            )
        }
    }

    private fun TextFieldBuffer.update() = align(reconcile(entities), asCharSequence())

    private fun align(entities: List<MatrixInlineEntity>, text: CharSequence) {
        this.entities = entities
        reconciledText = text.toString()
    }
}
