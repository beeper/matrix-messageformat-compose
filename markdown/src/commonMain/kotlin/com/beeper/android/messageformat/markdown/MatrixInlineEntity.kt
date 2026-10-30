package com.beeper.android.messageformat.markdown

import com.beeper.android.messageformat.MatrixPatterns
import com.beeper.android.messageformat.MatrixToLink

/**
 * A span of composer text with special meaning.
 */
sealed interface MatrixInlineEntity {
    val start: Int
    val end: Int

    fun moved(start: Int, end: Int): MatrixInlineEntity

    data class UserMention(
        override val start: Int,
        override val end: Int,
        val userId: String,
        /** Link text for the formatted body when it should differ from the visible text. */
        val contentText: String? = null,
    ) : MatrixInlineEntity {
        override fun moved(start: Int, end: Int) = copy(start = start, end = end)

        fun toMatrixToLink(): MatrixToLink.UserMention =
            MatrixToLink.UserMention(userId, MatrixPatterns.MATRIX_TO_LINK_PREFIX + userId, isAutoLink = false)
    }

    data class RoomMention(override val start: Int, override val end: Int) : MatrixInlineEntity {
        override fun moved(start: Int, end: Int) = copy(start = start, end = end)
    }

    data class CustomEmote(
        override val start: Int,
        override val end: Int,
        val shortcode: String,
        val mxcUrl: String,
        val packId: String? = null,
    ) : MatrixInlineEntity {
        override fun moved(start: Int, end: Int) = copy(start = start, end = end)
    }
}

/** The entities that lie inside [text] without overlapping each other, in text order. */
fun List<MatrixInlineEntity>.validIn(text: CharSequence): List<MatrixInlineEntity> {
    var lastEnd = 0
    return sortedBy { it.start }.filter { entity ->
        val valid = entity.start >= lastEnd && entity.end <= text.length && entity.start < entity.end
        if (valid) lastEnd = entity.end
        valid
    }
}

