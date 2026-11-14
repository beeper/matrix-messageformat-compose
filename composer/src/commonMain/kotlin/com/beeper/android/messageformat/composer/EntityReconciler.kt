package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.text.TextRange
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import kotlin.math.min

/**
 * Updates [MatrixInlineEntity] ranges after an edit of a [TextFieldBuffer]: entities move with the
 * text, a deletion into an entity removes it whole, and text typed inside one turns it into plain text.
 * Entities whose characters an edit leaves intact survive it, e.g. when a keyboard rewrites the whole text.
 */
internal object EntityReconciler {

    fun TextFieldBuffer.reconcile(entities: List<MatrixInlineEntity>): List<MatrixInlineEntity> {
        if (entities.isEmpty()) return entities
        if (changes.changeCount == 0) {
            moveCursorOutOf(entities)
            return entities
        }
        val recorded = (0 until changes.changeCount).map { Change(changes.getOriginalRange(it), changes.getRange(it)) }
        val edit = Edit(recorded, originalText, asCharSequence())

        val kept = mutableListOf<MatrixInlineEntity>()
        val remainders = mutableListOf<TextRange>()
        for (entity in entities) {
            when (val outcome = outcomeFor(entity, edit)) {
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
     * [reconcile] for an edit that was not observed, such as undo. Touched entities are dropped, but their
     * text stays.
     */
    fun reconcileUnobserved(
        entities: List<MatrixInlineEntity>,
        before: CharSequence,
        after: CharSequence,
    ): List<MatrixInlineEntity> {
        val edit = Edit(listOf(Change(TextRange(0, before.length), TextRange(0, after.length))), before, after)
        return entities.mapNotNull { entity ->
            (outcomeFor(entity, edit) as? Outcome.Moved)?.let { entity.moved(it.start, it.end) }
        }
    }

    private class Change(val original: TextRange, val updated: TextRange)

    /**
     * An edit as [recorded], and narrowed to the characters it really changes. Narrowing can be ambiguous,
     * like which "A" was removed from "AA", so both readings are kept.
     */
    private class Edit(val recorded: List<Change>, before: CharSequence, after: CharSequence) {
        val narrowings: List<List<Change>> = listOf(true, false).map { prefixFirst ->
            recorded.mapNotNull { it.narrowed(before, after, prefixFirst) }
        }
    }

    /** This change minus its unchanged leading and trailing characters, or null if nothing changed. */
    private fun Change.narrowed(before: CharSequence, after: CharSequence, prefixFirst: Boolean): Change? {
        val maxCommon = min(original.length, updated.length)
        fun commonPrefix(limit: Int): Int {
            var length = 0
            while (length < limit && before[original.start + length] == after[updated.start + length]) length++
            return length
        }
        fun commonSuffix(limit: Int): Int {
            var length = 0
            while (length < limit && before[original.end - 1 - length] == after[updated.end - 1 - length]) length++
            return length
        }
        val prefix = if (prefixFirst) commonPrefix(maxCommon) else commonPrefix(maxCommon - commonSuffix(maxCommon))
        val suffix = commonSuffix(maxCommon - prefix)
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

    // A narrowing may spare an entity, but only the recorded range decides what a cut deletes
    private fun outcomeFor(entity: MatrixInlineEntity, edit: Edit): Outcome =
        edit.narrowings.firstNotNullOfOrNull { outcomeFor(entity, it) as? Outcome.Moved }
            ?: outcomeFor(entity, edit.recorded)

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
