package com.beeper.android.messageformat.markdown

/** The formatted text of a message to send, with the mentions it contains, as [MatrixMarkdownParser.toDraft] produces it. */
data class MatrixFormattedMessageDraft(
    val body: String,
    /** The Matrix HTML of [body], or null when it would show nothing the plain body does not. */
    val formattedBody: String?,
    val mentionedUserIds: List<String>,
    val mentionsRoom: Boolean,
)
