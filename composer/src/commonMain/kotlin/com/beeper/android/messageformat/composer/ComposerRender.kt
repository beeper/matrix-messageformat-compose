package com.beeper.android.messageformat.composer

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import co.touchlab.kermit.Logger
import com.beeper.android.messageformat.MatrixBodyParseResult
import com.beeper.android.messageformat.MatrixBodyStyledFormatter
import com.beeper.android.messageformat.MatrixFormatInteractionState
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import com.beeper.android.messageformat.markdown.MatrixMarkdownContent
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser

/**
 * U+2007 FIGURE SPACE placeholder for custom emotes, to work around lack of real inline content
 * in text fields.
 */
internal const val EMOTE_PLACEHOLDER = ' '

/** A custom emote the field shows as an image, over one [EMOTE_PLACEHOLDER] in [style]. */
internal data class ComposerEmote(val entity: MatrixInlineEntity.CustomEmote, val style: SpanStyle) {
    val range: TextRange get() = TextRange(entity.start, entity.end)
}

/**
 * Everything the field shows for [content]. [spanStyles] and [emotes] use the offsets of the content's text,
 * which [toDisplayOffset] maps to [displayText].
 */
internal class ComposerRender(
    val content: MatrixMarkdownContent,
    val emotes: List<ComposerEmote>,
    val spanStyles: List<AnnotatedString.Range<SpanStyle>>,
    parseResult: MatrixBodyParseResult,
    val interactionState: MatrixFormatInteractionState,
) {
    private val offsets = TransformedOffsets(emotes.map { it.range })

    /** The text as the field lays it out, with the annotations that backgrounds are drawn from. */
    val displayText: AnnotatedString = offsets.transform(parseResult.text)

    fun toDisplayOffset(offset: Int): Int = offsets.toTransformed(offset)

    /** Whether [layout] can be this render's, so that display offsets are safe to look up in it. */
    fun fits(layout: TextLayoutResult): Boolean = layout.layoutInput.text.length == displayText.length
}

/**
 * Renders composer content: its markdown formatting as span styles from [formatter], and as images those
 * custom emotes that [emoteStyle] has a placeholder style for.
 * The last render is kept, because the output transformation, the background drawing and the emote layout
 * each ask for the render of the same content.
 */
internal class ComposerRenderer(
    private val parser: MatrixMarkdownParser,
    private val formatter: MatrixBodyStyledFormatter,
    private val emoteStyle: (MatrixInlineEntity.CustomEmote) -> SpanStyle?,
) {
    private val log = Logger.withTag("ComposerRenderer")
    private var last: ComposerRender? = null

    fun render(content: MatrixMarkdownContent): ComposerRender {
        val emotes = content.entities.mapNotNull { entity ->
            (entity as? MatrixInlineEntity.CustomEmote)?.let { emote ->
                emoteStyle(emote)?.let { ComposerEmote(emote, it) }
            }
        }
        last?.takeIf { it.content == content && it.emotes == emotes }?.let { return it }
        // A parser failure must not break typing
        val parseResult = runCatching { parser.annotateSource(content) }.getOrElse {
            log.e("Formatting failed, showing plain text", it)
            MatrixBodyParseResult(content.text)
        }
        // Spoilers are never hidden while typing
        val interactionState =
            MatrixFormatInteractionState(parseResult.expandableItems, mutableStateOf(parseResult.expandableItems))
        val styled = formatter.applyStyle(parseResult, interactionState)
        // Links are not clickable while typing, only styled
        val linkStyles = styled.getLinkAnnotations(0, styled.length).mapNotNull { link ->
            link.item.linkStyles?.style?.let { AnnotatedString.Range(it, link.start, link.end) }
        }
        // Paragraph styles would split lines into separate paragraphs
        val spanStyles = styled.spanStyles + linkStyles
        return ComposerRender(content, emotes, spanStyles, parseResult, interactionState).also { last = it }
    }
}

/**
 * Sizes one [EMOTE_PLACEHOLDER] to [size] through font size and horizontal text scale.
 * Letter spacing can't do this: Android drops part of it at the edges of a run, and the placeholder is a run of its own.
 */
internal fun emotePlaceholderStyle(
    size: MatrixComposerEmoteSize,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
): SpanStyle {
    val baseFontSize = textStyle.fontSize.takeIf { it.isSpecified } ?: DEFAULT_FONT_SIZE
    val height = size.height.resolve(baseFontSize)
    val width = size.width.resolve(baseFontSize)
    // The span's letter spacing and scale replace the style's, except for the scale where spanScaleXBase says
    val glyphStyle = textStyle.copy(fontSize = height, letterSpacing = 0.sp, textGeometricTransform = null)
    val advance = textMeasurer.measure(EMOTE_PLACEHOLDER.toString(), glyphStyle).getBoundingBox(0).width *
        spanScaleXBase(textStyle)
    val widthPx = density.run { width.toPx() }
    val scaleX = if (advance > 0f) widthPx / advance else 1f
    return SpanStyle(
        fontSize = height,
        letterSpacing = 0.sp,
        textGeometricTransform = TextGeometricTransform(scaleX = scaleX),
    )
}

private fun TextUnit.resolve(fontSize: TextUnit): TextUnit = if (isEm) fontSize * value else this

private val DEFAULT_FONT_SIZE = 16.sp

private val LinkAnnotation.linkStyles
    get() = when (this) {
        is LinkAnnotation.Url -> styles
        is LinkAnnotation.Clickable -> styles
        else -> null
    }

/** The horizontal scale of [textStyle] that a span's [TextGeometricTransform.scaleX] is multiplied with, 1 where it replaces it. */
internal expect fun spanScaleXBase(textStyle: TextStyle): Float
