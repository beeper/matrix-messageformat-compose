package com.beeper.android.messageformat

import io.ktor.http.Url

object MatrixPatterns {
    const val MATRIX_TO = "https://matrix.to"
    const val MATRIX_TO_LINK_PREFIX = "$MATRIX_TO/#/"

    // This does not enforce strict rules as per spec but is a bit more permissive just in case.
    val USER_ID_REGEX = Regex("""@.*:.+""")
    val ROOM_ID_REGEX = Regex("""!.+""")
    val ROOM_ALIAS_REGEX = Regex("""#.*:.+""")
    val MESSAGE_ID_REGEX = Regex("""\$.+""")

    private fun String.isRoomIdOrAlias() =
        ROOM_ID_REGEX.matches(this) || ROOM_ALIAS_REGEX.matches(this)

    fun parseMatrixToUrl(url: String): MatrixToLink? {
        if (!url.startsWith(MATRIX_TO_LINK_PREFIX)) {
            return null
        }
        val parsed = try {
            // ktor ignores things starting with '#' so strip the prefix and treat it as server-relative url
            Url("/" + url.removePrefix(MATRIX_TO_LINK_PREFIX))
        } catch (_: Exception) {
            return null
        }
        if (parsed.pathSegments.isEmpty()) {
            return null
        }
        // First segment is just blank
        val segments = parsed.pathSegments.subList(1, parsed.pathSegments.size)
        when (segments.size) {
            1 -> {
                val segment = segments.first()
                if (USER_ID_REGEX.matches(segment)) {
                    return MatrixToLink.UserMention(segment, url)
                }
                if (segment.isRoomIdOrAlias()) {
                    val via = parsed.parameters.getAll("via")
                    return MatrixToLink.RoomLink(segment, via, url)
                }
            }
            2 -> {
                if (segments[0].isRoomIdOrAlias() && MESSAGE_ID_REGEX.matches(segments[1])) {
                    val via = parsed.parameters.getAll("via")
                    return MatrixToLink.MessageLink(segments[0], segments[1], via, url)
                }
            }
        }
        return null
    }

    fun isValidMatrixUri(url: String) = url.startsWith("mxc://") || url.startsWith("localmxc://")
}
