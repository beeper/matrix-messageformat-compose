package com.beeper.android.messageformat.composer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import com.beeper.android.messageformat.DefaultMatrixBodyStyledFormatter
import com.beeper.android.messageformat.InlineImageInfo
import com.beeper.android.messageformat.SpanAttributes

open class DefaultMatrixComposerStyledFormatter(
    density: Density,
    textMeasurer: TextMeasurer,
    private val textStyle: TextStyle,
    private val inlineImageTextStyle: SpanStyle = SpanStyle(),
) : DefaultMatrixBodyStyledFormatter(
    density = density,
    textMeasurer = textMeasurer,
    textStyle = textStyle,
    handleWebLinkClick = {},
) {
    private val textColor: Color get() = textStyle.color.takeOrElse { Color.Black }

    override fun formatSyntaxMarker(context: FormatContext): List<AnnotatedString.Annotation>? =
        listOf(SpanStyle(color = textColor.copy(alpha = 0.5f)))

    override fun formatSpan(attributes: SpanAttributes, context: FormatContext): List<AnnotatedString.Annotation> {
        if (!attributes.isSpoiler) return super.formatSpan(attributes, context)
        return listOf(SpanStyle(background = textColor.copy(alpha = 0.12f)))
    }

    override fun formatInlineImageText(
        image: InlineImageInfo,
        context: FormatContext,
    ): List<AnnotatedString.Annotation>? = listOf(inlineImageTextStyle)
}

@Composable
fun defaultMatrixComposerStyledFormatter(
    textStyle: TextStyle,
    inlineImageTextStyle: SpanStyle = SpanStyle(),
): DefaultMatrixComposerStyledFormatter {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    return remember(density, textMeasurer, textStyle, inlineImageTextStyle) {
        DefaultMatrixComposerStyledFormatter(density, textMeasurer, textStyle, inlineImageTextStyle)
    }
}
