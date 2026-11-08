package com.beeper.android.messageformat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private class MeasuredCodeBlock(
    val id: String,
    val start: Int,
    val end: Int,
    val lines: List<AnnotatedString.Range<String>>,
    val contentWidth: Int,
    val lineHeight: Int,
)

private class CodeBlockLayout(
    val block: MeasuredCodeBlock,
    val placeholder: Placeholder,
)

@Composable
fun Map<String, InlineCodeBlockInfo>.toCodeBlockInlineContent(
    text: AnnotatedString,
    maxWidth: Dp,
    style: TextStyle,
    textColor: Color,
): Map<String, InlineTextContent> {
    if (isEmpty()) {
        return emptyMap()
    }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val measuredBlocks = remember(this, text, style, density, textMeasurer) {
        measureCodeBlocks(text, style, density, textMeasurer)
    }
    val overflowingBlocks = remember(measuredBlocks, text, maxWidth, style, density) {
        measuredBlocks.layoutOverflowing(text, maxWidth, style, density)
    }
    return buildMap {
        overflowingBlocks.forEach { layout ->
            val scrollState = key(layout.block.id) { rememberScrollState() }
            val contentWidth = with(density) { layout.block.contentWidth.toDp() }
            layout.block.lines.forEach { line ->
                put(line.item, InlineTextContent(layout.placeholder) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .clearAndSetSemantics {}
                                .horizontalScroll(scrollState, overscrollEffect = null)
                        ) {
                            Text(
                                text = text.codeLine(line),
                                modifier = Modifier.width(contentWidth),
                                style = style,
                                color = textColor,
                                softWrap = false,
                                maxLines = 1,
                            )
                        }
                    }
                })
            }
        }
    }
}

fun Map<String, InlineCodeBlockInfo>.codeBlockPlaceholders(
    text: AnnotatedString,
    maxWidth: Dp,
    style: TextStyle,
    density: Density,
    textMeasurer: TextMeasurer,
): List<AnnotatedString.Range<Placeholder>> {
    return measureCodeBlocks(text, style, density, textMeasurer)
        .layoutOverflowing(text, maxWidth, style, density)
        .flatMap { layout ->
            layout.block.lines.map { AnnotatedString.Range(layout.placeholder, it.start, it.end) }
        }
}

private fun Map<String, InlineCodeBlockInfo>.measureCodeBlocks(
    text: AnnotatedString,
    style: TextStyle,
    density: Density,
    textMeasurer: TextMeasurer,
): List<MeasuredCodeBlock> {
    if (isEmpty()) {
        return emptyList()
    }
    return text.getStringAnnotations(MatrixBodyAnnotations.BLOCK_CODE, 0, text.length).mapNotNull { block ->
        val lineIds = this[block.item]?.lines?.toSet() ?: return@mapNotNull null
        val lines = text.getStringAnnotations(MatrixBodyAnnotations.BLOCK_CODE_LINE, block.start, block.end)
            .filter { it.item in lineIds }
        if (lines.isEmpty()) {
            return@mapNotNull null
        }
        val blockText = text.subSequence(block.start, block.end).let { AnnotatedString(it.text, it.spanStyles) }
        MeasuredCodeBlock(
            id = block.item,
            start = block.start,
            end = block.end,
            lines = lines,
            contentWidth = textMeasurer.measure(blockText, style, softWrap = false, density = density).size.width,
            lineHeight = textMeasurer.measure(text.codeLine(lines.first()), style, softWrap = false, maxLines = 1, density = density).size.height,
        )
    }
}

private fun List<MeasuredCodeBlock>.layoutOverflowing(
    text: AnnotatedString,
    maxWidth: Dp,
    style: TextStyle,
    density: Density,
): List<CodeBlockLayout> = mapNotNull { block ->
    val viewportWidth = with(density) {
        maxWidth.toPx() - text.indentionFor(block.start, block.end, style, density) - PLACEHOLDER_WIDTH_EPSILON_SP.sp.toPx()
    }
    if (block.contentWidth <= viewportWidth) {
        null
    } else {
        CodeBlockLayout(
            block = block,
            placeholder = with(density) {
                Placeholder(
                    width = viewportWidth.coerceAtLeast(0f).toSp(),
                    height = block.lineHeight.toSp(),
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center,
                )
            },
        )
    }
}

private fun AnnotatedString.codeLine(line: AnnotatedString.Range<String>): AnnotatedString {
    val content = subSequence(line.start, line.end)
    return AnnotatedString(content.text, content.spanStyles)
}

private fun AnnotatedString.indentionFor(
    start: Int,
    end: Int,
    style: TextStyle,
    density: Density,
): Float {
    val textIndent = paragraphStyles
        .filter { it.start <= start && end <= it.end }
        .minByOrNull { it.end - it.start }
        ?.item?.textIndent
        ?: return 0f
    return maxOf(textIndent.firstLine.toPx(style, density), textIndent.restLine.toPx(style, density))
}

private fun TextUnit.toPx(style: TextStyle, density: Density): Float = with(density) {
    when {
        isSp -> toPx()
        isEm && style.fontSize.isSp -> value * style.fontSize.toPx()
        else -> 0f
    }
}
