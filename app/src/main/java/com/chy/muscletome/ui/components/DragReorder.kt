package com.chy.muscletome.ui.components

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Long-press drag reordering for LazyColumn — no external dependency.
 *
 * Usage: create with [rememberDragReorderState] (give it the list state plus
 * an `onMove` that reorders your in-memory list and an `onDrop` that
 * persists the new order), pass the list state to your LazyColumn, and apply
 * [dragReorderItem] to each item row with the item's LazyColumn key.
 *
 * The dragged item floats above the list (zIndex + translation); crossing a
 * neighbor's midpoint swaps positions in memory; the list autoscrolls near
 * the viewport edges. Order is only persisted on drop.
 */
class DragReorderState internal constructor(
    private val listState: LazyListState,
    private val scope: CoroutineScope,
    private val onMove: (Int, Int) -> Unit,
    private val onDrop: () -> Unit,
) {
    var draggingKey by mutableStateOf<Any?>(null)
        private set
    var dragOffset by mutableFloatStateOf(0f)
        private set

    fun start(key: Any) {
        if (draggingKey != null) return
        draggingKey = key
        dragOffset = 0f
    }

    fun dragBy(delta: Float) {
        dragOffset += delta
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == draggingKey } ?: return
        val position = visible.indexOf(dragged)

        // Autoscroll when the dragged row nears either viewport edge.
        val itemTop = dragged.offset + dragOffset
        val itemBottom = dragged.offset + dragged.size + dragOffset
        val info = listState.layoutInfo
        if (itemBottom > info.viewportEndOffset - 64) {
            scope.launch { listState.scrollBy(12f) }
        } else if (itemTop < info.viewportStartOffset + 64) {
            scope.launch { listState.scrollBy(-12f) }
        }

        // Swap once the dragged edge passes a neighbor's midpoint; shifting
        // the offset by the neighbor's size keeps the finger tracking.
        if (dragOffset > 0 && position < visible.lastIndex) {
            val below = visible[position + 1]
            if (itemBottom > below.offset + below.size / 2) {
                onMove(dragged.index, below.index)
                dragOffset -= below.size
            }
        } else if (dragOffset < 0 && position > 0) {
            val above = visible[position - 1]
            if (itemTop < above.offset + above.size / 2) {
                onMove(dragged.index, above.index)
                dragOffset += above.size
            }
        }
    }

    fun end() {
        if (draggingKey == null) return
        draggingKey = null
        dragOffset = 0f
        onDrop()
    }
}

@Composable
fun rememberDragReorderState(
    listState: LazyListState,
    onMove: (Int, Int) -> Unit,
    onDrop: () -> Unit,
): DragReorderState {
    val scope = rememberCoroutineScope()
    return remember { DragReorderState(listState, scope, onMove, onDrop) }
}

/** Applies to an item row: floats it while dragging and handles the gestures. */
fun Modifier.dragReorderItem(state: DragReorderState, key: Any): Modifier =
    this
        .zIndex(if (state.draggingKey == key) 1f else 0f)
        .graphicsLayer {
            translationY = if (state.draggingKey == key) state.dragOffset else 0f
        }
        .pointerInput(state) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.start(key) },
                onDrag = { change, amount ->
                    change.consume()
                    state.dragBy(amount.y)
                },
                onDragEnd = { state.end() },
                onDragCancel = { state.end() },
            )
        }
