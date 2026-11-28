package com.beeper.android.messageformat.markdown

import com.beeper.android.messageformat.InlineImageInfo
import com.beeper.android.messageformat.MENTION_ROOM
import com.beeper.android.messageformat.MatrixPatterns
import com.beeper.android.messageformat.MatrixToLink

/**
 * A span of composer text with special meaning.
 */
sealed interface MatrixInlineEntity {
    val start: Int
    val end: Int

    fun moved(start: Int, end: Int): MatrixInlineEntity

    /** The text a sent message carries for this entity, where the composer shows [visibleText]. */
    fun sentText(visibleText: String): String = visibleText

    data class UserMention(
        override val start: Int,
        override val end: Int,
        val userId: String,
        /** Text to send in place of the visible text, e.g. so a local contact rename does not leak into the message. */
        val contentText: String? = null,
    ) : MatrixInlineEntity {
        override fun moved(start: Int, end: Int) = copy(start = start, end = end)

        override fun sentText(visibleText: String): String = contentText ?: visibleText

        fun toMatrixToLink(): MatrixToLink.UserMention =
            MatrixToLink.UserMention(userId, MatrixPatterns.MATRIX_TO_LINK_PREFIX + userId, isAutoLink = false)
    }

    /** Always sent as `@room`, the text the Matrix spec uses for room mentions, whatever the composer shows. */
    data class RoomMention(override val start: Int, override val end: Int) : MatrixInlineEntity {
        override fun moved(start: Int, end: Int) = copy(start = start, end = end)

        override fun sentText(visibleText: String): String = MENTION_ROOM
    }

    /**
     * An inline image, such as a custom emote. Its [image] is what the sent HTML describes. [pack] and [packInfo]
     * are set for emotes picked from an image pack.
     */
    data class InlineImage(
        override val start: Int,
        override val end: Int,
        val image: InlineImageInfo,
        val pack: ImagePackSource? = null,
        /** The image's raw `info` JSON object from its image pack. */
        val packInfo: String? = null,
    ) : MatrixInlineEntity {
        override fun moved(start: Int, end: Int) = copy(start = start, end = end)

        companion object {
            /** A custom emote, with the [body] and image size from its image pack. */
            fun customEmote(
                start: Int,
                end: Int,
                shortcode: String,
                mxcUrl: String,
                pack: ImagePackSource? = null,
                body: String? = null,
                width: Int? = null,
                height: Int? = null,
                packInfo: String? = null,
            ) = InlineImage(
                start,
                end,
                InlineImageInfo(uri = mxcUrl, isEmote = true, width = width, height = height, title = shortcode, alt = body),
                pack,
                packInfo,
            )
        }
    }
}

/** The room image pack a custom emote was picked from, by the pack's state event. */
data class ImagePackSource(
    val roomId: String,
    val stateKey: String,
    /** The pack's `usage` values. */
    val usage: List<String>? = null,
)

/** The entities that lie inside [text] without overlapping each other, in text order. */
fun List<MatrixInlineEntity>.validIn(text: CharSequence): List<MatrixInlineEntity> {
    var lastEnd = 0
    return sortedBy { it.start }.filter { entity ->
        val valid = entity.start >= lastEnd && entity.end <= text.length && entity.start < entity.end
        if (valid) lastEnd = entity.end
        valid
    }
}

