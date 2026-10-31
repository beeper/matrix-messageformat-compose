package com.beeper.android.messageformat.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Slider
import androidx.compose.material.Switch
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.beeper.android.messageformat.FooterOverlayLayout
import com.beeper.android.messageformat.MatrixBodyDrawStyle
import com.beeper.android.messageformat.MatrixBodyParseResult
import com.beeper.android.messageformat.MatrixHtmlParser
import com.beeper.android.messageformat.MatrixBodyPreFormatStyle
import com.beeper.android.messageformat.MatrixStyledFormattedText
import com.beeper.android.messageformat.StrippedFormattingRenderer
import com.beeper.android.messageformat.markdown.MatrixMarkdownParser
import com.beeper.android.messageformat.markdown.MarkdownFeature
import com.beeper.android.messageformat.toInlineContent
import androidx.compose.material3.TextButton
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextRange
import com.beeper.android.messageformat.composer.MatrixMarkdownTextFieldState
import com.beeper.android.messageformat.composer.MatrixMarkdownTextField
import com.beeper.android.messageformat.markdown.MatrixInlineEntity

fun main() = application {
    Window(onCloseRequest = ::exitApplication, title = "Message Render Playground") {
        MaterialTheme {
            Surface {
                TextRenderScreen()
            }
        }
    }
}

