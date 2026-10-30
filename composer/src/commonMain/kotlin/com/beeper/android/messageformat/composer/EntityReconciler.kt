package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.ui.text.TextRange
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import kotlin.math.abs

/**
 * Updates [MatrixInlineEntity] ranges after an edit of a [TextFieldBuffer]: entities move with the
 * text, a deletion into an entity removes it whole, and text typed inside one turns it into plain text.
 */
internal object EntityReconciler {

    fun TextFieldBuffer.reconcile(entities: List<MatrixInlineEntity>): List<MatrixInlineEntity> {
        if (entities.isEmpty()) return entities
        if (changes.changeCount == 0) {
            moveCursorOutOf(entities)
            return entities
        }
        if (isWholeTextReplacement()) return findByText(entities)

        val kept = mutableListOf<MatrixInlineEntity>()
        val remainders = mutableListOf<TextRange>()
        for (entity in entities) {
            when (val outcome = outcomeFor(entity)) {
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

    private fun TextFieldBuffer.outcomeFor(entity: MatrixInlineEntity): Outcome {
        var shift = 0
        var typedInside = false
        var cut = false
        for (index in 0 until changes.changeCount) {
            val original = changes.getOriginalRange(index)
            val delta = changes.getRange(index).length - original.length
            when {
                original.end <= entity.start -> shift += delta
                original.start >= entity.end -> Unit
                original.collapsed -> typedInside = true
                else -> cut = true
            }
        }
        return when {
            cut -> Outcome.Deleted(
                TextRange(mapOffset(entity.start, towardsEnd = true), mapOffset(entity.end, towardsEnd = false)),
            )
            typedInside -> Outcome.PlainText
            else -> Outcome.Moved(entity.start + shift, entity.end + shift)
        }
    }

    /** Maps an original offset to the edited text; an offset inside a change maps to an edge of the replacement. */
    private fun TextFieldBuffer.mapOffset(offset: Int, towardsEnd: Boolean): Int {
        var shift = 0
        for (index in 0 until changes.changeCount) {
            val original = changes.getOriginalRange(index)
            val updated = changes.getRange(index)
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

    private fun TextFieldBuffer.isWholeTextReplacement(): Boolean {
        if (changes.changeCount != 1 || originalText.isEmpty() || length == 0) return false
        val original = changes.getOriginalRange(0)
        return original.start == 0 && original.end == originalText.length
    }

    // Some IMEs replace the whole text on autocorrect, so entities are searched for by their text
    private fun TextFieldBuffer.findByText(entities: List<MatrixInlineEntity>): List<MatrixInlineEntity> {
        val text = asCharSequence().toString()
        val relocated = mutableListOf<MatrixInlineEntity>()
        var searchFrom = 0
        for (entity in entities) {
            val needle = originalText.substring(entity.start, entity.end)
            val candidates = generateSequence(text.indexOf(needle, searchFrom)) { text.indexOf(needle, it + 1) }
                .takeWhile { it >= 0 }
                .toList()
            val start = candidates.minByOrNull { abs(it - entity.start) } ?: continue
            relocated += entity.moved(start, start + needle.length)
            searchFrom = start + needle.length
        }
        return relocated
    }

    private fun TextFieldBuffer.moveCursorOutOf(entities: List<MatrixInlineEntity>) {
        if (!selection.collapsed) return
        val cursor = selection.start
        val inside = entities.firstOrNull { it.start < cursor && cursor < it.end } ?: return
        selection = TextRange(if (cursor - inside.start < inside.end - cursor) inside.start else inside.end)
    }
}
