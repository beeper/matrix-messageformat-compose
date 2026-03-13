package com.beeper.android.messageformat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.buildAnnotatedString
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json

open class MatrixBodyToPlaintextFormatter : MatrixBodyStyledFormatter() {
    override fun formatHeading(tag: String, context: FormatContext) = listOf(ParagraphStyle())
    override fun formatSpan(attributes: SpanAttributes, context: FormatContext) = null
    override fun formatInlineCode(context: FormatContext) = null
    override fun formatCodeBlock(context: FormatContext) = null
    override fun formatBlockQuote(depth: Int, context: FormatContext) = listOf(ParagraphStyle())
    override fun formatRoomMention(context: FormatContext) = null
    override fun formatUserMention(mention: MatrixToLink.UserMention, context: FormatContext) = null
    override fun formatRoomLink(roomLink: MatrixToLink.RoomLink, context: FormatContext) = null
    override fun formatMessageLink(messageLink: MatrixToLink.MessageLink, context: FormatContext) = null
    override fun formatWebLink(href: String, context: FormatContext) = null
    override fun formatUnorderedListItem(depth: Int, context: FormatContext) = listOf(ParagraphStyle())
    override fun formatOrderedListItem(depth: Int, context: FormatContext) = listOf(ParagraphStyle())
    override fun formatTable(depth: Int, context: FormatContext) = listOf(ParagraphStyle())
    override fun formatDetailsSummary(revealId: Int, context: FormatContext) = listOf(ParagraphStyle())
    override fun formatDetailsContent(revealId: Int, context: FormatContext) = listOf(ParagraphStyle())
}

object StrippedFormattingRenderer {

    private val STRIP_WHITESPACE_REGEX = "\\s+".toRegex()
    private val STRIP_WHITESPACE_EXCEPT_NEWLINES_REGEX = "[\\s&&[^\\n]]+".toRegex()

    fun formattedContentToPlainString(
        parseResult: MatrixBodyParseResult,
        formatter: MatrixBodyStyledFormatter = MatrixBodyToPlaintextFormatter(),
        stripNewlines: Boolean = true,
        spoilerReplacement: (AnnotatedString) -> String = ::defaultSpoilerReplacement,
        inlineImageReplacement: (InlineImageInfo) -> String = ::defaultInlineImageReplacement,
    ): String {
        return stripFormattingKeepingAnnotations(
            parseResult,
            formatter,
            spoilerReplacement,
            inlineImageReplacement
        ).toString()
            .trim()
            .replace(if (stripNewlines) STRIP_WHITESPACE_REGEX else STRIP_WHITESPACE_EXCEPT_NEWLINES_REGEX, " ")
            .let {
                if (stripNewlines) {
                    it
                } else {
                    var tmp: String
                    var new = it
                    do {
                        tmp = new
                        new = tmp.replace("\n\n", "\n")
                    } while (tmp != new)
                    new
                }
            }
    }

    private fun defaultInlineImageReplacement(info: InlineImageInfo): String =
        info.alt ?: info.title ?: if (info.isEmote) "[emote]" else "[IMG]"

    private fun defaultSpoilerReplacement(spoiler: AnnotatedString): String =
        "█".repeat(spoiler.length)

    fun stripFormattingKeepingAnnotations(
        parseResult: MatrixBodyParseResult,
        formatter: MatrixBodyStyledFormatter = MatrixBodyToPlaintextFormatter(),
        spoilerReplacement: (AnnotatedString) -> String = ::defaultSpoilerReplacement,
        inlineImageReplacement: ((InlineImageInfo) -> String)? = ::defaultInlineImageReplacement
    ): AnnotatedString {
        // Strip spoilers, formatting, and unnecessary whitespace
        return formatter
            .applyStyle(parseResult, MatrixFormatInteractionState(parseResult.expandableItems, mutableStateOf(emptySet())))
            .stripDetailsContent()
            .ensureParagraphItemNewlines()
            .let {
                if (inlineImageReplacement != null) {
                    it.replaceInlineImages(parseResult.inlineImages, inlineImageReplacement)
                } else {
                    it
                }
            }
            .stripMatrixSpoilers(spoilerReplacement)
    }

    fun AnnotatedString.stripMatrixSpoilers(
        spoilerReplacement: (AnnotatedString) -> String,
    ) = replaceAnnotationContent(
        tag = MatrixBodyAnnotations.SPAN,
        predicate = {
            try {
                Json.decodeFromString<SpanAttributes>(it.item).isSpoiler
            } catch (e: Exception) {
                Logger.withTag("stripMatrixSpoilers").e("Failed to parse span attributes", e)
                false
            }
        }
    ) { content, _ ->
        AnnotatedString(spoilerReplacement(content))
    }

    fun AnnotatedString.stripDetailsContent() = replaceAnnotationContent(
        tag = MatrixBodyAnnotations.DETAILS_CONTENT,
    ) { _, _ ->
        AnnotatedString("")
    }

    private fun AnnotatedString.replaceInlineImages(
        inlineImages: Map<String, InlineImageInfo>,
        inlineImageReplacement: ((InlineImageInfo) -> String),
    ) = if (inlineImages.isEmpty()) this else replaceAnnotationContent(
        MatrixBodyAnnotations.INLINE_IMAGE,
    ) { content, id ->
        val info = inlineImages[id]
        if (info == null) {
            Logger.withTag("replaceInlineImages").w("Unknown URI: $id")
            return@replaceAnnotationContent content
        }
        AnnotatedString(inlineImageReplacement(info))
    }

    private fun AnnotatedString.ensureParagraphItemNewlines() = replaceAnnotationContent(
        paragraphStyles,
        recurse = true,
    ) { content, _, _ ->
        buildAnnotatedString {
            append("\n")
            append(content)
            append("\n")
        }
    }

    fun AnnotatedString.replaceAnnotationContent(
        tag: String,
        predicate: (AnnotatedString.Range<String>) -> Boolean = { true },
        replacement: (content: AnnotatedString, annotation: String) -> AnnotatedString,
    ) = replaceAnnotationContent(
        annotationRanges = getStringAnnotations(tag, 0, text.length).filter(predicate),
        replacement = { content, _, annotation -> replacement(content, annotation) },
    )

    fun <T> AnnotatedString.replaceAnnotationContent(
        annotationRanges: List<AnnotatedString.Range<T>>,
        recurse: Boolean = false,
        replacement: (content: AnnotatedString, annotationKey: String, annotation: T) -> AnnotatedString,
    ): AnnotatedString {
        val ranges = annotationRanges.sortedWith(compareBy({ it.start }, { -it.end }))
        if (ranges.isEmpty()) return this

        return buildAnnotatedString {
            var cursor = 0

            for (r in ranges) {
                // Skip overlapping/contained ranges
                if (r.start < cursor) continue

                if (cursor < r.start) {
                    append(subSequence(cursor, r.start))
                }

                if (recurse) {
                    val containedRanges = annotationRanges.filter {
                        it.start >= r.start
                                && it.end <= r.end
                                && (it.start != r.start || it.end != r.end)
                    }.map { it.copy(start = it.start - r.start, end = it.end - r.start) }
                    append(
                        replacement(
                            subSequence(r.start, r.end).replaceAnnotationContent(containedRanges, true, replacement),
                            r.tag,
                            r.item
                        )
                    )
                } else {
                    append(replacement(subSequence(r.start, r.end), r.tag, r.item))
                }

                cursor = r.end
            }

            if (cursor < text.length) {
                append(subSequence(cursor, text.length))
            }
        }
    }
}
