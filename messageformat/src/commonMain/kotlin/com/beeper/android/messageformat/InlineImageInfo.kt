package com.beeper.android.messageformat

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.coerceIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

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
    ): Placeholder {
        return if (isEmote || (width == null && height == null)) {
            Placeholder(defaultHeight, defaultHeight, PlaceholderVerticalAlign.TextCenter)
        } else {
            Placeholder(
                width = (width ?: height)?.coerceIn(minWidthSp, maxWidthSp)?.sp ?: defaultHeight,
                height = (height ?: width)?.coerceIn(minHeightSp, maxHeightSp)?.sp ?: defaultHeight,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter,
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
    maxWidth: Dp = 512.dp,
    minHeight: Dp = 16.dp,
    maxHeight: Dp = 512.dp,
    drawContent: @Composable (InlineImageInfo, Modifier) -> Unit
) = mapValues { (_, info) ->
    val placeholder = info.placeholder(
        defaultHeight = defaultHeight,
        minWidthSp = density.run { minWidth.toSp().value }.roundToInt(),
        maxWidthSp = density.run { maxWidth.toSp().value }.roundToInt(),
        minHeightSp = density.run { minHeight.toSp().value }.roundToInt(),
        maxHeightSp = density.run { maxHeight.toSp().value }.roundToInt(),
    )
    InlineTextContent(placeholder) {
        val modifier = when {
            info.isEmote -> Modifier.height(density.run { defaultHeight.toDp() })
            info.width != null && info.height != null -> Modifier.size(
                info.width.spAsDp(density).coerceIn(minWidth, maxWidth),
                info.height.spAsDp(density).coerceIn(minHeight, maxHeight)
            )
            info.width != null -> Modifier.width(info.width.spAsDp(density).coerceIn(minWidth, maxWidth))
            info.height != null -> Modifier.height(info.height.spAsDp(density).coerceIn(minHeight, maxHeight))
            else -> Modifier.height(density.run { defaultHeight.toDp() })
        }
        drawContent(info, modifier)
    }
}

/** Treat this Int as SP but calculate it to DB */
private fun Int.spAsDp(density: Density) = density.run { this@spAsDp.sp.toDp() }
