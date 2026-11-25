package com.beeper.android.messageformat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InlineImageInfoTest {

    private fun image(alt: String?, title: String?, isEmote: Boolean = true) =
        InlineImageInfo("mxc://x/cat", isEmote, width = null, height = null, title = title, alt = alt)

    @Test
    fun emoteShortcodeComesFromTheTitleAsTheSpecSendsIt() {
        assertEquals(":cat:", image(alt = "A cat", title = "cat").emoteFallbackText)
    }

    @Test
    fun emoteShortcodeComesFromAnAltWrappedInColons() {
        assertEquals(":cat:", image(alt = ":cat:", title = "A cat").emoteFallbackText)
    }

    @Test
    fun emoteShortcodeKeepsNoDoubleColons() {
        assertEquals(":cat:", image(alt = "A cat", title = ":cat:").emoteFallbackText)
    }

    @Test
    fun noEmoteShortcodeWithoutTitleOrShortcodeAlt() {
        assertNull(image(alt = "\uD83D\uDC31", title = null).emoteFallbackText)
    }

    @Test
    fun noEmoteShortcodeForOtherImages() {
        assertNull(image(alt = "A cat", title = "cat", isEmote = false).emoteFallbackText)
    }
}
