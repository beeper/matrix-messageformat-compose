package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import com.beeper.android.messageformat.composer.EntityReconciler.reconcile
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import com.beeper.android.messageformat.markdown.MatrixFormattedMessageDraft
import com.beeper.android.messageformat.markdown.validIn

/**
 * The text of a [MatrixMarkdownTextField] and the [MatrixInlineEntity]s in it, kept aligned through
 * every edit. [inputTransformation] must be attached to the text field.
 */
class MatrixMarkdownTextFieldState(
    initialText: String = "",
    initialEntities: List<MatrixInlineEntity> = emptyList(),
) {

    val textFieldState = TextFieldState(initialText)

    var entities: List<MatrixInlineEntity> by mutableStateOf(initialEntities.validIn(initialText))
        private set

    internal var sourceFormatter: MarkdownSourceFormatter? = null

    /** The text [entities] last matched, to detect edits that bypass [inputTransformation]. */
    private var reconciledText: String = initialText

    val text: CharSequence get() = textFieldState.text

    val inputTransformation: InputTransformation = InputTransformation { update() }

    fun edit(block: TextFieldBuffer.() -> Unit) {
        textFieldState.edit {
            block()
            update()
        }
    }

    internal fun refreshFormatting() {
        textFieldState.edit { applyFormatting() }
    }

    /** Realigns [entities] after edits that bypass [inputTransformation], such as undo. */
    internal fun reconcileUnobservedEdits() {
        if (textFieldState.text.contentEquals(reconciledText)) return
        textFieldState.edit {
            entities = EntityReconciler.reconcileUnobserved(entities, reconciledText, asCharSequence())
            finishEdit()
        }
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
            replace(replaced.min, replaced.max, "")
            selection = TextRange(replaced.min)
            // Reconciling may delete what is left of a cut entity, moving the cursor to the insertion point
            val kept = reconcile(entities)
            val start = selection.start
            val inserted = "$visibleText "
            replace(start, start, inserted)
            val shifted = kept.map {
                if (it.start >= start) it.moved(it.start + inserted.length, it.end + inserted.length) else it
            }
            entities = (shifted + entity(start, start + visibleText.length)).sortedBy { it.start }
            finishEdit()
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
        this.entities = entities.validIn(text)
        textFieldState.edit {
            replace(0, length, text)
            this.selection = TextRange(
                selection.start.coerceIn(0, text.length),
                selection.end.coerceIn(0, text.length),
            )
            finishEdit()
        }
    }

    fun toDraft(parser: MatrixMarkdownParser): MatrixFormattedMessageDraft =
        parser.toDraft(text.toString(), entities)

    private fun TextFieldBuffer.update() {
        entities = reconcile(entities)
        finishEdit()
    }

    private fun TextFieldBuffer.finishEdit() {
        reconciledText = asCharSequence().toString()
        applyFormatting()
    }

    private fun TextFieldBuffer.applyFormatting() {
        val formatter = sourceFormatter ?: return
        getSpanStyles(TextRange(0, length)).forEach { removeStyle(it) }
        formatter.applyTo(this, entities)
    }
}
