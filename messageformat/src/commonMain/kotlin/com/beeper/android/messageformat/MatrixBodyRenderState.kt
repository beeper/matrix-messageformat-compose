package com.beeper.android.messageformat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import co.touchlab.kermit.Logger
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.max

class MatrixFormatOnTextRenderResult(
    internal val userMentions: ImmutableList<Pair<String, DrawPosition.InLine>>,
    internal val roomMentions: ImmutableList<DrawPosition.InLine>,
    internal val blockQuotes: ImmutableList<Pair<Int, DrawPosition.Block>>,
    internal val inlineCode: ImmutableList<DrawPosition.InLine>,
    internal val blockCode: ImmutableList<DrawPosition.Block>,
    internal val spans: ImmutableList<Pair<SpanAttributes, DrawPosition.InLine>>,
    internal val fullDetailsSummaries: ImmutableList<Pair<Int, DrawPosition.Block>>,
    internal val detailsSummariesFirstLines: ImmutableList<Pair<Int, DrawPosition.InLine>>,
    internal val detailsContents: ImmutableList<Pair<Int, DrawPosition.Block>>,
)

@Stable
data class MatrixFormatInteractionState(
    /** IDs of all expandable items contained in [com.beeper.android.messageformat.MatrixBodyRenderState.fullText]. */
    val expandableItems: Set<Int>,
    /** IDs of expandable items that are currently visible. */
    val expandedItems: MutableState<Set<Int>>,
)

@Stable
data class MatrixBodyRenderState(
    /** Fully formatted & styled text, including applied render state. */
    val text: AnnotatedString,
    /** Post-processing style, for use in [matrixBodyDrawWithContent]. */
    val style: MatrixBodyDrawStyle,
    /** Render result of [MatrixBodyRenderState.text] in an actual [androidx.compose.material3.Text]. */
    val renderResult: MutableState<MatrixFormatOnTextRenderResult?>,
)

@Composable
fun rememberMatrixFormatInteractionState(parseResult: MatrixBodyParseResult): MatrixFormatInteractionState {
    val expandedItems = remember(parseResult.expandableItems) { mutableStateOf(setOf<Int>()) }
    return remember(expandedItems, parseResult.expandableItems) {
        MatrixFormatInteractionState(
            expandableItems = parseResult.expandableItems,
            expandedItems = expandedItems,
        )
    }
}

@Composable
fun rememberMatrixFormatRenderState(
    styledText: AnnotatedString,
    style: MatrixBodyDrawStyle,
    interactionState: MatrixFormatInteractionState,
): MatrixBodyRenderState {
    val currentExpanded = interactionState.expandedItems.value
    val text = remember(styledText, currentExpanded) {
        if (interactionState.expandableItems.all { it in currentExpanded }) {
            styledText
        } else {
            styledText.applyFormatRenderState(currentExpanded)
        }
    }
    val renderResult = remember(text) { mutableStateOf<MatrixFormatOnTextRenderResult?>(null) }
    return remember(text, style, renderResult) {
        MatrixBodyRenderState(
            text = text,
            style = style,
            renderResult = renderResult,
        )
    }
}

private fun AnnotatedString.applyFormatRenderState(expandedItems: Set<Int>): AnnotatedString {
    val ts = System.currentTimeMillis()
    // Just strip details tags contents.
    val detailContents = getStringAnnotations(MatrixBodyAnnotations.DETAILS_CONTENT, 0, length)
        .sortedBy { it.start }
    if (detailContents.isEmpty()) {
        return this
    }
    val input = this
    return buildAnnotatedString {
        var index = 0
        for (range in detailContents) {
            if (range.item.toIntOrNull() in expandedItems) {
                continue
            }
            if (index > range.start) {
                // Already covered??
                continue
            }
            // Not expanded, skip this content
            append(input.subSequence(index, range.start))
            index = max(index, range.end)
        }
        append(input.subSequence(index, input.length))
    }.also {
        if (VERBOSE_DBG) {
            val now = System.currentTimeMillis()
            Logger.withTag("MatrixBodyFormatRenderState").v("Applied state in ${now - ts} ms; len=${this.length}->${it.length}")
        }
    }
}
