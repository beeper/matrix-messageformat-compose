package com.beeper.android.messageformat

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation

/**
 * Util [Modifier] to intercept touches to detect link longpress actions,
 * since link annotations do not support this by themselves but like to consume all touch events
 * anyway.
 */
fun Modifier.linkLongPress(
    state: MatrixBodyRenderState,
    onOtherLongPress: (() -> Unit)? = null,
    onLinkClick: (LinkAnnotation) -> Unit,
    onLinkLongPress: (LinkAnnotation) -> Unit,
) = pointerInput(state.renderResult, onLinkClick, onLinkLongPress, onOtherLongPress) {
    awaitEachGesture {
        // Link annotations consume touches, but we need to get them anyway, so we need the initial pass
        val down = awaitFirstDown(pass = PointerEventPass.Initial)
        val link = state.findLinkAt(down.position)
        if (link == null && onOtherLongPress == null) {
            // Nothing to do
            return@awaitEachGesture
        }
        if (link != null) {
            down.consume()
            when (val result = awaitLinkGestureResult(down.id, down.position)) {
                is LinkGestureResult.Click -> if (state.isPointerStillOnLink(link, result.position)) {
                    onLinkClick(link.item)
                }
                is LinkGestureResult.LongPress -> {
                    if (state.isPointerStillOnLink(link, result.position)) {
                        onLinkLongPress(link.item)
                    } else {
                        onOtherLongPress?.invoke()
                    }
                    consumeUntilUp(down.id)
                }
            }
            return@awaitEachGesture
        }
        if (awaitUnconsumedLongPress(down.id)) {
            onOtherLongPress?.invoke()
            consumeUntilUp(down.id)
        }
    }
}

private sealed interface LinkGestureResult {
    data class Click(val position: Offset) : LinkGestureResult
    data class LongPress(val position: Offset) : LinkGestureResult
}

private fun MatrixBodyRenderState.findLinkAt(position: Offset): AnnotatedString.Range<LinkAnnotation>? {
    val textPosition = renderResult.value?.textLayoutResult?.getOffsetForPosition(position) ?: return null
    return text.getLinkAnnotations(textPosition, textPosition).firstOrNull()
}

private fun MatrixBodyRenderState.isPointerStillOnLink(
    link: AnnotatedString.Range<LinkAnnotation>,
    position: Offset,
): Boolean {
    return findLinkAt(position) == link
}

private suspend fun AwaitPointerEventScope.awaitLinkGestureResult(
    pointerId: PointerId,
    initialPosition: Offset,
): LinkGestureResult {
    var lastPointerPosition = initialPosition
    val releasedBeforeLongPress = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
            event.changes.forEach { it.consume() }
            val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
            lastPointerPosition = change.position
            if (!change.pressed) {
                return@withTimeoutOrNull true
            }
        }
    } == true
    return if (releasedBeforeLongPress) {
        LinkGestureResult.Click(lastPointerPosition)
    } else {
        LinkGestureResult.LongPress(lastPointerPosition)
    }
}

private suspend fun AwaitPointerEventScope.awaitUnconsumedLongPress(pointerId: PointerId): Boolean {
    val longPressCanceled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
        while (true) {
            val initialEvent = awaitPointerEvent(pass = PointerEventPass.Initial)
            val initialChange = initialEvent.changes.firstOrNull { it.id == pointerId } ?: continue
            if (!initialChange.pressed) {
                return@withTimeoutOrNull true
            }

            // Let other recognizers arbitrate the gesture and cancel this long-press if they consume.
            val finalEvent = awaitPointerEvent(pass = PointerEventPass.Final)
            val finalChange = finalEvent.changes.firstOrNull { it.id == pointerId } ?: continue
            if (finalChange.isConsumed) {
                return@withTimeoutOrNull true
            }
        }
    } == true
    return !longPressCanceled
}

private suspend fun AwaitPointerEventScope.consumeUntilUp(pointerId: PointerId) {
    var pointerStillDown: Boolean
    do {
        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
        event.changes.forEach { it.consume() }
        val change = event.changes.firstOrNull { it.id == pointerId }
        pointerStillDown = change?.pressed == true
    } while (pointerStillDown)
}
