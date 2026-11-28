package com.beeper.android.messageformat.composer

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.node.Ref
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.toOffset
import com.beeper.android.messageformat.DrawPosition
import com.beeper.android.messageformat.InlineImageInfo
import com.beeper.android.messageformat.MatrixBodyDrawStyle
import com.beeper.android.messageformat.MatrixBodyPreFormatStyle
import com.beeper.android.messageformat.MatrixBodyStyledFormatter
import com.beeper.android.messageformat.drawMatrixBodyBackgrounds
import com.beeper.android.messageformat.matrixFormatOnTextRenderResult
import com.beeper.android.messageformat.perLineBoundingBoxesForRange
import com.beeper.android.messageformat.markdown.MarkdownFeature
import com.beeper.android.messageformat.markdown.MatrixInlineEntity
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser
import kotlin.math.roundToInt

/**
 * A text field that formats markdown in place, as [features] allow.
 * Inline images, such as custom emotes, are drawn by [inlineContent] and sized by its placeholders, like the inline
 * images of a message. It gets the [MatrixInlineEntity.InlineImage.image] of each, keyed by [InlineImageInfo.id].
 * Without [inlineContent], inline images show as their text.
 * [drawMark] draws the marks of [state], which do not show without it. It draws behind the text, once per
 * line a mark is on, given where the mark is on that line and the line's baseline. [onMarkClick] is told of
 * a tap on a mark, but not of a drag or long press, with the bounds of the mark on the tapped line. The tap
 * still places the cursor.
 * [inputTransformation] transforms the user's input before the field keeps its entities in step with it.
 */
@Composable
fun MatrixMarkdownTextField(
    state: MatrixMarkdownTextFieldState,
    modifier: Modifier = Modifier,
    features: Set<MarkdownFeature> = MarkdownFeature.ALL,
    textStyle: TextStyle = TextStyle.Default,
    preFormatStyle: MatrixBodyPreFormatStyle = remember { MatrixBodyPreFormatStyle() },
    formatter: MatrixBodyStyledFormatter = defaultMatrixComposerStyledFormatter(textStyle),
    drawStyle: MatrixBodyDrawStyle = remember { MatrixBodyDrawStyle() },
    inlineContent: (@Composable (Map<String, InlineImageInfo>) -> Map<String, InlineTextContent>)? = null,
    drawMark: (DrawScope.(mark: ComposerTextMark<*>, position: DrawPosition.InLine, baseline: Float) -> Unit)? = null,
    onMarkClick: ((mark: ComposerTextMark<*>, boundsInWindow: Rect) -> Unit)? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    inputTransformation: InputTransformation? = null,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    cursorBrush: Brush = SolidColor(Color.Black),
    interactionSource: MutableInteractionSource? = null,
    scrollState: ScrollState = rememberScrollState(),
    placeholder: @Composable (() -> Unit)? = null,
    decorator: TextFieldDecorator? = null,
) {
    val parser = remember(features, preFormatStyle) { MatrixMarkdownParser(features, preFormatStyle) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val entities = state.entities
    val emoteImages = remember(entities) {
        entities.filterIsInstance<MatrixInlineEntity.InlineImage>().associate { it.image.id to it.image }
    }
    val emoteContent = inlineContent?.invoke(emoteImages)
    val currentEmoteContent by rememberUpdatedState(emoteContent)
    val renderer = remember(parser, formatter, textStyle, textMeasurer, density) {
        ComposerRenderer(parser, formatter) { entity ->
            currentEmoteContent?.let { content ->
                val placeholder = content[entity.image.id]?.placeholder
                val size = placeholder?.let { MatrixComposerEmoteSize(it.width, it.height) } ?: MatrixComposerEmoteSize.Default
                emotePlaceholderStyle(size, textStyle, textMeasurer, density)
            }
        }
    }
    val outputTransformation = remember(state, renderer) {
        ComposerOutputTransformation { text -> renderer.render(state.contentFor(text)) }
    }
    val render = renderer.render(state.content)

    var layoutProvider by remember { mutableStateOf<(() -> TextLayoutResult?)?>(null) }
    val scrollsVertically = lineLimits is TextFieldLineLimits.MultiLine
    fun scrollOffset() = IntOffset(
        if (scrollsVertically) 0 else -scrollState.value,
        if (scrollsVertically) -scrollState.value else 0,
    )
    val backgrounds = Modifier.drawBehind {
        val layout = layoutProvider?.invoke()?.takeIf(render::fits) ?: return@drawBehind
        val result = matrixFormatOnTextRenderResult(render.displayText, drawStyle, layout)
        val offset = scrollOffset()
        translate(left = offset.x.toFloat(), top = offset.y.toFloat()) {
            drawMatrixBodyBackgrounds(drawStyle, result, render.interactionState)
            if (drawMark != null) {
                state.marks.forEach { mark ->
                    render.markSegments(mark, layout).forEach { drawMark(mark, it, layout.getLineBaseline(it.line)) }
                }
            }
        }
    }
    val currentOnMarkClick by rememberUpdatedState(onMarkClick)
    val markCoordinates = remember { Ref<LayoutCoordinates>() }
    val markClicks = if (onMarkClick == null) Modifier else Modifier
        .onGloballyPositioned { markCoordinates.value = it }
        .pointerInput(state, renderer, scrollState, scrollsVertically) {
            awaitEachGesture {
                // Only observed, so that the field still places the cursor
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                    waitForUpOrCancellation(PointerEventPass.Initial)
                } ?: return@awaitEachGesture
                if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                val render = renderer.render(state.content)
                val layout = layoutProvider?.invoke()?.takeIf(render::fits) ?: return@awaitEachGesture
                val coordinates = markCoordinates.value?.takeIf { it.isAttached } ?: return@awaitEachGesture
                val scroll = scrollOffset().toOffset()
                val position = up.position - scroll
                for (mark in state.marks) {
                    val segment = render.markSegments(mark, layout).firstOrNull { position in it.rect } ?: continue
                    val topLeft = coordinates.localToWindow(segment.rect.topLeft + scroll)
                    currentOnMarkClick?.invoke(mark, Rect(topLeft, segment.rect.size))
                    break
                }
            }
        }
    BasicTextField(
        state = state.textFieldState,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                if (event.key in NAVIGATION_KEYS) state.navigatingByKey = event.type == KeyEventType.KeyDown
                false
            }
            // The key up of a key held while focus is lost goes elsewhere
            .onFocusChanged { if (!it.hasFocus) state.navigatingByKey = false },
        enabled = enabled,
        readOnly = readOnly,
        inputTransformation = remember(state, inputTransformation) { state.inputTransformationAfter(inputTransformation) },
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
                Box(backgrounds.then(markClicks).clipToBounds()) {
                    if (placeholder != null && state.text.isEmpty()) placeholder()
                    innerTextField()
                    if (emoteContent != null) {
                        Emotes(
                            render = render,
                            emote = { emote ->
                                val alternateText = emote.image.emoteFallbackText ?: emote.image.alt.orEmpty()
                                emoteContent[emote.image.id]?.children?.invoke(alternateText)
                            },
                            layout = { layoutProvider?.invoke() },
                            scrollOffset = ::scrollOffset,
                        )
                    }
                }
            }
            if (decorator != null) decorator.Decoration(decorated) else decorated()
        },
    )
}

