package com.beeper.android.messageformat

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

private class CodeBlockLayout(
    val id: String,
    val lines: List<AnnotatedString.Range<String>>,
    val contentWidth: Int,
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
    val overflowingBlocks = remember(this, text, maxWidth, style, density, textMeasurer) {
        overflowingCodeBlocks(text, maxWidth, style, density, textMeasurer)
    }
    return buildMap {
        overflowingBlocks.forEach { block ->
            val scrollState = key(block.id) { rememberScrollState() }
            val contentWidth = with(density) { block.contentWidth.toDp() }
            block.lines.forEach { line ->
                put(line.item, InlineTextContent(block.placeholder) {
                    Box(Modifier.fillMaxSize().horizontalScroll(scrollState, overscrollEffect = null)) {
                        Text(
                            text = text.codeLine(line),
                            modifier = Modifier.width(contentWidth),
                            style = style,
                            color = textColor,
                            softWrap = false,
                            maxLines = 1,
                        )
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
    return overflowingCodeBlocks(text, maxWidth, style, density, textMeasurer).flatMap { block ->
        block.lines.map { AnnotatedString.Range(block.placeholder, it.start, it.end) }
    }
}

private fun Map<String, InlineCodeBlockInfo>.overflowingCodeBlocks(
    text: AnnotatedString,
    maxWidth: Dp,
    style: TextStyle,
    density: Density,
    textMeasurer: TextMeasurer,
): List<CodeBlockLayout> {
    if (isEmpty()) {
        return emptyList()
    }
    return text.getStringAnnotations(MatrixBodyAnnotations.BLOCK_CODE, 0, text.length).mapNotNull { block ->
        val info = this[block.item] ?: return@mapNotNull null
        val lines = text.getStringAnnotations(MatrixBodyAnnotations.BLOCK_CODE_LINE, block.start, block.end)
            .filter { it.item in info.lines }
        if (lines.isEmpty()) {
            return@mapNotNull null
        }
        val measuredLines = lines.map {
            textMeasurer.measure(text.codeLine(it), style, softWrap = false, maxLines = 1, density = density)
        }
        val contentWidth = measuredLines.maxOf { it.size.width }
        val viewportWidth = with(density) {
            maxWidth.toPx() - text.indentionFor(block, style, density) - PLACEHOLDER_WIDTH_EPSILON_SP.sp.toPx()
        }
        if (contentWidth <= viewportWidth) {
            return@mapNotNull null
        }
        CodeBlockLayout(
            id = block.item,
            lines = lines,
            contentWidth = contentWidth,
            placeholder = with(density) {
                Placeholder(
                    width = viewportWidth.coerceAtLeast(0f).toSp(),
                    height = measuredLines.maxOf { it.size.height }.toSp(),
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
    block: AnnotatedString.Range<String>,
    style: TextStyle,
    density: Density,
): Float {
    val textIndent = paragraphStyles
        .filter { it.start <= block.start && block.end <= it.end }
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
