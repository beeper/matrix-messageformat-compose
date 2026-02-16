package com.beeper.android.messageformat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.LinkAnnotation

/**
 * Util [Modifier] to intercept touches to detect link longpress actions,
 * since link annotations do not support this by themselves but like to consume all touch events
 * anyway.
 */
fun Modifier.linkLongPress(
    state: MatrixBodyRenderState,
    onOtherLongPress: (() -> Unit)? = null,
    onLinkLongPress: (LinkAnnotation) -> Unit,
) = pointerInput(state.renderResult, onLinkLongPress, onOtherLongPress) {
    awaitEachGesture {
        // Link annotations consume touches, but we need to get them anyway, so we need the initial pass
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val link = state.renderResult.value?.textLayoutResult
            ?.getOffsetForPosition(down.position)?.let { position ->
                state.text.getLinkAnnotations(position, position).firstOrNull()
            }
        if (link == null && onOtherLongPress == null) {
            // Nothing to do
            return@awaitEachGesture
        }
        var lastPointerPosition = down.position
        val longPressCanceled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val initialEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
                val initialChange = initialEvent.changes.firstOrNull { it.id == down.id } ?: continue
                lastPointerPosition = initialChange.position
                if (!initialChange.pressed) {
                    return@withTimeoutOrNull true
                }

                // Let other recognizers arbitrate the gesture and cancel this long-press if they consume.
                val finalEvent = awaitPointerEvent(pass = PointerEventPass.Final)
                val finalChange = finalEvent.changes.firstOrNull { it.id == down.id } ?: continue
                if (finalChange.isConsumed) {
                    return@withTimeoutOrNull true
                }
            }
        } == true
        if (!longPressCanceled) {
            // Check the touch input wasn't dragged out of the link - otherwise do generic longpress
            val linkCheck = state.renderResult.value?.textLayoutResult?.getOffsetForPosition(lastPointerPosition)
            if (linkCheck == null || state.text.getLinkAnnotations(linkCheck, linkCheck).none { it == link }) {
                onOtherLongPress?.invoke()
            } else {
                link?.item?.let(onLinkLongPress) ?: onOtherLongPress?.invoke()
            }
            var pointerStillDown: Boolean
            do {
                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
                val change = event.changes.firstOrNull { it.id == down.id }
                pointerStillDown = change?.pressed == true
            } while (pointerStillDown)
        }
    }
}
