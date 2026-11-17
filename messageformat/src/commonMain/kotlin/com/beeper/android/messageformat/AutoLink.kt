package com.beeper.android.messageformat

import androidx.compose.ui.text.AnnotatedString
import com.beeper.android.messageformat.MatrixPatterns.MATRIX_TO_LINK_PREFIX
import kotlinx.serialization.json.Json

/** [text] must already be in the builder, starting at [offset]. */
fun AnnotatedString.Builder.addAutoLinkAnnotations(text: String, offset: Int, style: MatrixBodyPreFormatStyle) {
    // Avoid adding overlapping links
    val linkedRanges = mutableListOf<Pair<Int, Int>>()
    fun overlapsExistingLink(start: Int, end: Int): Boolean {
        return linkedRanges.any { (existingStart, existingEnd) ->
            start < existingEnd && end > existingStart
        }
    }

    // Like LinkifyCompat, the earlier match wins when a web URL and an email address overlap: an address keeps
    // its domain from becoming a web link, a URL keeps an address-like path (Mastodon /@user@server/id) intact.
    val webUrlRanges = style.autoLinkUrlPattern?.matcher(text)?.let { matcher ->
        buildList { while (matcher.find()) add(matcher.start() to matcher.end()) }
    }.orEmpty()

    // Auto-linkification of email addresses
    if (style.autoLinkEmailAddressPattern != null) {
        val matcher = style.autoLinkEmailAddressPattern.matcher(text)
        while (matcher.find()) {
            val startInText = matcher.start()
            val endInText = matcher.end()
            if (webUrlRanges.any { (urlStart, urlEnd) -> urlStart < startInText && urlEnd > startInText && !overlapsExistingLink(urlStart, urlEnd) }) {
                continue
            }
            val emailAddress = text.substring(startInText, endInText)
            addStringAnnotation(
                MatrixBodyAnnotations.MAIL_ADDRESS,
                emailAddress,
                offset + startInText,
                offset + endInText,
            )
            linkedRanges += startInText to endInText
        }
    }

    // Auto-linkification of matrix: URIs, which are not covered by generic web URL patterns.
    val matrixUriMatcher = MatrixPatterns.MATRIX_URI_PATTERN.matcher(text)
    while (matrixUriMatcher.find()) {
        val startInText = matrixUriMatcher.start()
        val matchedUrl = matrixUriMatcher.group()
        val url = matchedUrl.trimEnd('.', ',', ';', '!', '?', ')', ']', '}', '>')
        val endInText = startInText + url.length
        if (url.isEmpty() || overlapsExistingLink(startInText, endInText)) {
            continue
        }
        val matrixLink = MatrixPatterns.parseMatrixLink(url, isAutoLink = true) ?: continue
        val tag = when (matrixLink) {
            is MatrixToLink.UserMention -> MatrixBodyAnnotations.USER_MENTION
            is MatrixToLink.RoomLink -> MatrixBodyAnnotations.ROOM_LINK
            is MatrixToLink.MessageLink -> MatrixBodyAnnotations.MESSAGE_LINK
        }
        addStringAnnotation(
            tag,
            Json.encodeToString(matrixLink),
            offset + startInText,
            offset + endInText,
        )
        linkedRanges += startInText to endInText
    }

    val roomAliasMatches = MatrixPatterns.ROOM_ALIAS_LINKIFY_REGEX.findAll(text).toList()

    // Auto-linkification of hyperlinks
    for ((startInText, endInText) in webUrlRanges) {
        if (overlapsExistingLink(startInText, endInText)) {
            continue
        }
        // Android's web URL pattern matches bare domains, including the server part of room aliases.
        // Prefer the complete alias, but keep URLs that extend beyond it intact.
        if (roomAliasMatches.any { startInText >= it.range.first && endInText <= it.range.last + 1 }) {
            continue
        }
        val url = text.substring(startInText, endInText)
        // Handle matrix.to links specifically for user mentions and room / message links
        val matrixLink = MatrixPatterns.parseMatrixLink(url, isAutoLink = true)
        // Custom formatting of auto-linked url contents disabled for now -
        // let's call it a feature, maybe it wasn't supposed to be a link?
        val tag = when (matrixLink) {
            is MatrixToLink.UserMention -> MatrixBodyAnnotations.USER_MENTION
            is MatrixToLink.RoomLink -> MatrixBodyAnnotations.ROOM_LINK
            is MatrixToLink.MessageLink -> MatrixBodyAnnotations.MESSAGE_LINK
            null -> MatrixBodyAnnotations.WEB_LINK
        }
        addStringAnnotation(
            tag,
            matrixLink?.let { Json.encodeToString(it) } ?: url,
            offset + startInText,
            offset + endInText,
        )
        linkedRanges += startInText to endInText
    }

    // Auto-linkify room aliases in plaintext as well (except in code/pre blocks)
    roomAliasMatches.forEach { match ->
        val startInText = match.range.first
        val endInText = match.range.last + 1
        if (overlapsExistingLink(startInText, endInText)) {
            return@forEach
        }
        val roomLink: MatrixToLink =
            MatrixToLink.RoomLink(
                roomId = match.value,
                via = null,
                rawUrl = "$MATRIX_TO_LINK_PREFIX${match.value.replace("#", "%23")}",
            )
        addStringAnnotation(
            MatrixBodyAnnotations.ROOM_LINK,
            Json.encodeToString(roomLink),
            offset + startInText,
            offset + endInText,
        )
        linkedRanges += startInText to endInText
    }
}
