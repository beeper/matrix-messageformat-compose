package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.beeper.android.messageformat.MatrixBodyAnnotations
import com.beeper.android.messageformat.MatrixBodyParseResult

/**
 * U+2007 FIGURE SPACE placeholder for inline images, to work around lack of real inline content
 * in text fields.
 */
internal const val INLINE_IMAGE_PLACEHOLDER = '\u2007'

/** An inline image to draw at [range], where the text currently reads [alt]. */
internal class ComposerInlineImage(
    val range: AnnotatedString.Range<String>,
    val alt: String,
    val content: InlineTextContent,
    val style: SpanStyle,
) {
    /** Whether [text] still reads [alt] at [range], which an edit before the next parse can change. */
    fun matches(text: CharSequence): Boolean =
        range.end <= text.length && text.subSequence(range.start, range.end) == alt
}

internal fun composerInlineImages(
    parseResult: MatrixBodyParseResult,
    inlineContent: Map<String, InlineTextContent>,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
): List<ComposerInlineImage> {
    val text = parseResult.text
    return text.getStringAnnotations(MatrixBodyAnnotations.INLINE_IMAGE, 0, text.length).mapNotNull { range ->
        val content = inlineContent[range.item] ?: return@mapNotNull null
        val alt = text.text.substring(range.start, range.end)
        ComposerInlineImage(range, alt, content, placeholderStyle(content, textStyle, textMeasurer, density))
    }
}

/** Sizes one [INLINE_IMAGE_PLACEHOLDER] to the placeholder box through font size and letter spacing. */
private fun placeholderStyle(
    content: InlineTextContent,
    textStyle: TextStyle,
    textMeasurer: TextMeasurer,
    density: Density,
): SpanStyle {
    val baseFontSize = textStyle.fontSize.takeIf { it.isSpecified } ?: DEFAULT_FONT_SIZE
    val height = content.placeholder.height.resolve(baseFontSize)
    val width = content.placeholder.width.resolve(baseFontSize)
    val measured = textMeasurer.measure(INLINE_IMAGE_PLACEHOLDER.toString(), textStyle.copy(fontSize = height))
    val advance = measured.size.width
    val widthPx = density.run { width.toPx() }
    val letterSpacing = ((widthPx - advance) / (density.density * density.fontScale)).sp
    return SpanStyle(fontSize = height, letterSpacing = letterSpacing)
}

private fun TextUnit.resolve(fontSize: TextUnit): TextUnit = if (isEm) fontSize * value else this

private val DEFAULT_FONT_SIZE = 16.sp
