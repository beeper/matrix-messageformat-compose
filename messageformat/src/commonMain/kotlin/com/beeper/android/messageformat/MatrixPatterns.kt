package com.beeper.android.messageformat

import io.ktor.http.Url
import io.ktor.http.decodeURLPart
import java.util.regex.Pattern

object MatrixPatterns {
    const val MATRIX_TO = "https://matrix.to"
    const val MATRIX_TO_LINK_PREFIX = "$MATRIX_TO/#/"
    private const val MATRIX_URI_SCHEME = "matrix:"

    val MATRIX_URI_PATTERN: Pattern = Pattern.compile("""(?i)(?:(?<=\s)|(?<=[(\[{<])|^)matrix:\S+""")

    // This does not enforce strict rules as per spec but is a bit more permissive just in case.
    val USER_ID_REGEX = Regex("""@.*:.+""")
    val ROOM_ID_REGEX = Regex("""!.+""")
    val ROOM_ALIAS_REGEX = Regex("""#.*:.+""")
    val MESSAGE_ID_REGEX = Regex("""\$.+""")
    // This one is more strict, as it used for auto-linkification.
    val ROOM_ALIAS_LINKIFY_REGEX = Regex("""(?:(?<=\s)|(?<=[(\[{<])|^)(#[A-Za-z0-9._=\-/]+:[A-Za-z0-9.-]+(?::\d+)?)(?=$|\s|[)\]}>.,;!?])""")

    private fun String.isRoomIdOrAlias() =
        ROOM_ID_REGEX.matches(this) || ROOM_ALIAS_REGEX.matches(this)

    fun parseMatrixLink(url: String, isAutoLink: Boolean): MatrixToLink? {
        return parseMatrixToUrl(url, isAutoLink) ?: parseMatrixUri(url, isAutoLink)
    }

    fun parseMatrixToUrl(url: String, isAutoLink: Boolean): MatrixToLink? {
        if (!url.startsWith(MATRIX_TO_LINK_PREFIX)) {
            return null
        }
        val parsed = try {
            // Need to escape the '#' found in plaintext in many matrix.to urls
            // sent within matrix clients, to not confuse ktor
            Url(url.replace("#", "%23"))
        } catch (_: Exception) {
            return null
        }
        if (parsed.pathSegments.size <= 2) {
            return null
        }
        // First segment is just blank, second is just the '#'
        val segments = parsed.pathSegments.subList(2, parsed.pathSegments.size)
        when (segments.size) {
            1 -> {
                val segment = segments.first()
                if (USER_ID_REGEX.matches(segment)) {
                    return MatrixToLink.UserMention(segment, url, isAutoLink)
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

    fun parseMatrixUri(url: String, isAutoLink: Boolean): MatrixToLink? {
        if (!url.startsWith(MATRIX_URI_SCHEME, ignoreCase = true)) {
            return null
        }
        return try {
            parseMatrixUriUnsafe(url, isAutoLink)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMatrixUriUnsafe(url: String, isAutoLink: Boolean): MatrixToLink? {
        val schemeSpecific = url.substringAfter(':')
        val withoutFragment = schemeSpecific.substringBefore('#')
        val pathAndQuery = withoutFragment.substringAfterAuthority()
        val path = pathAndQuery.substringBefore('?')
        val query = pathAndQuery.substringAfter('?', missingDelimiterValue = "")
        val segments = path.split('/')
        if (segments.size != 2 && segments.size != 4) {
            return null
        }

        val roomId = parseTopLevelIdentifier(segments[0], segments[1]) ?: return null
        val via = query.parameters("via")
        val action = query.parameters("action")?.lastOrNull()

        return when {
            segments.size == 2 -> when (roomId.first()) {
                '@' -> MatrixToLink.UserMention(roomId, url, isAutoLink, action)
                '#', '!' -> MatrixToLink.RoomLink(roomId, via, url, action)
                else -> null
            }
            roomId.isRoomIdOrAlias() && segments[2].equals("e", ignoreCase = true) ||
                roomId.isRoomIdOrAlias() && segments[2].equals("event", ignoreCase = true) -> {
                val eventId = segments[3].takeIf { it.isNotEmpty() }?.decodeURLPart()?.let { "\$$it" } ?: return null
                if (MESSAGE_ID_REGEX.matches(eventId)) {
                    MatrixToLink.MessageLink(roomId, eventId, via, url, action)
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun parseTopLevelIdentifier(type: String, idWithoutSigil: String): String? {
        if (idWithoutSigil.isEmpty()) {
            return null
        }
        val sigil = when {
            type.equals("u", ignoreCase = true) || type.equals("user", ignoreCase = true) -> '@'
            type.equals("r", ignoreCase = true) || type.equals("room", ignoreCase = true) -> '#'
            type.equals("roomid", ignoreCase = true) -> '!'
            else -> return null
        }
        return "$sigil${idWithoutSigil.decodeURLPart()}"
    }

    private fun String.substringAfterAuthority(): String {
        if (!startsWith("//")) {
            return this
        }
        return drop(2).substringAfter('/', missingDelimiterValue = "")
    }

    private fun String.parameters(name: String): List<String>? {
        val values = split('&').mapNotNull { item ->
            if (item.isEmpty()) {
                return@mapNotNull null
            }
            val key = item.substringBefore('=')
            if (!key.equals(name, ignoreCase = true)) {
                return@mapNotNull null
            }
            item.substringAfter('=', missingDelimiterValue = "").decodeURLPart()
        }
        return values.ifEmpty { null }
    }

    fun isValidMatrixUri(url: String) = url.startsWith("mxc://")
}
