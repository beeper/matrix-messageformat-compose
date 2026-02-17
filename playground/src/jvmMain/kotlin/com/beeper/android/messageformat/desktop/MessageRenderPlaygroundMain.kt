package com.beeper.android.messageformat.desktop

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.Color
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
import com.beeper.android.messageformat.MatrixBodyParseResult
import com.beeper.android.messageformat.MatrixHtmlParser
import com.beeper.android.messageformat.MatrixBodyPreFormatStyle
import com.beeper.android.messageformat.MatrixStyledFormattedText
import com.beeper.android.messageformat.toInlineContent

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
    var allowRoomMention by remember { mutableStateOf(true) }
    var newlineDbg by remember { mutableStateOf(false) }
    var wrapWidth by remember { mutableStateOf(false) }
    var forceWrapWidth by remember { mutableStateOf(false) }
    var inverseLayout by remember { mutableStateOf(false) }
    var rtlText by remember { mutableStateOf(false) }
    var withFooter by remember { mutableStateOf(false) }
    var showStringAnnotations by remember { mutableStateOf(false) }
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
                LaunchedEffect(parser, textInput, preFormatStyle, allowRoomMention) {
                    parseResult = parser.parseHtml(
                        textInput,
                        preFormatStyle,
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
                Box(
                    modifier = Modifier.fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    FooterOverlayLayoutWrapper(
                        textLayoutResult = textLayoutResult,
                        content = {
                            MatrixStyledFormattedText(
                                parseResult = parseResult,
                                inlineContent = parseResult.inlineImages.toInlineContent(density, renderTextStyle.fontSize) { info, modifier ->
                                    // Playground doesn't have a Matrix client for fetching images, just do a placeholder icon
                                    Image(
                                        Icons.Default.Image,
                                        info.alt ?: info.title,
                                        modifier,
                                    )
                                },
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDirection = if (rtlText) {
                                        TextDirection.Rtl
                                    } else {
                                        TextDirection.Ltr
                                    }
                                ),
                                onTextLayout = { textLayoutResult = it },
                            )
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
                ToggleRow("RTL text", rtlText) { rtlText = it }
                ToggleRow("Footer", withFooter) { withFooter = it }
            }
            Text(
                "Input:",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            TextField(
                value = textInput,
                onValueChange = { textInput = it },
                modifier = Modifier.fillMaxSize().weight(1f),
            )
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
private fun ToggleRow(text: String, checked: Boolean, onCheckChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            onCheckChange(!checked)
        }
    ) {
        Switch(checked = checked, onCheckedChange = onCheckChange)
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
