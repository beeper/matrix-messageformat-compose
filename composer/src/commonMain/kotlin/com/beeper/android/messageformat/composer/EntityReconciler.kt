package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.text.TextRange
import com.beeper.android.messageformat.markdown.MatrixInlineEntity

/**
 * Updates [MatrixInlineEntity] ranges after an edit of a [TextFieldBuffer]: entities move with the
 * text, a deletion into an entity removes it whole, and text typed inside one turns it into plain text.
 * Entities whose characters an edit leaves intact survive it, e.g. when a keyboard rewrites the whole text.
 */
internal object EntityReconciler {

    /** [stepped] tells that a selection change is a single step, like that of a key, and not one of a drag. */
    fun TextFieldBuffer.reconcile(
        entities: List<MatrixInlineEntity>,
        stepped: Boolean = false,
    ): List<MatrixInlineEntity> {
        if (entities.isEmpty()) return entities
        if (changes.changeCount == 0) {
            moveSelectionOutOf(entities, stepped)
            return entities
        }
        val recorded = (0 until changes.changeCount).map { TextChange(changes.getOriginalRange(it), changes.getRange(it)) }
        val edit = Edit(recorded, originalText, asCharSequence())

        val kept = mutableListOf<MatrixInlineEntity>()
        val remainders = mutableListOf<TextRange>()
        for ((entity, outcome) in entities.zip(outcomesFor(entities, edit))) {
            when (outcome) {
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
        if (entities.isEmpty()) return entities
        val edit = Edit(listOf(TextChange(TextRange(0, before.length), TextRange(0, after.length))), before, after)
        return entities.zip(outcomesFor(entities, edit)).mapNotNull { (entity, outcome) ->
            (outcome as? Outcome.Moved)?.let { entity.moved(it.start, it.end) }
        }
    }

    /**
     * An edit as [recorded], and narrowed to the characters it really changes. Narrowing can be ambiguous,
     * like which "A" was removed from "AA", so both readings are kept.
     */
    private class Edit(val recorded: List<TextChange>, before: CharSequence, after: CharSequence) {
        val narrowings: List<List<TextChange>> = listOf(true, false).map { prefixFirst ->
            recorded.mapNotNull { it.narrowed(before, after, prefixFirst) }
        }
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

    /**
     * The narrowing that spares the most entities decides which survive. Using one reading for all entities
     * keeps two equal neighbours from both claiming the same text. Only the recorded range decides what a
     * cut deletes.
     */
    private fun outcomesFor(entities: List<MatrixInlineEntity>, edit: Edit): List<Outcome> {
        val spared = edit.narrowings
            .map { narrowing -> entities.map { outcomeFor(it, narrowing) as? Outcome.Moved } }
            .maxBy { moved -> moved.count { it != null } }
        return entities.mapIndexed { index, entity -> spared[index] ?: outcomeFor(entity, edit.recorded) }
    }

    private fun outcomeFor(entity: MatrixInlineEntity, changes: List<TextChange>): Outcome {
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
    private fun mapOffset(offset: Int, changes: List<TextChange>, towardsEnd: Boolean): Int {
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

    private fun TextFieldBuffer.moveSelectionOutOf(entities: List<MatrixInlineEntity>, stepped: Boolean) {
        val from = originalSelection
        val moved = selection
        fun entityAround(offset: Int) = entities.firstOrNull { it.start < offset && offset < it.end }

        if (moved.collapsed) {
            val cursor = moved.start
            val inside = entityAround(cursor) ?: return
            val target = when {
                // A cursor stepping in from an edge crosses the entity, as snapping back would trap arrow keys
                from.collapsed && from.start == inside.end -> inside.start
                from.collapsed && from.start == inside.start -> inside.end
                cursor - inside.start < inside.end - cursor -> inside.start
                else -> inside.end
            }
            selection = TextRange(target)
            return
        }

        fun outOf(offset: Int, previous: Int, steppedAlone: Boolean, isLowerEnd: Boolean): Int {
            val inside = entityAround(offset) ?: return offset
            return when {
                // Crossing lets a step shrink the selection past an entity. A dragged end stays inside the
                // entity over many changes, and would flip between the edges if it crossed.
                steppedAlone && previous == inside.end -> inside.start
                steppedAlone && previous == inside.start -> inside.end
                isLowerEnd -> inside.start
                else -> inside.end
            }
        }
        val snapped = TextRange(
            outOf(moved.start, from.start, stepped && moved.end == from.end, isLowerEnd = !moved.reversed),
            outOf(moved.end, from.end, stepped && moved.start == from.start, isLowerEnd = moved.reversed),
        )
        if (snapped != moved) selection = snapped
    }
}