@Composable
fun TextRenderScreen() {
    var renderScale by remember { mutableFloatStateOf(2f) }
    var fontScale by remember { mutableFloatStateOf(1f) }
    var textInput by remember { mutableStateOf(EXAMPLE_MESSAGE) }
    var footerText by remember { mutableStateOf("⏲") }
    var parseResult by remember { mutableStateOf(MatrixBodyParseResult("")) }
    var strippedParseResult by remember { mutableStateOf(MatrixBodyParseResult("")) }
    var allowRoomMention by remember { mutableStateOf(true) }
    var newlineDbg by remember { mutableStateOf(false) }
    var wrapWidth by remember { mutableStateOf(false) }
    var forceWrapWidth by remember { mutableStateOf(false) }
    var inverseLayout by remember { mutableStateOf(false) }
    var autoTextDirection by remember { mutableStateOf(true) }
    var rtlText by remember { mutableStateOf(false) }
    var withFooter by remember { mutableStateOf(false) }
    var showStringAnnotations by remember { mutableStateOf(false) }
    var markdownInput by remember { mutableStateOf(false) }
    var markdownFeatures by remember { mutableStateOf(MarkdownFeature.ALL) }
    val composerState = remember { MatrixMarkdownTextFieldState(EXAMPLE_MARKDOWN) }
    LaunchedEffect(composerState) {
        snapshotFlow { composerState.text.toString() }.collect { if (markdownInput) textInput = it }
    }
    val markdownParser = remember(markdownFeatures) { MatrixMarkdownParser(markdownFeatures) }
    val htmlInput = remember(textInput, markdownInput, markdownParser, composerState.entities) {
        if (markdownInput) markdownParser.toHtml(textInput, composerState.entities) else textInput
    }
    val parser = remember(newlineDbg) { MatrixHtmlParser(newlineDbg = newlineDbg) }
    val renderTextStyle = MaterialTheme.typography.bodyLarge
    val baseDensity = LocalDensity.current
    val density = Density(baseDensity.density * renderScale, fontScale)
    val stringAnnotations = remember(parseResult.text) {
        parseResult.text.getStringAnnotations(0, parseResult.text.length)
    }
    Row(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CompositionLocalProvider(
                LocalDensity provides density,
                LocalLayoutDirection provides if (inverseLayout) {
                    when (LocalLayoutDirection.current) {
                        LayoutDirection.Ltr -> LayoutDirection.Rtl
                        LayoutDirection.Rtl -> LayoutDirection.Ltr
                    }
                } else {
                    LocalLayoutDirection.current
                }
            ) {
                val preFormatStyle = remember {
                    MatrixBodyPreFormatStyle(
                        // For some reason, having anything that's not exactly one character long here, breaks our annotation finding logic on text layout
                        // for JVM targets.
                        formatInlineImageFallback = { "\uFFFD" },
                    )
                }
                val strippedPreFormatStyle = remember {
                    MatrixBodyPreFormatStyle(
                        detailsSummaryIndicatorPlaceholder = "",
                    )
                }
                LaunchedEffect(parser, htmlInput, preFormatStyle, allowRoomMention) {
                    parseResult = parser.parseHtml(
                        htmlInput,
                        preFormatStyle,
                        allowRoomMention = allowRoomMention,
                    )
                }
                LaunchedEffect(parser, htmlInput, strippedPreFormatStyle, allowRoomMention) {
                    strippedParseResult = parser.parseHtml(
                        htmlInput,
                        strippedPreFormatStyle,
                        allowRoomMention = allowRoomMention,
                    )
                }
                Text(
                    text = "String annotations: ${stringAnnotations.size}",
                    modifier = Modifier.clickable { showStringAnnotations = !showStringAnnotations },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (showStringAnnotations) {
                    Column(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(max = 180.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outline)
                            .padding(8.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (stringAnnotations.isEmpty()) {
                            Text("No string annotations")
                        } else {
                            stringAnnotations.forEachIndexed { index, annotation ->
                                Text(
                                    "$index: [${annotation.start}, ${annotation.end}) tag=${annotation.tag} item=${annotation.item}"
                                )
                            }
                        }
                    }
                }
                Text(
                    "Rendered:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = renderTextStyle,
                )
                Column(
                    modifier = Modifier.fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    FooterOverlayLayoutWrapper(
                        textLayoutResult = textLayoutResult,
                        content = {
                            SelectionContainer {
                                MatrixStyledFormattedText(
                                    parseResult = parseResult,
                                    inlineContent = { result: MatrixBodyParseResult ->
                                        result.inlineImages.toInlineContent(
                                            density,
                                            renderTextStyle.fontSize
                                        ) { info, modifier ->
                                            // Playground doesn't have a Matrix client for fetching images, just do a placeholder icon
                                            Image(
                                                Icons.Default.Image,
                                                info.alt ?: info.title,
                                                modifier,
                                            )
                                        }
                                    },
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        textDirection = if (autoTextDirection) {
                                            TextDirection.Content
                                        } else if (rtlText) {
                                            TextDirection.Rtl
                                        } else {
                                            TextDirection.Ltr
                                        }
                                    ),
                                    onTextLayout = { textLayoutResult = it },
                                    drawStyle = remember {
                                        MatrixBodyDrawStyle(
                                            drawBehindUserMention = { mention, pos ->
                                                if (mention.isAutoLink) {
                                                    return@MatrixBodyDrawStyle
                                                }
                                                val rect = pos.rect
                                                val leftRadius =
                                                    this.density * if (pos.leftHasContinuation) {
                                                        2f
                                                    } else {
                                                        8f
                                                    }
                                                val rightRadius =
                                                    this.density * if (pos.rightHasContinuation) {
                                                        2f
                                                    } else {
                                                        8f
                                                    }
                                                drawIntoCanvas {
                                                    val path = Path().apply {
                                                        addRoundRect(
                                                            RoundRect(
                                                                rect = rect,
                                                                topLeft = CornerRadius(
                                                                    leftRadius,
                                                                    leftRadius
                                                                ),
                                                                bottomLeft = CornerRadius(
                                                                    leftRadius,
                                                                    leftRadius
                                                                ),
                                                                topRight = CornerRadius(
                                                                    rightRadius,
                                                                    rightRadius
                                                                ),
                                                                bottomRight = CornerRadius(
                                                                    rightRadius,
                                                                    rightRadius
                                                                ),
                                                            ),
                                                        )
                                                    }
                                                    drawPath(path, color = Color.Blue)
                                                }
                                            }
                                        )
                                    }
                                )
                            }
                        },
                        overlay = {
                            if (withFooter) {
                                Text(footerText)
                            }
                        },
                        forceWrapWidth = forceWrapWidth,
                        modifier = if (wrapWidth) {
                            Modifier.border(2.dp, Color.Gray)
                        } else {
                            Modifier.fillMaxWidth()
                        },
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Text(
                            "Rendered with stripped formatting:",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = renderTextStyle,
                        )
                        SelectionContainer {
                            Text(
                                StrippedFormattingRenderer.formattedContentToPlainString(strippedParseResult),
                                color = MaterialTheme.colorScheme.onSurface,
                                style = renderTextStyle,
                            )
                        }
                    }
                }
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Render scale:")
                Slider(
                    value = renderScale,
                    onValueChange = { renderScale = it },
                    valueRange = 0.5f..4f,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Font scale:")
                Slider(
                    value = fontScale,
                    onValueChange = { fontScale = it },
                    valueRange = 0.5f..4f,
                )
            }
            // Wrap toggles in column to have narrower arrangement
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ToggleRow("Allow room mentions", allowRoomMention) { allowRoomMention = it }
                ToggleRow("Debug newlines", newlineDbg) { newlineDbg = it }
                ToggleRow("Wrap width", wrapWidth) { wrapWidth = it }
                ToggleRow("Force layout wrap width", forceWrapWidth) { forceWrapWidth = it }
                ToggleRow("Inverse layout", inverseLayout) { inverseLayout = it }
                ToggleRow("Auto text direction", autoTextDirection) { autoTextDirection = it }
                ToggleRow("RTL text", rtlText, enabled = !autoTextDirection) { rtlText = it }
                ToggleRow("Footer", withFooter) { withFooter = it }
                ToggleRow("Markdown input", markdownInput) {
                    markdownInput = it
                    if (it) textInput = composerState.text.toString()
                    if (!it && textInput == EXAMPLE_MARKDOWN) textInput = EXAMPLE_MESSAGE
                }
            }
            if (markdownInput) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MarkdownFeature.entries.forEach { feature ->
                        ToggleRow(feature.name.lowercase().replace('_', ' '), feature in markdownFeatures) { enabled ->
                            markdownFeatures = if (enabled) markdownFeatures + feature else markdownFeatures - feature
                        }
                    }
                }
            }
            Text(
                "Input:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (markdownInput) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        val cursor = composerState.textFieldState.selection
                        composerState.insertEntity(TextRange(cursor.start, cursor.end), "@Alice") { start, end ->
                            MatrixInlineEntity.UserMention(start, end, "@alice:example.org")
                        }
                    }) { Text("Insert mention") }
                    TextButton(onClick = {
                        val cursor = composerState.textFieldState.selection
                        composerState.insertEntity(TextRange(cursor.start, cursor.end), ":example_emote:") { start, end ->
                            MatrixInlineEntity.CustomEmote(start, end, "example_emote", "mxc://local.beeper.com/spiritcroc_TnTnE9q1fOXD0gtA9UlcBrStvCVLWwAbAtKcAIOKqWJRtQeZwsniGSRVA8xV51tB")
                        }
                    }) { Text("Insert custom emote") }
                }
                MatrixMarkdownTextField(
                    state = composerState,
                    features = markdownFeatures,
                    inlineContent = { result ->
                        result.inlineImages.toInlineContent(LocalDensity.current, MaterialTheme.typography.bodyLarge.fontSize) { info, imageModifier ->
                            Image(Icons.Default.Image, info.alt ?: info.title, imageModifier)
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxSize().weight(1f)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .padding(8.dp),
                )
            } else {
                TextField(
                    value = textInput,
                    onValueChange = { textInput = it },
                    modifier = Modifier.fillMaxSize().weight(1f),
                )
            }
            if (markdownInput) {
                Text(
                    "Generated HTML:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                SelectionContainer(
                    Modifier.fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(htmlInput, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (withFooter) {
                Text(
                    "Footer:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                TextField(
                    value = footerText,
                    onValueChange = { footerText = it },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(
    text: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckChange: (Boolean) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable(enabled = enabled) {
            onCheckChange(!checked)
        }
    ) {
        Switch(checked = checked, onCheckedChange = onCheckChange, enabled = enabled)
        Text(text)
    }
}

/** Wrapper that drops the overlay while [textLayoutResult] is null */
@Composable
fun FooterOverlayLayoutWrapper(
    textLayoutResult: TextLayoutResult?,
    horizontalPadding: Dp = 8.dp,
    verticalPadding: Dp = 8.dp,
    content: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    forceWrapWidth: Boolean = false,
) {
    if (textLayoutResult == null) {
        Box(modifier) {
            content()
        }
    } else {
        FooterOverlayLayout(
            textLayoutResult = textLayoutResult,
            horizontalPadding = horizontalPadding,
            verticalPadding = verticalPadding,
            content = content,
            overlay = overlay,
            modifier = modifier,
            forceWrapWidth = forceWrapWidth,
        )
    }
}
