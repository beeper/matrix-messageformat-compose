package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.TextRange
import kotlin.math.min

/** A replacement of the [original] range of a text, which makes up the [updated] range of the edited text. */
internal class TextChange(val original: TextRange, val updated: TextRange)

/** This change minus its unchanged leading and trailing characters, or null if nothing changed. */
internal fun TextChange.narrowed(before: CharSequence, after: CharSequence, prefixFirst: Boolean): TextChange? {
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
    val narrowed = TextChange(
        TextRange(original.start + prefix, original.end - suffix),
        TextRange(updated.start + prefix, updated.end - suffix),
    )
    return narrowed.takeUnless { it.original.collapsed && it.updated.collapsed }
}
