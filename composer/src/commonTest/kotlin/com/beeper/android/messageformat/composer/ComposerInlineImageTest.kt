package com.beeper.android.messageformat.composer

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.beeper.android.messageformat.markdown.MarkdownFeature
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposerInlineImageTest {

    private fun image(start: Int, end: Int, shortcode: String = "cat") = ComposerInlineImage(
        range = AnnotatedString.Range("img:$start", start, end),
        alt = ":$shortcode:",
        content = InlineTextContent(Placeholder(20.sp, 20.sp, PlaceholderVerticalAlign.TextCenter)) {},
        style = SpanStyle(fontSize = 20.sp),
    )

    private val sourceFormatter = MarkdownSourceFormatter(MatrixMarkdownParser(MarkdownFeature.ALL), NoStyleFormatter())

    private fun transformation(vararg images: ComposerInlineImage) =
        ComposerOutputTransformation(sourceFormatter, { emptyList() }) { images.toList() }

    @Test
    fun transformationFormatsMarkdownAroundPlaceholders() {
        val state = TextFieldState(":cat: **hi**")
        state.edit {
            with(transformation(image(0, 5))) { transformOutput() }
            val bold = getSpanStyles(TextRange(0, length))
                .filter { it.spanStyle.fontWeight == FontWeight.Bold }
                .map { it.textRange }
            assertEquals(listOf(TextRange(4, 6)), bold)
        }
    }

    @Test
    fun transformedOffsetsCollapseEachImageToOneCharacter() {
        val offsets = TransformedOffsets(listOf(image(3, 8), image(13, 19, "dog!")))
        assertEquals(0, offsets.toTransformed(0))
        assertEquals(3, offsets.toTransformed(3))
        assertEquals(4, offsets.toTransformed(8))
        assertEquals(9, offsets.toTransformed(13))
        assertEquals(10, offsets.toTransformed(19))
        assertEquals(12, offsets.toTransformed(21))
    }

    @Test
    fun transformationReplacesShortcodesWithStyledPlaceholders() {
        val transformation = transformation(image(3, 8), image(13, 19, "dog!"))
        val state = TextFieldState("hi :cat: and :dog!: there")
        state.edit {
            with(transformation) { transformOutput() }
            assertEquals("hi   and   there", asCharSequence().toString())
            val styled = getSpanStyles(TextRange(0, length))
                .filter { it.spanStyle.fontSize == 20.sp }
                .map { it.textRange }
                .sortedBy { it.start }
            assertEquals(listOf(TextRange(3, 4), TextRange(9, 10)), styled)
        }
    }

    @Test
    fun transformationSkipsImagesWhoseTextMovedAway() {
        val transformation = transformation(image(3, 8))
        val state = TextFieldState("hey :cat: there")
        state.edit {
            with(transformation) { transformOutput() }
            assertEquals("hey :cat: there", asCharSequence().toString())
        }
    }

    @Test
    fun transformCollapsesImagesAndMovesAnnotations() {
        val source = buildAnnotatedString {
            append("hi :cat: and :dog!: there")
            addStringAnnotation("tag", "a", 3, 8)
            addStringAnnotation("tag", "b", 9, 21)
        }
        val transformed = TransformedOffsets(listOf(image(3, 8), image(13, 19, "dog!"))).transform(source)
        assertEquals("hi \u2007 and \u2007 there", transformed.text)
        val ranges = transformed.getStringAnnotations("tag", 0, transformed.length).map { it.start to it.end }
        assertEquals(listOf(3 to 4, 5 to 12), ranges)
    }
}
