package com.beeper.android.messageformat

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Wraps [content] in a [BoxWithConstraints] only when [enabled], avoiding the
 * subcomposition and extra layout node cost when the max width is not needed.
 * [content] receives the incoming max width, or null when [enabled] is false.
 */
@Composable
fun ConditionalBoxWithConstraints(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier, maxWidth: Dp?) -> Unit,
) {
    if (enabled) {
        BoxWithConstraints(modifier) {
            content(Modifier, this.maxWidth)
        }
    } else {
        content(modifier, null)
    }
}
