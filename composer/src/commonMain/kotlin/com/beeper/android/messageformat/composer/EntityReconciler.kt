package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.text.TextRange
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import kotlin.math.min

/**
 * Updates [MatrixInlineEntity] ranges after an edit of a [TextFieldBuffer]: entities move with the
 * text, a deletion into an entity removes it whole, and text typed inside one turns it into plain text.
 * Only the characters an edit actually changes count, so rewriting text to itself leaves entities alone.
 */
internal object EntityReconciler {

    fun TextFieldBuffer.reconcile(entities: List<MatrixInlineEntity>): List<MatrixInlineEntity> {
        if (entities.isEmpty()) return entities
        val changes = changes()
        if (changes.isEmpty()) {
            moveCursorOutOf(entities)
            return entities
        }

        val kept = mutableListOf<MatrixInlineEntity>()
        val remainders = mutableListOf<TextRange>()
        for (entity in entities) {
            when (val outcome = outcomeFor(entity, changes)) {
                is Outcome.Moved -> kept += entity.moved(outcome.start, outcome.end)
                // Nothing is left to delete when the change removed the whole entity
                is Outcome.Deleted -> if (!outcome.remainder.collapsed && !outcome.remainder.reversed) {
                    remainders += outcome.remainder
                }
                Outcome.PlainText -> Unit
            }
        }
        if (remainders.isEmpty()) return kept
        // Deleting from the back keeps earlier offsets valid
        var cursor = selection.start
        for (remainder in remainders.sortedByDescending { it.start }) {
            delete(remainder.start, remainder.end)
            cursor = cursor.shiftedPast(remainder)
            for (index in kept.indices) {
                val entity = kept[index]
                if (entity.start >= remainder.end) {
                    kept[index] = entity.moved(entity.start - remainder.length, entity.end - remainder.length)
                }
            }
        }
        selection = TextRange(cursor)
        return kept
    }

    /**
     * Updates [entities] for [before] having become [after] through edits that were not observed, such as undo.
     * Entities the edit cut into are dropped, as the text must stay exactly as it is.
     */
    fun reconcileUnobserved(
        entities: List<MatrixInlineEntity>,
        before: CharSequence,
        after: CharSequence,
    ): List<MatrixInlineEntity> {
        val changes = listOfNotNull(
            Change(TextRange(0, before.length), TextRange(0, after.length)).narrowed(before, after),
        )
        return entities.mapNotNull { entity ->
            (outcomeFor(entity, changes) as? Outcome.Moved)?.let { entity.moved(it.start, it.end) }
        }
    }

    private class Change(val original: TextRange, val updated: TextRange)

    private fun TextFieldBuffer.changes(): List<Change> {
        val text = asCharSequence()
        return (0 until changes.changeCount).mapNotNull { index ->
            Change(changes.getOriginalRange(index), changes.getRange(index)).narrowed(originalText, text)
        }
    }

    /** This change without the characters it leaves as they were, or null if it changes nothing. */
    private fun Change.narrowed(before: CharSequence, after: CharSequence): Change? {
        val maxCommon = min(original.length, updated.length)
        var prefix = 0
        while (prefix < maxCommon && before[original.start + prefix] == after[updated.start + prefix]) prefix++
        var suffix = 0
        while (suffix < maxCommon - prefix && before[original.end - 1 - suffix] == after[updated.end - 1 - suffix]) {
            suffix++
        }
        val narrowed = Change(
            TextRange(original.start + prefix, original.end - suffix),
            TextRange(updated.start + prefix, updated.end - suffix),
        )
        return narrowed.takeUnless { it.original.collapsed && it.updated.collapsed }
    }

    private fun Int.shiftedPast(deleted: TextRange): Int = when {
        this >= deleted.end -> this - deleted.length
        this > deleted.start -> deleted.start
        else -> this
    }

    private sealed interface Outcome {
        data class Moved(val start: Int, val end: Int) : Outcome
        data class Deleted(val remainder: TextRange) : Outcome
        data object PlainText : Outcome
    }

    private fun outcomeFor(entity: MatrixInlineEntity, changes: List<Change>): Outcome {
        var shift = 0
        var typedInside = false
        var cut = false
        for (change in changes) {
            val original = change.original
            when {
                original.end <= entity.start -> shift += change.updated.length - original.length
                original.start >= entity.end -> Unit
                original.collapsed -> typedInside = true
                else -> cut = true
            }
        }
        return when {
            cut -> Outcome.Deleted(
                TextRange(
                    mapOffset(entity.start, changes, towardsEnd = true),
                    mapOffset(entity.end, changes, towardsEnd = false),
                ),
            )
            typedInside -> Outcome.PlainText
            else -> Outcome.Moved(entity.start + shift, entity.end + shift)
        }
    }

    /** Maps an original offset to the edited text; an offset inside a change maps to an edge of the replacement. */
    private fun mapOffset(offset: Int, changes: List<Change>, towardsEnd: Boolean): Int {
        var shift = 0
        for (change in changes) {
            val original = change.original
            val updated = change.updated
            val swallowed = when {
                towardsEnd -> offset in original.start until original.end
                else -> offset in original.start + 1..original.end
            }
            when {
                swallowed -> return if (towardsEnd) updated.end else updated.start
                original.end <= offset && !(original.collapsed && original.start == offset) ->
                    shift += updated.length - original.length
            }
        }
        return offset + shift
    }

    private fun TextFieldBuffer.moveCursorOutOf(entities: List<MatrixInlineEntity>) {
        if (!selection.collapsed) return
        val cursor = selection.start
        val inside = entities.firstOrNull { it.start < cursor && cursor < it.end } ?: return
        selection = TextRange(if (cursor - inside.start < inside.end - cursor) inside.start else inside.end)
    }
}