private val NAVIGATION_KEYS = setOf(
    Key.DirectionLeft,
    Key.DirectionRight,
    Key.DirectionUp,
    Key.DirectionDown,
    Key.MoveHome,
    Key.MoveEnd,
    Key.PageUp,
    Key.PageDown,
)

/** Where [layout] shows [mark], per line. Nothing for a mark that does not fit this render. */
private fun ComposerRender.markSegments(mark: ComposerTextMark<*>, layout: TextLayoutResult): List<DrawPosition.InLine> {
    val start = toDisplayOffset(mark.start)
    val end = toDisplayOffset(mark.end)
    if (start >= end || end > displayText.length) return emptyList()
    return layout.perLineBoundingBoxesForRange(start, end)
}

/** The size of a custom emote in a [MatrixMarkdownTextField], in sp or in em of the text's font size. */
internal data class MatrixComposerEmoteSize(val width: TextUnit, val height: TextUnit) {
    companion object {
        val Default = MatrixComposerEmoteSize(1.em, 1.em)
    }
}

/** Lays out each emote of [render] over its placeholder character. */
@Composable
private fun Emotes(
    render: ComposerRender,
    emote: @Composable (MatrixInlineEntity.InlineImage) -> Unit,
    layout: () -> TextLayoutResult?,
    scrollOffset: () -> IntOffset,
) {
    val emotes = render.emotes
    if (emotes.isEmpty()) return
    // One Box per emote, so each emote is exactly one measurable, sized to its placeholder
    val content: @Composable () -> Unit = {
        emotes.forEach { Box(propagateMinConstraints = true) { emote(it.entity) } }
    }
    Layout(content) { measurables, constraints ->
        val layoutResult = layout()?.takeIf(render::fits)
        val boxes = emotes.map {
            val placeholderOffset = render.toDisplayOffset(it.range.start)
            layoutResult?.placeholderBox(placeholderOffset, it.style.fontSize.toPx())
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
 * Where to draw the emote whose placeholder character sits at [transformedOffset]: as wide
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
