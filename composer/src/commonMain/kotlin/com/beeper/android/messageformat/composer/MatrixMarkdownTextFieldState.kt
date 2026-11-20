package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.UndoState
import androidx.compose.foundation.text.input.delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import com.beeper.android.messageformat.composer.EntityReconciler.reconcile
import com.beeper.android.messageformat.composer.MarkReconciler.reconcileMarks
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import com.beeper.android.messageformat.markdown.MatrixMarkdownContent

/**
 * The text of a [MatrixMarkdownTextField] with the [MatrixInlineEntity]s and [ComposerTextMark]s in it, kept
 * aligned through every edit.
 */
class MatrixMarkdownTextFieldState(
    initialText: String = "",
    initialEntities: List<MatrixInlineEntity> = emptyList(),
    initialSelection: TextRange = TextRange(initialText.length),
) {

    /** Not public, so that undo and redo are the only edits that do not go through this class. */
    internal val textFieldState = TextFieldState(initialText, initialSelection)

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

    private var marked by mutableStateOf(Marks(initialText, emptyList()))

    /**
     * The marks from [setMarks], which have followed the text since as [ComposerTextMark] describes. Undo and
     * redo hide them, unless they return to the text that the marks last fit. Marks are not saved with the state.
     */
    val marks: List<ComposerTextMark<*>>
        get() = marked.takeIf { textFieldState.text.contentEquals(it.text) }?.list.orEmpty()

    /**
     * Replaces the [marks] with those the app made for [text], leaving out any that are empty or reach outside
     * of it. Returns false and keeps the current marks if the field no longer holds [text], as the ranges
     * would not fit.
     */
    fun setMarks(text: CharSequence, marks: List<ComposerTextMark<*>>): Boolean {
        if (!textFieldState.text.contentEquals(text)) return false
        val valid = marks.filter { it.start in 0 until it.end && it.end <= text.length }
        marked = Marks(text.toString(), valid)
        return true
    }

    internal val inputTransformation: InputTransformation = InputTransformation { update(stepped = navigatingByKey) }

    /** Whether a navigation key is held, so that it and not a pointer moves the selection. */
    internal var navigatingByKey = false

    /**
     * A selection end that [block] moves into an entity from one of its edges crosses the entity, so
     * [block] should not follow a drag.
     */
    fun edit(block: TextFieldBuffer.() -> Unit) {
        textFieldState.edit {
            block()
            update(stepped = true)
        }
    }

    /**
     * The content for [text], the current text of [textFieldState]. After undo or redo, a text that [history]
     * knows gets its entities back, and any other text keeps the entities that the change left intact.
     */
    internal fun contentFor(text: CharSequence): MatrixMarkdownContent {
        // Read even if realigned is used instead, so that observers of the content are notified of the next edit
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

    private fun TextFieldBuffer.update(stepped: Boolean) {
        val previous = contentFor(originalText)
        align(previous, reconcile(previous.entities, stepped), asCharSequence())
    }

    private fun TextFieldBuffer.align(
        previous: MatrixMarkdownContent,
        entities: List<MatrixInlineEntity>,
        text: CharSequence,
    ) {
        val content = MatrixMarkdownContent(text.toString(), entities)
        aligned = content
        realigned = null
        history.record(previous, content)
        alignMarks(content.text)
    }

    private fun TextFieldBuffer.alignMarks(text: String) {
        val current = marked
        if (current.list.isEmpty()) return
        val kept = if (originalText.contentEquals(current.text)) reconcileMarks(current.list) else emptyList()
        marked = Marks(text, kept)
    }

    /** Marks with the [text] they fit. */
    private class Marks(val text: String, val list: List<ComposerTextMark<*>>)

    companion object {
        /** Saves the text, its entities and the selection, but neither marks nor undo history. */
        val Saver: Saver<MatrixMarkdownTextFieldState, Any> = listSaver(
            save = { state ->
                val content = state.content
                val entities = content.entities.map { it.toSaveable() }
                listOf(content.text, entities, state.selection.start, state.selection.end)
            },
            restore = { saved ->
                val text = saved[0] as String
                val entities = (saved[1] as List<*>).mapNotNull { (it as List<*>).toInlineEntity() }
                val selection = TextRange(
                    (saved[2] as Int).coerceIn(0, text.length),
                    (saved[3] as Int).coerceIn(0, text.length),
                )
                MatrixMarkdownTextFieldState(text, entities, selection)
            },
        )
    }
}

private fun MatrixInlineEntity.toSaveable(): List<Any?> = when (this) {
    is MatrixInlineEntity.UserMention -> listOf(USER_MENTION, start, end, userId, contentText)
    is MatrixInlineEntity.RoomMention -> listOf(ROOM_MENTION, start, end)
    is MatrixInlineEntity.CustomEmote -> listOf(CUSTOM_EMOTE, start, end, shortcode, mxcUrl, packId, body, width, height)
}

private fun List<*>.toInlineEntity(): MatrixInlineEntity? {
    val start = this[1] as Int
    val end = this[2] as Int
    return when (this[0]) {
        USER_MENTION -> MatrixInlineEntity.UserMention(start, end, this[3] as String, this[4] as String?)
        ROOM_MENTION -> MatrixInlineEntity.RoomMention(start, end)
        CUSTOM_EMOTE -> MatrixInlineEntity.CustomEmote(
            start,
            end,
            shortcode = this[3] as String,
            mxcUrl = this[4] as String,
            packId = this[5] as String?,
            body = this[6] as String?,
            width = getOrNull(7) as Int?,
            height = getOrNull(8) as Int?,
        )
        else -> null
    }
}

private const val USER_MENTION = "user"
private const val ROOM_MENTION = "room"
private const val CUSTOM_EMOTE = "emote"

/** Remembers a [MatrixMarkdownTextFieldState] that is saved and restored with the composition. */
@Composable
fun rememberMatrixMarkdownTextFieldState(
    initialText: String = "",
    initialEntities: List<MatrixInlineEntity> = emptyList(),
): MatrixMarkdownTextFieldState = rememberSaveable(saver = MatrixMarkdownTextFieldState.Saver) {
    MatrixMarkdownTextFieldState(initialText, initialEntities)
}

private fun MatrixMarkdownContent.entitiesAfterChangeTo(text: String): List<MatrixInlineEntity> =
    EntityReconciler.reconcileUnobserved(entities, this.text, text)

/**
 * The contents before and after each recent edit that changed entities in a way the two texts do not show,
 * such as removing one. A text that undo or redo returns to gets its entities back from here.
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
            // A recorded text that an edit arrives at again may have other entities by now
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
