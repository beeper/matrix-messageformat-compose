package com.beeper.android.messageformat.composer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.beeper.android.messageformat.MatrixBodyDrawStyle
import com.beeper.android.messageformat.MatrixBodyParseResult
import com.beeper.android.messageformat.MatrixBodyStyledFormatter
import com.beeper.android.messageformat.drawMatrixBodyBackgrounds
import com.beeper.android.messageformat.matrixFormatOnTextRenderResult
import com.beeper.android.messageformat.rememberMatrixFormatInteractionState
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser
import com.beeper.android.messageformat.markdown.MarkdownFeature
import kotlin.math.roundToInt

/**
 * A text field that formats markdown in place, as [features] allow.
 * Custom emotes render through [inlineContent].
 */
@Composable
fun MatrixMarkdownTextField(
    state: MatrixMarkdownTextFieldState,
    modifier: Modifier = Modifier,
    features: Set<MarkdownFeature> = MarkdownFeature.ALL,
    textStyle: TextStyle = TextStyle.Default,
    formatter: MatrixBodyStyledFormatter = defaultMatrixComposerStyledFormatter(textStyle),
    drawStyle: MatrixBodyDrawStyle = remember { MatrixBodyDrawStyle() },
    inlineContent: (@Composable (MatrixBodyParseResult) -> Map<String, InlineTextContent>)? = null,
    enabled: Boolean = true,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    cursorBrush: Brush = SolidColor(Color.Black),
    interactionSource: MutableInteractionSource? = null,
    scrollState: ScrollState = rememberScrollState(),
    placeholder: @Composable (() -> Unit)? = null,
    decorator: TextFieldDecorator? = null,
) {
    val parser = remember(features) { MatrixMarkdownParser(features) }
    val sourceFormatter = remember(parser, formatter) {
        MarkdownSourceFormatter(parser, formatter)
    }

    SideEffect {
        if (state.sourceFormatter !== sourceFormatter) {
            state.sourceFormatter = sourceFormatter
            state.refreshFormatting()
        }
    }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val parseResult = sourceFormatter.annotate(state.text.toString(), state.entities)
    val images = if (inlineContent == null) {
        emptyList()
    } else {
        val content = inlineContent(parseResult)
        remember(parseResult, content, textStyle, textMeasurer, density) {
            composerInlineImages(parseResult, content, textStyle, textMeasurer, density)
        }
    }
    val currentImages by rememberUpdatedState(images)
    val outputTransformation = remember(state) { ComposerOutputTransformation { currentImages } }
    val transformedOffsets = remember(images) { TransformedOffsets(images) }

    var layoutProvider by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    val scrollsVertically = lineLimits is TextFieldLineLimits.MultiLine
    fun scrollOffset() = IntOffset(
        if (scrollsVertically) 0 else -scrollState.value,
        if (scrollsVertically) -scrollState.value else 0,
    )
    val transformedText = remember(parseResult, transformedOffsets) {
        transformedOffsets.transform(parseResult.text)
    }
    val interactionState = rememberMatrixFormatInteractionState(parseResult)
    val backgrounds = Modifier.drawBehind {
        val layout = layoutProvider?.invoke() ?: return@drawBehind
        val result = matrixFormatOnTextRenderResult(transformedText, drawStyle, layout)
        val offset = scrollOffset()
        translate(left = offset.x.toFloat(), top = offset.y.toFloat()) {
            drawMatrixBodyBackgrounds(drawStyle, result, interactionState)
        }
    }
    BasicTextField(
        state = state.textFieldState,
        modifier = modifier,
        enabled = enabled,
        inputTransformation = state.inputTransformation,
        textStyle = textStyle,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        lineLimits = lineLimits,
        onTextLayout = { getResult -> layoutProvider = getResult },
        interactionSource = interactionSource,
        cursorBrush = cursorBrush,
        outputTransformation = outputTransformation,
        scrollState = scrollState,
        decorator = { innerTextField ->
            val decorated: @Composable () -> Unit = {
                Box(backgrounds.clipToBounds()) {
                    if (placeholder != null && state.text.isEmpty()) placeholder()
                    innerTextField()
                    InlineImages(
                        images = images,
                        offsets = transformedOffsets,
                        layout = { layoutProvider?.invoke() },
                        scrollOffset = ::scrollOffset,
                    )
                }
            }
            if (decorator != null) decorator.Decoration(decorated) else decorated()
        },
    )
}

/** Lays out each inline image over its placeholder character. */
@Composable
private fun InlineImages(
    images: List<ComposerInlineImage>,
    offsets: TransformedOffsets,
    layout: () -> TextLayoutResult?,
    scrollOffset: () -> IntOffset,
) {
    if (images.isEmpty()) return
    // One Box per image, so each image is exactly one measurable
    val content: @Composable () -> Unit = { images.forEach { Box { it.content.children(it.alt) } } }
    Layout(content) { measurables, constraints ->
        val layoutResult = layout()
        val boxes = images.map { image ->
            val placeholderOffset = offsets.toTransformed(image.range.start)
            layoutResult?.placeholderBox(placeholderOffset, image.style.fontSize.toPx())
        }
        val placeables = measurables.mapIndexed { index, measurable ->
            val box = boxes[index] ?: return@mapIndexed null
            val width = box.width.roundToInt().coerceAtLeast(0)
            val height = box.height.roundToInt().coerceAtLeast(0)
            measurable.measure(Constraints.fixed(width, height))
        }
        layout(constraints.minWidth, constraints.minHeight) {
            val scroll = scrollOffset()
            placeables.forEachIndexed { index, placeable ->
                val box = boxes[index] ?: return@forEachIndexed
                placeable?.place(box.left.roundToInt() + scroll.x, box.top.roundToInt() + scroll.y)
            }
        }
    }
}

/**
 * Where to draw the inline image whose placeholder character sits at [transformedOffset]: as wide
 * as that character, [heightPx] tall, and centered on the line like
 * [androidx.compose.ui.text.PlaceholderVerticalAlign.TextCenter].
 */
private fun TextLayoutResult.placeholderBox(transformedOffset: Int, heightPx: Float): Rect? {
    if (transformedOffset >= layoutInput.text.length) return null
    val glyph = getBoundingBox(transformedOffset)
    val line = getLineForOffset(transformedOffset)
    val lineCenter = (getLineTop(line) + getLineBottom(line)) / 2
    return Rect(glyph.left, lineCenter - heightPx / 2, glyph.right, lineCenter + heightPx / 2)
}
