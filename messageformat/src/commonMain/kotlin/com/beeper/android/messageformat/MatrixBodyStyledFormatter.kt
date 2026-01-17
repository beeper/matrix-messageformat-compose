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

    abstract fun formatHeading(tag: String): List<AnnotatedString.Annotation>?
    abstract fun formatSpan(attributes: SpanAttributes, state: MatrixFormatInteractionState): List<AnnotatedString.Annotation>?
    abstract fun formatInlineCode(): List<AnnotatedString.Annotation>?
    abstract fun formatCodeBlock(): List<AnnotatedString.Annotation>?
    abstract fun formatBlockQuote(depth: Int): List<AnnotatedString.Annotation>?
    abstract fun formatRoomMention(): List<AnnotatedString.Annotation>?
    abstract fun formatUserMention(mention: MatrixToLink.UserMention): List<AnnotatedString.Annotation>?
    abstract fun formatRoomLink(roomLink: MatrixToLink.RoomLink): List<AnnotatedString.Annotation>?
    abstract fun formatMessageLink(messageLink: MatrixToLink.MessageLink): List<AnnotatedString.Annotation>?
    abstract fun formatWebLink(href: String): List<AnnotatedString.Annotation>?
    abstract fun formatUnorderedListItem(depth: Int): List<AnnotatedString.Annotation>?
    abstract fun formatOrderedListItem(depth: Int): List<AnnotatedString.Annotation>?
    abstract fun formatDetailsSummary(revealId: Int, state: MatrixFormatInteractionState): List<AnnotatedString.Annotation>?
    abstract fun formatDetailsContent(revealId: Int, state: MatrixFormatInteractionState): List<AnnotatedString.Annotation>?

    open fun mapAnnotation(
        range: AnnotatedString.Range<out AnnotatedString.Annotation>,
        state: MatrixFormatInteractionState,
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
                    formatHeading(annotation.value)
                }
                MatrixBodyAnnotations.ROOM_MENTION -> formatRoomMention()
                MatrixBodyAnnotations.USER_MENTION -> {
                    try {
                        val link = Json.decodeFromString<MatrixToLink>(annotation.value) as MatrixToLink.UserMention
                        formatUserMention(link)
                    } catch (e: Exception) {
                        log.e("User mention data parsing error", e)
                        null
                    }
                }
                MatrixBodyAnnotations.ROOM_LINK -> {
                    try {
                        val link = Json.decodeFromString<MatrixToLink>(annotation.value) as MatrixToLink.RoomLink
                        formatRoomLink(link)
                    } catch (e: Exception) {
                        log.e("Room link data parsing error", e)
                        null
                    }
                }
                MatrixBodyAnnotations.MESSAGE_LINK -> {
                    try {
                        val link = Json.decodeFromString<MatrixToLink>(annotation.value) as MatrixToLink.MessageLink
                        formatMessageLink(link)
                    } catch (e: Exception) {
                        log.e("Message link data parsing error", e)
                        null
                    }
                }
                MatrixBodyAnnotations.WEB_LINK -> formatWebLink(annotation.value)
                MatrixBodyAnnotations.BLOCK_QUOTE -> {
                    val depth = annotation.value.toIntOrNull()
                    if (depth == null) {
                        log.e("Block quote data parsing error, is not an int depth: ${annotation.value}")
                        null
                    } else {
                        formatBlockQuote(depth)
                    }
                }
                MatrixBodyAnnotations.INLINE_CODE -> formatInlineCode()
                MatrixBodyAnnotations.BLOCK_CODE -> formatCodeBlock()
                MatrixBodyAnnotations.SPAN -> {
                    val attributes = try {
                        Json.decodeFromString<SpanAttributes>(annotation.value)
                    } catch (e: Exception) {
                        log.e("Span data parsing error", e)
                        return listOf(range)
                    }
                    formatSpan(attributes, state)
                }

                MatrixBodyAnnotations.UNORDERED_LIST_ITEM -> {
                    val depth = annotation.value.toIntOrNull()
                    if (depth == null) {
                        log.e("Unordered list item data parsing error, is not an int depth: ${annotation.value}")
                        null
                    } else {
                        formatUnorderedListItem(depth)
                    }
                }
                MatrixBodyAnnotations.ORDERED_LIST_ITEM -> {
                    val depth = annotation.value.toIntOrNull()
                    if (depth == null) {
                        log.e("Ordered list data parsing error, is not an int depth: ${annotation.value}")
                        null
                    } else {
                        formatOrderedListItem(depth)
                    }
                }

                MatrixBodyAnnotations.DETAILS_SUMMARY -> {
                    val revealId = annotation.value.toIntOrNull()
                    if (revealId == null) {
                        log.e("Details summary item data parsing error, is not an int ID: ${annotation.value}")
                        null
                    } else {
                        formatDetailsSummary(revealId, state)
                    }
                }
                MatrixBodyAnnotations.DETAILS_CONTENT -> {
                    val revealId = annotation.value.toIntOrNull()
                    if (revealId == null) {
                        log.e("Details content item data parsing error, is not an int ID: ${annotation.value}")
                        null
                    } else {
                        formatDetailsContent(revealId, state)
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
            val mapped = mapAnnotation(it, state)
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

    open fun formatHeadingScaled(scale: Float, underline: Boolean = false) = listOf(
        ParagraphStyle(lineHeight = textStyle.lineHeight * scale),
        SpanStyle(
            fontWeight = FontWeight.Bold,
            fontSize = textStyle.fontSize * scale,
            textDecoration = if (underline) TextDecoration.Underline else null
        ),
    )

    override fun formatHeading(tag: String): List<AnnotatedString.Annotation>? {
        return when (tag) {
            // Font sizes relative to regular text size matching https://www.w3schools.com/tags/tag_hn.asp
            "h1" -> formatHeadingScaled(2f)
            "h2" -> formatHeadingScaled(1.5f)
            "h3" -> formatHeadingScaled(1.17f)
            "h4" -> formatHeadingScaled(1f)
            "h5" -> formatHeadingScaled(0.83f)
            "h6" -> formatHeadingScaled(0.67f)
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

    override fun formatSpan(attributes: SpanAttributes, state: MatrixFormatInteractionState): List<AnnotatedString.Annotation> {
        return listOfNotNull(
            attributes.fgColor?.let { SpanStyle(color = Color(it)) },
            if (attributes.isSpoiler) {
                clickToRevealAnnotation(state, attributes.revealId)
            } else {
                null
            }
        )
    }

    override fun formatInlineCode(): List<AnnotatedString.Annotation>? {
        return listOf(SpanStyle(fontFamily = FontFamily.Monospace, color = Color.White))
    }

    override fun formatCodeBlock(): List<AnnotatedString.Annotation>? {
        return listOf(SpanStyle(fontFamily = FontFamily.Monospace, color = Color.White))
    }

    override fun formatBlockQuote(depth: Int): List<AnnotatedString.Annotation>? {
        val indent = blockIndention * depth
        return listOf(
            ParagraphStyle(
                textIndent = TextIndent(firstLine = indent, restLine = indent)
            )
        )
    }

    override fun formatRoomMention(): List<AnnotatedString.Annotation>? {
        return listOf(
            SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)
        )
    }

    override fun formatUserMention(mention: MatrixToLink.UserMention): List<AnnotatedString.Annotation>? {
        return listOf(
            SpanStyle(color = Color.White, fontWeight = FontWeight.Bold)
        )
    }

    override fun formatRoomLink(roomLink: MatrixToLink.RoomLink): List<AnnotatedString.Annotation>? {
        // Best overridden downstream to follow room links
        return listOf(
            SpanStyle(color = Color.Blue)
        )
    }

    override fun formatMessageLink(messageLink: MatrixToLink.MessageLink): List<AnnotatedString.Annotation>? {
        // Best overridden downstream to follow message links
        return listOf(
            SpanStyle(color = Color.Blue)
        )
    }

    override fun formatWebLink(href: String): List<AnnotatedString.Annotation>? {
        return listOf(
            LinkAnnotation.Url(href, urlStyle),
        )
    }

    open fun formatListItem(depth: Int, bulletWidth: TextUnit): List<AnnotatedString.Annotation> {
        val baseDepth = blockIndention * depth
        return listOf(
            ParagraphStyle(
                textIndent = TextIndent(
                    firstLine = baseDepth,
                    restLine = (baseDepth.value + bulletWidth.value).sp,
                )
            )
        )
    }

    override fun formatUnorderedListItem(depth: Int): List<AnnotatedString.Annotation>? {
        val bullet = listBulletForDepth(depth)
        val bulletWidth = cachedBulletSizes.getOrPut(bullet) {
            density.run {
                textMeasurer.measure(bullet).size.width.toSp()
            }
        }
        return formatListItem(depth, bulletWidth)
    }

    override fun formatOrderedListItem(depth: Int): List<AnnotatedString.Annotation>? {
        return formatListItem(depth, blockIndention)
    }

    override fun formatDetailsSummary(
        revealId: Int,
        state: MatrixFormatInteractionState
    ): List<AnnotatedString.Annotation>? {
        return listOf(
            ParagraphStyle(),
            clickToRevealAnnotation(state, revealId)
        )
    }

    override fun formatDetailsContent(
        revealId: Int,
        state: MatrixFormatInteractionState
    ): List<AnnotatedString.Annotation>? {
        return listOf(
            ParagraphStyle()
        )
    }
}
