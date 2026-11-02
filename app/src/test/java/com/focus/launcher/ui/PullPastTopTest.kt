package com.focus.launcher.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page that slides up closes when it is pulled down past the top of its list, by a finger and far
 * enough. Scrolling the list, or a fling that happens to reach its top, never closes it.
 */
class PullPastTopTest {
    private val p = PullPastTop(64f)

    @Test fun `a pull past the top closes once it reaches the threshold`() {
        assertFalse(p.onScroll(consumedY = 0f, leftoverY = 40f, byFinger = true))
        assertTrue(p.onScroll(consumedY = 0f, leftoverY = 30f, byFinger = true))
    }

    @Test fun `while the list scrolls nothing builds up`() {
        p.onScroll(0f, 40f, byFinger = true)
        p.onScroll(consumedY = 10f, leftoverY = 0f, byFinger = true)
        assertFalse(p.onScroll(0f, 40f, byFinger = true))
    }

    @Test fun `a fling that reaches the top does not close`() {
        assertFalse(p.onScroll(0f, 500f, byFinger = false))
    }

    @Test fun `a pull up does not count`() {
        assertFalse(p.onScroll(0f, -500f, byFinger = true))
    }

    @Test fun `a new gesture starts from zero`() {
        p.onScroll(0f, 40f, byFinger = true)
        p.reset()
        assertFalse(p.onScroll(0f, 40f, byFinger = true))
    }
}
