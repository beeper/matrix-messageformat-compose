package com.beeper.android.messageformat

import kotlinx.serialization.Serializable

/**
 * String annotation tag names that are applied in the first processing pass,
 * in order to do further post processing later.
 */
internal object MatrixBodyAnnotations {
    const val HEADING = "mx:HEADING"
    const val USER_MENTION = "mx:USER_MENTION"
    const val ROOM_MENTION = "mx:ROOM_MENTION"
    const val ROOM_LINK = "mx:ROOM_LINK"
    const val MESSAGE_LINK = "mx:MESSAGE_LINK"
    const val WEB_LINK = "mx:WEB_LINK"
    const val BLOCK_QUOTE = "mx:BLOCK_QUOTE"
    const val UNORDERED_LIST = "mx:UNORDERED_LIST"
    const val UNORDERED_LIST_ITEM = "mx:UNORDERED_LIST_ITEM"
    const val ORDERED_LIST = "mx:ORDERED_LIST"
    const val ORDERED_LIST_ITEM = "mx:ORDERED_LIST_ITEM"
    const val INLINE_CODE = "mx:INLINE_CODE"
    const val BLOCK_CODE = "mx:BLOCK_CODE"
    const val SPAN = "mx:SPAN"
    const val DETAILS_SUMMARY = "mx:DETAILS_SUMMARY"
    const val DETAILS_CONTENT = "mx:DETAILS_CONTENT"
    const val INLINE_IMAGE_PREFIX = "mx:IMG:"
    const val CLICK_TO_REVEAL = "mx:CLICK_TO_REVEAL"
}

/**
 * Holder of supported attributes in Matrix span tags.
 */
@Serializable
data class SpanAttributes(
    val fgColor: Int?,
    val bgColor: Int?,
    val isSpoiler: Boolean,
    val revealId: Int,
)

/** Holders of parsed matrix.to links. */
@Serializable
sealed interface MatrixToLink {
    val rawUrl: String
    @Serializable
    data class UserMention(val userId: String, override val rawUrl: String) : MatrixToLink
    @Serializable
    data class RoomLink(val roomId: String, val via: List<String>?, override val rawUrl: String) : MatrixToLink
    @Serializable
    data class MessageLink(val roomId: String, val messageId: String, val via: List<String>?, override val rawUrl: String) : MatrixToLink
}
