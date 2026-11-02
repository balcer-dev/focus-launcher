package com.focus.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {
    private val keys = listOf("a", "b", "c")

    @Test fun `an app moves one place up or down`() {
        assertEquals(listOf("b", "a", "c"), keys.movedAmong(keys, "b", -1))
        assertEquals(listOf("a", "c", "b"), keys.movedAmong(keys, "b", 1))
    }

    @Test fun `nothing moves past either end or for an unknown app`() {
        assertEquals(keys, keys.movedAmong(keys, "a", -1))
        assertEquals(keys, keys.movedAmong(keys, "c", 1))
        assertEquals(keys, keys.movedAmong(keys, "z", 1))
        assertEquals(keys, keys.movedAmong(keys, "b", 0))
    }

    @Test fun `apps not on screen keep their places`() {
        // x is hidden or in the other profile: b swaps with a, the app above it on screen, around x.
        val stored = listOf("a", "x", "b", "c")
        val shown = listOf("a", "b", "c")
        assertEquals(listOf("b", "x", "a", "c"), stored.movedAmong(shown, "b", -1))
        assertEquals(listOf("a", "x", "c", "b"), stored.movedAmong(shown, "b", 1))
    }
}
