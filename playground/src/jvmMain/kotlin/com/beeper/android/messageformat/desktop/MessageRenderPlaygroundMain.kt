package com.beeper.android.messageformat.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
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
    var parseResult by remember { mutableStateOf(MatrixBodyParseResult("")) }
    var allowRoomMention by remember { mutableStateOf(true) }
    var newlineDbg by remember { mutableStateOf(false) }
    val parser = remember(newlineDbg) { MatrixHtmlParser(newlineDbg = newlineDbg) }
    val renderTextStyle = MaterialTheme.typography.bodyLarge
    val baseDensity = LocalDensity.current
    val density = Density(baseDensity.density * renderScale, fontScale)
    Row(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CompositionLocalProvider(
                LocalDensity provides density
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
                    "Rendered:",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = renderTextStyle,
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
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
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Allow room mentions:")
                Switch(checked = allowRoomMention, onCheckedChange = { allowRoomMention = it})
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Debug newlines:")
                Switch(checked = newlineDbg, onCheckedChange = { newlineDbg = it})
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
        }
    }
}
