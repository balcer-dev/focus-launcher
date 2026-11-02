package com.focus.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity

/**
 * Whether the page under swipe up is slid up, and how it was last closed. In sight (back, Home,
 * a pull) it slides away. Out of sight (the launcher left) it is gone at once: Compose holds back
 * frames while the launcher is stopped, so a sliding close would play only on the way back, over
 * the home screen. [generation] changes then, and the page is started afresh, already closed.
 */
internal class SlideUpState {
    var isOpen by mutableStateOf(false)
        private set

    /** Changes on every close that must not be seen: key the page on it. */
    var generation by mutableIntStateOf(0)
        private set

    /** While it slides away it is still drawn, mostly see-through: a tap meant for the home screen must not open an app on it. */
    val takesTaps: Boolean get() = isOpen

    fun open() {
        isOpen = true
    }

    fun close() {
        isOpen = false
    }

    fun closeAtOnce() {
        if (!isOpen) return
        isOpen = false
        generation++
    }
}

/**
 * Sums how far a finger pulls down past the top of a list, and says when that has reached
 * [threshold]. Whatever the list scrolls itself starts it from zero, and so does a fling: only a
 * finger pulling at a list already at its top counts.
 */
internal class PullPastTop(private val threshold: Float) {
    private var pulled = 0f

    /**
     * One step of a scroll: [consumedY] is what the list took, [leftoverY] what it could not (positive
     * when the finger moves down at the top). True once the pull has reached the threshold.
     */
    fun onScroll(consumedY: Float, leftoverY: Float, byFinger: Boolean): Boolean {
        if (consumedY != 0f) {
            pulled = 0f
            return false
        }
        if (!byFinger) return false
        pulled = (pulled + leftoverY).coerceAtLeast(0f)
        if (pulled < threshold) return false
        pulled = 0f
        return true
    }

    fun reset() {
        pulled = 0f
    }
}

/** Closes the page it wraps once a finger pulls down past the top of the list inside by [threshold] pixels. */
internal class PullDownToClose(threshold: Float, private val onClose: () -> Unit) : NestedScrollConnection {
    private val pull = PullPastTop(threshold)

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (pull.onScroll(consumed.y, available.y, byFinger = source == NestedScrollSource.UserInput)) onClose()
        return Offset.Zero
    }

    // The finger was lifted: the next pull starts from zero.
    override suspend fun onPreFling(available: Velocity): Velocity {
        pull.reset()
        return Velocity.Zero
    }
}
