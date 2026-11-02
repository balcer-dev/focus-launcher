package com.focus.launcher.ui.drawer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The two sections above the app list, and the whole of the fast-and-pinned page: the fast apps in
 * their home-screen order, then the pinned apps not already among them. Keys stand for the apps.
 */
class QuickSectionsTest {

    @Test fun `fast apps first in their order, then pins not among them, both profiles`() {
        val (fast, pinned) = quickSections(listOf("a", "b", "c", "d#10"), { it }, favorites = listOf("c", "a"), pinned = listOf("d#10", "a", "gone"))
        assertEquals(listOf("c", "a"), fast)
        assertEquals(listOf("d#10"), pinned)
    }

    @Test fun `apps that are not shown are left out of both`() {
        val (fast, pinned) = quickSections(listOf("a"), { it }, favorites = listOf("hidden", "a"), pinned = listOf("hidden"))
        assertEquals(listOf("a"), fast)
        assertEquals(emptyList<String>(), pinned)
    }

    @Test fun `without fast apps the pins are the only section`() {
        val (fast, pinned) = quickSections(listOf("a", "b"), { it }, favorites = emptyList(), pinned = listOf("b", "a"))
        assertEquals(emptyList<String>(), fast)
        assertEquals(listOf("b", "a"), pinned)
    }
}
