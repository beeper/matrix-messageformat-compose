package com.beeper.android.messageformat.markdown.html

import com.beeper.android.messageformat.markdown.EntityMasking
import com.beeper.android.messageformat.MatrixPatterns
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import org.intellij.markdown.html.DUMMY_ATTRIBUTES_CUSTOMIZER
import org.intellij.markdown.html.HtmlGenerator
import kotlin.math.roundToInt

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
        "<a href=\"$href\">${escapeHtml(sentText(visibleText))}</a>"
    }
    is MatrixInlineEntity.RoomMention -> escapeHtml(sentText(visibleText))
    is MatrixInlineEntity.CustomEmote ->
        customEmoteHtml(escapeHtml(mxcUrl), escapeHtml(shortcode), body?.let(::escapeHtml), width, height)
}

/**
 * The `<img>` the Matrix spec defines for custom emotes, from already HTML-escaped values. The alt text is
 * [body], or [shortcode] when there is none.
 */
internal fun customEmoteHtml(
    mxcUrl: CharSequence,
    shortcode: CharSequence,
    body: CharSequence?,
    imageWidth: Int? = null,
    imageHeight: Int? = null,
): String {
    val alt = emoteAlt(shortcode, body)
    val width = if (imageWidth != null && imageHeight != null && imageWidth > 0 && imageHeight > 0) {
        " width=\"${(CUSTOM_EMOTE_HEIGHT * imageWidth.toFloat() / imageHeight).roundToInt().coerceAtLeast(1)}\""
    } else {
        ""
    }
    return "<img src=\"$mxcUrl\" alt=\"$alt\" title=\"$shortcode\" data-mx-emoticon$width height=\"$CUSTOM_EMOTE_HEIGHT\">"
}

/** A custom emote's `alt`, which the Matrix spec sets to its [body], or [shortcode] when there is none. */
internal fun emoteAlt(shortcode: CharSequence, body: CharSequence?): CharSequence = body?.takeIf { it.isNotBlank() } ?: shortcode

private const val CUSTOM_EMOTE_HEIGHT = 32
