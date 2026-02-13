package com.beeper.android.messageformat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit

@Composable
fun MatrixStyledFormattedText(
    parseResult: MatrixBodyParseResult,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    drawStyle: MatrixBodyDrawStyle = remember { MatrixBodyDrawStyle() },
    interactionState: MatrixFormatInteractionState = rememberMatrixFormatInteractionState(parseResult),
    formatter: MatrixBodyStyledFormatter = defaultMatrixBodyStyledFormatter(style),
    color: Color = Color.Unspecified,
    autoSize: TextAutoSize? = null,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent>,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    onLinkLongPress: ((LinkAnnotation) -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
) {
    val styledText = remember(formatter, parseResult, interactionState) {
        formatter.applyStyle(parseResult, interactionState)
    }
    val renderState = rememberMatrixFormatRenderState(styledText, drawStyle, interactionState)
    Text(
        text = renderState.text,
        modifier = modifier.matrixBodyDrawWithContent(
            state = renderState,
            interactionState = interactionState,
        ).then(
            if (onLinkLongPress != null) {
                Modifier.linkLongPress(
                    state = renderState,
                    onLinkLongPress = onLinkLongPress,
                    onOtherLongPress = onLongPress,
                ).then(
                    if (onClick != null)
                        Modifier.clickable(onClick = onClick)
                    else
                        Modifier
                )
            } else if (onLongPress != null) {
                Modifier.combinedClickable(
                    onClick = onClick ?: {},
                    onLongClick = onLongPress,
                )
            } else if (onClick != null) {
                modifier.clickable(onClick = onClick)
            } else {
                Modifier
            }
        ),
        style = style,
        color = color,
        autoSize = autoSize,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        inlineContent = inlineContent,
        onTextLayout = onTextLayout?.let {{
            renderState.onMatrixBodyLayout(it)
            onTextLayout(it)
        }} ?: renderState::onMatrixBodyLayout,
    )
}

@Composable
fun defaultMatrixBodyStyledFormatter(textStyle: TextStyle = LocalTextStyle.current): DefaultMatrixBodyStyledFormatter {
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val urlHandler = LocalUriHandler.current
    return remember(density, textMeasurer, textStyle, urlHandler) {
        DefaultMatrixBodyStyledFormatter(
            density = density,
            textMeasurer = textMeasurer,
            textStyle = textStyle,
            handleWebLinkClick = urlHandler::openUri,
        )
    }
}
