package com.beeper.android.messageformat.markdown

import androidx.compose.ui.text.buildAnnotatedString
import com.beeper.android.messageformat.InlineImageInfo
import com.beeper.android.messageformat.MatrixBodyAnnotations
import com.beeper.android.messageformat.MatrixBodyParseResult
import com.beeper.android.messageformat.MatrixToLink
import com.beeper.android.messageformat.markdown.html.MatrixHtmlProviders
import com.beeper.android.messageformat.markdown.html.MatrixInlineEntityTagRenderer
import com.beeper.android.messageformat.markdown.html.escapeHtml
import kotlinx.serialization.json.Json
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.CancellationToken
import org.intellij.markdown.parser.MarkdownParser

/**
 * Parses the markdown of [features] into Matrix HTML or into formatting of the source text itself.
 * [MatrixInlineEntity]s in the text are never parsed as markdown.
 */
class MatrixMarkdownParser(features: Set<MarkdownFeature>) {

    private val flavour = MatrixMarkdownFlavour(features)
    private val parser = MarkdownParser(flavour, false, CancellationToken.NonCancellable)
    private val sourceAnnotator = MarkdownSourceAnnotator(features)

    internal fun parse(markdown: CharSequence): ASTNode = parser.buildMarkdownTreeFromString(markdown)

    fun toHtml(markdown: String, entities: List<MatrixInlineEntity> = emptyList()): String =
        toHtml(MatrixMarkdownContent(markdown, entities))

    fun toHtml(content: MatrixMarkdownContent): String {
        val markdown = content.text
        val valid = content.entities
        if (valid.isEmpty()) {
            return HtmlGenerator(markdown, parse(markdown), flavour).generateHtml().unwrapSingleParagraph()
        }
        val masking = EntityMasking.mask(markdown, valid)
        val providers = MatrixHtmlProviders.create(flavour.features) { html ->
            masking.unmask(html) { entity -> escapeHtml(entity.sentText(markdown.substring(entity.start, entity.end))) }
        }
        return HtmlGenerator(masking.text, parse(masking.text), providers)
            .generateHtml(MatrixInlineEntityTagRenderer(markdown, masking))
            .unwrapSingleParagraph()
    }

    fun toDraft(markdown: String, entities: List<MatrixInlineEntity> = emptyList()): MatrixFormattedMessageDraft =
        toDraft(MatrixMarkdownContent(markdown, entities))

    fun toDraft(content: MatrixMarkdownContent): MatrixFormattedMessageDraft {
        val valid = content.entities
        val html = toHtml(content)
        val body = sentBody(content.text, valid)
        return MatrixFormattedMessageDraft(
            body = body,
            formattedBody = html.takeIf { it != escapeHtml(body) },
            mentionedUserIds = valid.filterIsInstance<MatrixInlineEntity.UserMention>().map { it.userId }.distinct(),
            mentionsRoom = valid.any { it is MatrixInlineEntity.RoomMention },
        )
    }

    /** Formats the markdown source itself, keeping every character in place. */
    fun annotateSource(markdown: String, entities: List<MatrixInlineEntity> = emptyList()): MatrixBodyParseResult =
        annotateSource(MatrixMarkdownContent(markdown, entities))

    fun annotateSource(content: MatrixMarkdownContent): MatrixBodyParseResult {
        val markdown = content.text
        val valid = content.entities
        if (valid.isEmpty()) return sourceAnnotator.annotate(markdown, parse(markdown))
        val masked = EntityMasking.mask(markdown, valid).text
        val result = sourceAnnotator.annotate(markdown, parse(masked), parsed = masked)
        val inlineImages = mutableMapOf<String, InlineImageInfo>()
        val text = buildAnnotatedString {
            append(result.text)
            valid.forEachIndexed { index, entity ->
                when (entity) {
                    is MatrixInlineEntity.UserMention -> {
                        val link: MatrixToLink = entity.toMatrixToLink()
                        val json = Json.encodeToString(link)
                        addStringAnnotation(MatrixBodyAnnotations.USER_MENTION, json, entity.start, entity.end)
                    }
                    is MatrixInlineEntity.RoomMention ->
                        addStringAnnotation(MatrixBodyAnnotations.ROOM_MENTION, "", entity.start, entity.end)
                    is MatrixInlineEntity.CustomEmote -> {
                        val id = "${MatrixBodyAnnotations.INLINE_IMAGE_PREFIX}$index"
                        inlineImages[id] = InlineImageInfo(
                            uri = entity.mxcUrl,
                            isEmote = true,
                            width = null,
                            height = null,
                            title = entity.shortcode,
                            alt = markdown.substring(entity.start, entity.end),
                        )
                        addStringAnnotation(MatrixBodyAnnotations.INLINE_IMAGE, id, entity.start, entity.end)
                    }
                }
            }
        }
        return result.copy(text = text, inlineImages = inlineImages)
    }

    /** [markdown] with each of [entities], in text order, replaced by its [MatrixInlineEntity.sentText]. */
    private fun sentBody(markdown: String, entities: List<MatrixInlineEntity>): String {
        val body = StringBuilder(markdown)
        for (entity in entities.asReversed()) {
            body.replace(entity.start, entity.end, entity.sentText(markdown.substring(entity.start, entity.end)))
        }
        return body.toString()
    }

    private fun String.unwrapSingleParagraph(): String {
        if (!startsWith(PARAGRAPH_START) || !endsWith(PARAGRAPH_END)) return this
        val body = substring(PARAGRAPH_START.length, length - PARAGRAPH_END.length)
        return if (body.contains(PARAGRAPH_START)) this else body
    }

    private companion object {
        const val PARAGRAPH_START = "<p>"
        const val PARAGRAPH_END = "</p>"
    }
}
