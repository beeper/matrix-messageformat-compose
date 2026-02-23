package com.beeper.android.messageformat

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import kotlin.ranges.coerceIn

/**
 * Metadata of expected inline images.
 */
data class InlineImageInfo(
    /** The mxc URL of the inline image */
    val uri: String,
    /** Whether the data-mx-emoticon attribute is set (value doesn't matter) via MSC-2545 */
    val isEmote: Boolean,
    /** Desired width */
    val width: Int?,
    /** Desired height */
    val height: Int?,
    /** Shortcode for custom emotes */
    val title: String?,
    /** Description */
    val alt: String?,
) {
    fun placeholder(
        defaultHeight: TextUnit,
        minWidthSp: Int,
        maxWidthSp: Int,
        minHeightSp: Int,
        maxHeightSp: Int,
        placeholderVerticalAlign: PlaceholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
    ): Placeholder {
        return if (isEmote || (width == null && height == null)) {
            Placeholder(defaultHeight, defaultHeight, PlaceholderVerticalAlign.TextCenter)
        } else {
            Placeholder(
                width = (width ?: height)?.coerceIn(minWidthSp, maxWidthSp)?.sp ?: defaultHeight,
                height = (height ?: width)?.coerceIn(minHeightSp, maxHeightSp)?.sp ?: defaultHeight,
                placeholderVerticalAlign = placeholderVerticalAlign,
            )
        }
    }
}

/**
 * Maps [InlineImageInfo] to actual [InlineTextContent] for use with Text composables,
 * when provided client-specific rendering instructions for each inline image.
 */
fun Map<String, InlineImageInfo>.toInlineContent(
    density: Density,
    defaultHeight: TextUnit,
    minWidth: Dp = 16.dp,
    maxWidth: Dp = 256.dp,
    minHeight: Dp = 16.dp,
    maxHeight: Dp = 256.dp,
    actualImageSizes: Map<String, IntSize> = emptyMap(),
    placeholderVerticalAlign: PlaceholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
    drawContent: @Composable (InlineImageInfo, Modifier) -> Unit
) = mapValues { (_, info) ->
    val rawSize = actualImageSizes[info.uri]
    val placeholder = if (rawSize != null && !info.isEmote) {
        val ratio = rawSize.width.toFloat() / rawSize.height
        var renderWidth = (info.width ?: rawSize.width).dp.coerceIn(minWidth, maxWidth)
        var renderHeight = (info.height ?: rawSize.height).dp.coerceIn(minHeight, maxHeight)
        if (renderHeight.value < rawSize.height) {
            renderWidth = renderHeight * ratio
        } else if (renderWidth.value < rawSize.width) {
            renderHeight = renderWidth / ratio
        } else if (info.width != null && info.height != null) {
            // Still do sanity recalculation to correct possible wrong image ratios
            renderWidth = renderHeight * ratio
        }
        Placeholder(
            width = density.run { renderWidth.toSp() },
            height = density.run { renderHeight.toSp() },
            placeholderVerticalAlign = placeholderVerticalAlign,
        )
    } else {
        info.placeholder(
            defaultHeight = defaultHeight,
            minWidthSp = density.run { minWidth.toSp().value }.roundToInt(),
            maxWidthSp = density.run { maxWidth.toSp().value }.roundToInt(),
            minHeightSp = density.run { minHeight.toSp().value }.roundToInt(),
            maxHeightSp = density.run { maxHeight.toSp().value }.roundToInt(),
            placeholderVerticalAlign = placeholderVerticalAlign,
        )
    }
    InlineTextContent(placeholder) {
        val modifier = when {
            info.isEmote -> Modifier.height(density.run { defaultHeight.toDp() })
            info.width != null && info.height != null -> Modifier.size(
                info.width.spAsDp(density).coerceIn(minWidth, maxWidth),
                info.height.spAsDp(density).coerceIn(minHeight, maxHeight)
            )
            info.width != null -> Modifier.width(info.width.spAsDp(density).coerceIn(minWidth, maxWidth))
            info.height != null -> Modifier.height(info.height.spAsDp(density).coerceIn(minHeight, maxHeight))
            else -> Modifier.sizeIn(
                minWidth = minWidth,
                maxWidth = maxWidth,
                minHeight = minHeight,
                maxHeight = maxHeight,
            )
        }
        drawContent(info, modifier)
    }
}

/** Treat this Int as SP but calculate it to DP */
private fun Int.spAsDp(density: Density) = density.run { this@spAsDp.sp.toDp() }
