package com.beeper.android.messageformat.markdown.html

import com.beeper.android.messageformat.markdown.EntityMasking
import com.beeper.android.messageformat.MatrixPatterns
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import org.intellij.markdown.html.DUMMY_ATTRIBUTES_CUSTOMIZER
import org.intellij.markdown.html.HtmlGenerator

/** A tag renderer that prints masked entities as their Matrix HTML. */
internal class MatrixInlineEntityTagRenderer(
    private val text: String,
    private val masking: EntityMasking,
) : HtmlGenerator.DefaultTagRenderer(DUMMY_ATTRIBUTES_CUSTOMIZER, includeSrcPositions = false) {

    override fun printHtml(html: CharSequence): CharSequence =
        masking.unmask(html) { entity -> entity.toHtml(text.substring(entity.start, entity.end)) }
}

internal fun MatrixInlineEntity.toHtml(visibleText: String): String = when (this) {
    is MatrixInlineEntity.UserMention -> {
        val href = MatrixPatterns.MATRIX_TO_LINK_PREFIX + escapeHtml(userId)
        "<a href=\"$href\">${escapeHtml(contentText ?: visibleText)}</a>"
    }
    is MatrixInlineEntity.RoomMention -> escapeHtml(visibleText)
    is MatrixInlineEntity.CustomEmote -> customEmoteHtml(mxcUrl, shortcode, alt = visibleText)
}

/** The `<img>` the Matrix spec defines for custom emotes. */
internal fun customEmoteHtml(mxcUrl: CharSequence, shortcode: CharSequence, alt: CharSequence): String =
    "<img src=\"${escapeHtml(mxcUrl)}\" alt=\"${escapeHtml(alt)}\" title=\"${escapeHtml(shortcode)}\"" +
        " data-mx-emoticon height=\"$CUSTOM_EMOTE_HEIGHT\">"

private const val CUSTOM_EMOTE_HEIGHT = 32
