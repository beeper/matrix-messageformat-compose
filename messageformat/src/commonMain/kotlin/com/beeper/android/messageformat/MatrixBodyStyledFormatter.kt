package com.beeper.android.messageformat

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.StringAnnotation
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Prepare an [androidx.compose.ui.text.AnnotatedString] output by [MatrixHtmlParser]
 * for final rendering, taking into account theme, style, screen density, and state (for spoilers and details tags).
 */
abstract class MatrixBodyStyledFormatter {
    private val log = Logger.withTag("MatrixBodyFormatter")

    abstract fun formatHeading(tag: String, context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatSpan(attributes: SpanAttributes, context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatInlineCode(context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatCodeBlock(context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatBlockQuote(depth: Int, context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatRoomMention(context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatUserMention(mention: MatrixToLink.UserMention, context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatRoomLink(roomLink: MatrixToLink.RoomLink, context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatMessageLink(messageLink: MatrixToLink.MessageLink, context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatWebLink(href: String, context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatUnorderedListItem(depth: Int, context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatOrderedListItem(depth: Int, context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatDetailsSummary(revealId: Int, context: FormatContext): List<AnnotatedString.Annotation>?
    abstract fun formatDetailsContent(revealId: Int, context: FormatContext): List<AnnotatedString.Annotation>?

    data class FormatContext(
        val input: MatrixBodyParseResult,
        val state: MatrixFormatInteractionState,
        val start: Int,
        val end: Int,
    )

    open fun mapAnnotation(
        range: AnnotatedString.Range<out AnnotatedString.Annotation>,
        context: FormatContext,
    ): List<AnnotatedString.Range<out AnnotatedString.Annotation>> {
        val annotation = range.item
        fun List<AnnotatedString.Annotation>.toRanges() = map {
            AnnotatedString.Range(
                item = it,
                start = range.start,
                end = range.end,
            )
        }
        return if (annotation is StringAnnotation) {
            when (range.tag) {
                MatrixBodyAnnotations.HEADING -> {
                    formatHeading(annotation.value, context)
                }
                MatrixBodyAnnotations.ROOM_MENTION -> formatRoomMention(context)
                MatrixBodyAnnotations.USER_MENTION -> {
                    try {
                        val link = Json.decodeFromString<MatrixToLink>(annotation.value) as MatrixToLink.UserMention
                        formatUserMention(link, context)
                    } catch (e: Exception) {
                        log.e("User mention data parsing error", e)
                        null
                    }
                }
                MatrixBodyAnnotations.ROOM_LINK -> {
                    try {
                        val link = Json.decodeFromString<MatrixToLink>(annotation.value) as MatrixToLink.RoomLink
                        formatRoomLink(link, context)
                    } catch (e: Exception) {
                        log.e("Room link data parsing error", e)
                        null
                    }
                }
                MatrixBodyAnnotations.MESSAGE_LINK -> {
                    try {
                        val link = Json.decodeFromString<MatrixToLink>(annotation.value) as MatrixToLink.MessageLink
                        formatMessageLink(link, context)
                    } catch (e: Exception) {
                        log.e("Message link data parsing error", e)
                        null
                    }
                }
                MatrixBodyAnnotations.WEB_LINK -> formatWebLink(annotation.value, context)
                MatrixBodyAnnotations.BLOCK_QUOTE -> {
                    val depth = annotation.value.toIntOrNull()
                    if (depth == null) {
                        log.e("Block quote data parsing error, is not an int depth: ${annotation.value}")
                        null
                    } else {
                        formatBlockQuote(depth, context)
                    }
                }
                MatrixBodyAnnotations.INLINE_CODE -> formatInlineCode(context)
                MatrixBodyAnnotations.BLOCK_CODE -> formatCodeBlock(context)
                MatrixBodyAnnotations.SPAN -> {
                    val attributes = try {
                        Json.decodeFromString<SpanAttributes>(annotation.value)
                    } catch (e: Exception) {
                        log.e("Span data parsing error", e)
                        return listOf(range)
                    }
                    formatSpan(attributes, context)
                }

                MatrixBodyAnnotations.UNORDERED_LIST_ITEM -> {
                    val depth = annotation.value.toIntOrNull()
                    if (depth == null) {
                        log.e("Unordered list item data parsing error, is not an int depth: ${annotation.value}")
                        null
                    } else {
                        formatUnorderedListItem(depth, context)
                    }
                }
                MatrixBodyAnnotations.ORDERED_LIST_ITEM -> {
                    val depth = annotation.value.toIntOrNull()
                    if (depth == null) {
                        log.e("Ordered list data parsing error, is not an int depth: ${annotation.value}")
                        null
                    } else {
                        formatOrderedListItem(depth, context)
                    }
                }

                MatrixBodyAnnotations.DETAILS_SUMMARY -> {
                    val revealId = annotation.value.toIntOrNull()
                    if (revealId == null) {
                        log.e("Details summary item data parsing error, is not an int ID: ${annotation.value}")
                        null
                    } else {
                        formatDetailsSummary(revealId, context)
                    }
                }
                MatrixBodyAnnotations.DETAILS_CONTENT -> {
                    val revealId = annotation.value.toIntOrNull()
                    if (revealId == null) {
                        log.e("Details content item data parsing error, is not an int ID: ${annotation.value}")
                        null
                    } else {
                        formatDetailsContent(revealId, context)
                    }
                }

                else -> {
                    // Not ours or not necessary to process
                    null
                }
            }?.toRanges() ?: emptyList()
        } else {
            // Not ours
            emptyList()
        }
    }

    fun applyStyle(
        input: MatrixBodyParseResult,
        state: MatrixFormatInteractionState,
    ): AnnotatedString {
        val ts = System.currentTimeMillis()
        return input.text.flatMapAnnotations {
            val context = FormatContext(
                input = input,
                state = state,
                start = it.start,
                end = it.end,
            )
            val mapped = mapAnnotation(it, context)
            // Ensure we do not remove the original annotation, so we can do further post processing on it (for drawing behind spans)
            if (it in mapped) {
                mapped
            } else {
                mapped + it
            }
        }.also {
            if (VERBOSE_DBG) {
                val now = System.currentTimeMillis()
                log.v("Processed in ${now - ts} ms; len=${input.text.length}->${it.length}")
            }
        }
    }
}

/**
 * Implementation of [MatrixBodyStyledFormatter] with some sensible defaults based on provided measure information.
 */
open class DefaultMatrixBodyStyledFormatter(
    private val density: Density,
    private val textMeasurer: TextMeasurer,
    private val textStyle: TextStyle,
    private val blockIndention: TextUnit = 16.sp,
    private val listBulletForDepth: (depth: Int) -> String = { DEFAULT_UNORDERED_BULLET_STRING },
    private val urlStyle: TextLinkStyles? = TextLinkStyles(style = SpanStyle(color = Color.Blue, textDecoration = TextDecoration.Underline)),
    private val clickToRevealStyle: TextLinkStyles? = TextLinkStyles(),
) : MatrixBodyStyledFormatter() {

    init {
        if (!blockIndention.isSp) {
            throw IllegalArgumentException("DefaultMatrixHtmlAnnotatedStringFormatter: Indention must be in SP")
        }
    }

    private val cachedBulletSizes = ConcurrentHashMap<String, TextUnit>()

    /**
     * Get the [ParagraphStyle] applicable for block quotes for a provided [depth].
     */
    open fun blockQuoteParagraphStyle(depth: Int): ParagraphStyle {
        val indent = blockIndention * depth
        return ParagraphStyle(
            textIndent = TextIndent(firstLine = indent, restLine = indent)
        )
    }

    /**
     * Get the [ParagraphStyle] applicable for list items for a provided [depth] and [bulletWidth].
     */
    open fun listItemParagraphStyle(
        depth: Int,
        bulletWidth: TextUnit,
    ): ParagraphStyle {
        val baseDepth = blockIndention * depth
        return ParagraphStyle(
            textIndent = TextIndent(
                firstLine = baseDepth,
                restLine = (baseDepth.value + bulletWidth.value).sp,
            )
        )
    }

    /**
     * Look up indention depth for [baseParagraphStyle].
     */
    open fun paragraphIndentionDepthFor(tag: String, value: String): Int {
        return when (tag) {
            MatrixBodyAnnotations.BLOCK_QUOTE,
            MatrixBodyAnnotations.ORDERED_LIST_ITEM,
            MatrixBodyAnnotations.UNORDERED_LIST_ITEM -> value.toIntOrNull() ?: 0
            else -> 0
        }
    }

    /**
     * Look up paragraph styles for given depth for [baseParagraphStyle].
     */
    open fun baseParagraphStyleFor(tag: String, depth: Int): ParagraphStyle {
        return when (tag) {
            MatrixBodyAnnotations.ORDERED_LIST_ITEM -> {
                listItemParagraphStyle(depth, blockIndention)
            }
            MatrixBodyAnnotations.UNORDERED_LIST_ITEM -> {
                listItemParagraphStyle(depth, cachedBulledWidth(depth))
            }
            else -> blockQuoteParagraphStyle(depth)
        }
    }

    open fun cachedBulledWidth(depth: Int): TextUnit {
        val bullet = listBulletForDepth(depth)
        return cachedBulletSizes.getOrPut(bullet) {
            density.run {
                textMeasurer.measure(bullet).size.width.toSp()
            }
        }
    }

    /**
     * When nesting paragraphs within paragraphs, by default we do not inherit the parent style,
     * but rather overwrite it. So in order to respect block indention, we need to lookup the
     * current parent's paragraph style.
     */
    open fun baseParagraphStyle(context: FormatContext): ParagraphStyle {
        val annotations =
            context.input.text.getStringAnnotations(context.start, context.end).mapNotNull {
                val depth = paragraphIndentionDepthFor(it.tag, it.item)
                if (depth > 0) {
                    Pair(it, depth)
                } else {
                    null
                }
            }
        if (annotations.isEmpty()) {
            return ParagraphStyle()
        }
        val (maxIndentedAnnotation, depth) = annotations.maxBy { it.second }
        return baseParagraphStyleFor(maxIndentedAnnotation.tag, depth)
    }

    open fun formatHeadingScaled(
        scale: Float,
        context: FormatContext,
    ) = listOf(
        baseParagraphStyle(context).copy(lineHeight = textStyle.lineHeight * scale),
        SpanStyle(
            fontWeight = FontWeight.Bold,
            fontSize = textStyle.fontSize * scale,
            //textDecoration = TextDecoration.Underline,
        ),
    )

    override fun formatHeading(tag: String, context: FormatContext): List<AnnotatedString.Annotation>? {
        return when (tag) {
            // Font sizes relative to regular text size matching https://www.w3schools.com/tags/tag_hn.asp
            "h1" -> formatHeadingScaled(2f, context)
            "h2" -> formatHeadingScaled(1.5f, context)
            "h3" -> formatHeadingScaled(1.17f, context)
            "h4" -> formatHeadingScaled(1f, context)
            "h5" -> formatHeadingScaled(0.83f, context)
            "h6" -> formatHeadingScaled(0.67f, context)
            // Hu, I don't know that one, keep it unchanged
            else -> null
        }
    }

    open fun clickToRevealAnnotation(
        state: MatrixFormatInteractionState,
        revealId: Int
    ) = LinkAnnotation.Clickable(MatrixBodyAnnotations.CLICK_TO_REVEAL, clickToRevealStyle) {
        if (revealId in state.expandedItems.value) {
            state.expandedItems.value -= revealId
        } else {
            state.expandedItems.value += revealId
        }
    }

    override fun formatSpan(attributes: SpanAttributes, context: FormatContext): List<AnnotatedString.Annotation> {
        return listOfNotNull(
            attributes.fgColor?.let { SpanStyle(color = Color(it)) },
            if (attributes.isSpoiler) {
                clickToRevealAnnotation(context.state, attributes.revealId)
            } else {
                null
            }
        )
    }

    override fun formatInlineCode(context: FormatContext): List<AnnotatedString.Annotation>? {
        return listOf(SpanStyle(fontFamily = FontFamily.Monospace, color = Color.White))
    }

    override fun formatCodeBlock(context: FormatContext): List<AnnotatedString.Annotation>? {
        return listOf(SpanStyle(fontFamily = FontFamily.Monospace, color = Color.White))
    }

    override fun formatBlockQuote(depth: Int, context: FormatContext): List<AnnotatedString.Annotation>? {
        return listOf(blockQuoteParagraphStyle(depth))
    }

    override fun formatRoomMention(context: FormatContext): List<AnnotatedString.Annotation>? {
        return listOf(
            SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)
        )
    }

    override fun formatUserMention(mention: MatrixToLink.UserMention, context: FormatContext): List<AnnotatedString.Annotation>? {
        return listOf(
            SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)
        )
    }

    override fun formatRoomLink(roomLink: MatrixToLink.RoomLink, context: FormatContext): List<AnnotatedString.Annotation>? {
        // Best overridden downstream to follow room links
        return formatWebLink(roomLink.rawUrl, context)
    }

    override fun formatMessageLink(messageLink: MatrixToLink.MessageLink, context: FormatContext): List<AnnotatedString.Annotation>? {
        // Best overridden downstream to follow message links
        return formatWebLink(messageLink.rawUrl, context)
    }

    override fun formatWebLink(href: String, context: FormatContext): List<AnnotatedString.Annotation>? {
        val hasScheme = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(href)
        val url = if (hasScheme) {
            href
        } else {
            "http://$href"
        }
        return listOf(
            LinkAnnotation.Url(url, urlStyle),
        )
    }

    override fun formatUnorderedListItem(depth: Int, context: FormatContext): List<AnnotatedString.Annotation>? {
        return listOf(listItemParagraphStyle(depth, cachedBulledWidth(depth)))
    }

    override fun formatOrderedListItem(depth: Int, context: FormatContext): List<AnnotatedString.Annotation>? {
        return listOf(listItemParagraphStyle(depth, blockIndention))
    }

    override fun formatDetailsSummary(
        revealId: Int,
        context: FormatContext,
    ): List<AnnotatedString.Annotation>? {
        return listOf(
            baseParagraphStyle(context),
            clickToRevealAnnotation(context.state, revealId)
        )
    }

    override fun formatDetailsContent(
        revealId: Int,
        context: FormatContext,
    ): List<AnnotatedString.Annotation>? {
        return listOf(
            baseParagraphStyle(context),
        )
    }
}
