package com.beeper.android.messageformat.composer

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.TextUnit
import com.beeper.android.messageformat.ListItemAttributes
import com.beeper.android.messageformat.MatrixBodyStyledFormatter
import com.beeper.android.messageformat.MatrixToLink
import com.beeper.android.messageformat.SpanAttributes

/** Styles nothing, so a test can override just the callbacks it cares about. */
internal open class NoStyleFormatter : MatrixBodyStyledFormatter() {
    override fun formatHeading(tag: String, context: FormatContext): Annotations = null
    override fun formatSpan(attributes: SpanAttributes, context: FormatContext): Annotations = null
    override fun formatInlineCode(context: FormatContext): Annotations = null
    override fun formatCodeBlock(context: FormatContext): Annotations = null
    override fun formatBlockQuote(depth: Int, context: FormatContext): Annotations = null
    override fun formatRoomMention(context: FormatContext): Annotations = null
    override fun formatUserMention(mention: MatrixToLink.UserMention, context: FormatContext): Annotations = null
    override fun formatRoomLink(roomLink: MatrixToLink.RoomLink, context: FormatContext): Annotations = null
    override fun formatMessageLink(messageLink: MatrixToLink.MessageLink, context: FormatContext): Annotations = null
    override fun formatWebLink(href: String, context: FormatContext): Annotations = null
    override fun formatUnorderedListItem(attributes: ListItemAttributes, context: FormatContext): Annotations = null
    override fun formatOrderedListItem(attributes: ListItemAttributes, context: FormatContext): Annotations = null
    override fun formatTable(depth: Int, context: FormatContext): Annotations = null
    override fun tableIndention(depth: Int): TextUnit = TextUnit.Unspecified
    override fun formatDetailsSummary(revealId: Int, context: FormatContext): Annotations = null
    override fun formatDetailsContent(revealId: Int, context: FormatContext): Annotations = null
}

private typealias Annotations = List<AnnotatedString.Annotation>?
