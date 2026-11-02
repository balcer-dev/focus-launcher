package com.focus.launcher.data

import com.focus.launcher.data.LauncherPage.ALL_APPS
import com.focus.launcher.data.LauncherPage.QUICK
import com.focus.launcher.data.LauncherPage.WIDGETS
import com.focus.launcher.data.Swipe.LEFT
import com.focus.launcher.data.Swipe.RIGHT
import com.focus.launcher.data.SwipeTarget.App
import com.focus.launcher.data.SwipeTarget.None
import com.focus.launcher.data.SwipeTarget.WebSearch
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules every gesture layout keeps, however it was made: in Settings, on the welcome screen or
 * by hand in a backup file. A page stands in the sequences at most once, swipe up holds one page,
 * and all apps can always be reached.
 */
class GestureLayoutTest {

    @Test fun `a page stands in the sequences at most once, swipe left keeping it`() {
        val l = GestureLayout(pages(ALL_APPS, WIDGETS, ALL_APPS), pages(WIDGETS), None).normalized()
        assertEquals(pages(ALL_APPS), l.right)
        assertEquals(pages(WIDGETS), l.left)
    }

    @Test fun `a sequence left empty is nothing`() {
        assertEquals(None, GestureLayout(pages(WIDGETS), pages(WIDGETS, ALL_APPS), None).normalized().right)
        assertEquals(None, GestureLayout(SwipeTarget.Pages(emptyList()), pages(ALL_APPS), None).normalized().right)
    }

    @Test fun `swipe up holds one page`() {
        assertEquals(pages(QUICK), GestureLayout(WebSearch, pages(ALL_APPS), pages(QUICK, WIDGETS)).normalized().up)
    }

    @Test fun `without the app list anywhere swipe up gets it`() {
        assertEquals(pages(ALL_APPS), GestureLayout(WebSearch, None, App("x")).normalized().up)
        assertEquals(pages(ALL_APPS), GestureLayout(None, None, None).normalized().up)
    }

    @Test fun `a blank app key is the web search`() {
        assertEquals(WebSearch, GestureLayout(App(""), pages(ALL_APPS), None).normalized().right)
        assertEquals(WebSearch, GestureLayout(WebSearch, pages(ALL_APPS), App(" ")).normalized().up)
    }

    @Test fun `unknown types and page names are dropped`() {
        val o = JSONObject("""{"right":{"type":"rocket"},"left":{"type":"pages","pages":["ALL_APPS","MAP"]}}""")
        assertEquals(GestureLayout(None, pages(ALL_APPS), None), GestureLayout.fromJson(o)!!.normalized())
    }

    @Test fun `a layout of the wrong shape still comes out keeping the rules`() {
        val o = JSONObject("""{"right":"search","left":{"type":"pages","pages":"ALL_APPS"},"up":{"type":"app"}}""")
        val read = GestureLayout.fromJson(o)!!
        assertEquals(GestureLayout(None, SwipeTarget.Pages(emptyList()), App("")), read)
        assertEquals(GestureLayout(None, None, pages(ALL_APPS)), read.normalized())
        assertEquals(null, GestureLayout.fromJson(null))
    }

    @Test fun `a page taken by one sequence leaves the other`() {
        assertEquals(GestureLayout(pages(ALL_APPS), None, pages(ALL_APPS)), GestureLayout.DEFAULT.withTarget(RIGHT, pages(ALL_APPS)))
        val bothSides = GestureLayout(pages(WIDGETS), pages(QUICK, ALL_APPS), None).withTarget(LEFT, pages(WIDGETS, ALL_APPS))
        assertEquals(GestureLayout(None, pages(WIDGETS, ALL_APPS), None), bothSides)
    }

    @Test fun `the last way to the app list cannot be taken away`() {
        val onlyLeft = GestureLayout(WebSearch, pages(ALL_APPS), None)
        assertFalse(onlyLeft.allows(LEFT, None))
        assertFalse(onlyLeft.allows(LEFT, pages(WIDGETS)))
        assertTrue(onlyLeft.allows(Swipe.UP, pages(QUICK)))
        assertTrue(onlyLeft.allows(RIGHT, pages(ALL_APPS)))
        assertTrue(GestureLayout.DEFAULT.allows(LEFT, None))
        assertFalse(GestureLayout(None, None, pages(ALL_APPS)).allows(Swipe.UP, pages(QUICK)))
    }

    @Test fun `an app that is gone falls back to the web search`() {
        assertEquals(WebSearch, App("gone").resolvedAgainst(setOf("a")))
        assertEquals(App("a"), App("a").resolvedAgainst(setOf("a")))
        assertEquals(None, None.resolvedAgainst(emptySet()))
        assertEquals(pages(WIDGETS), pages(WIDGETS).resolvedAgainst(emptySet()))
    }

    @Test fun `a page is reached from a sequence or from swipe up`() {
        assertTrue(EXAMPLE_1.reaches(WIDGETS))
        assertTrue(EXAMPLE_1.reaches(ALL_APPS))
        assertFalse(EXAMPLE_1.reaches(QUICK))
        assertTrue(EXAMPLE_2.reaches(QUICK))
    }

    @Test fun `each swipe's target`() {
        assertEquals(EXAMPLE_2.right, EXAMPLE_2[RIGHT])
        assertEquals(EXAMPLE_2.left, EXAMPLE_2[LEFT])
        assertEquals(EXAMPLE_2.up, EXAMPLE_2[Swipe.UP])
    }

    @Test fun `where a page stands`() {
        val widgets = GestureLayout.fromLegacy(true, "", true, true)
        assertEquals(Place(LEFT, 1), GestureLayout.DEFAULT.placeOf(ALL_APPS))
        assertEquals(null, GestureLayout.DEFAULT.placeOf(WIDGETS))
        assertEquals(Place(LEFT, 1), widgets.placeOf(WIDGETS))
        assertEquals(Place(LEFT, 2), widgets.placeOf(ALL_APPS))
        assertEquals(Place(RIGHT, 2), EXAMPLE_2.placeOf(WIDGETS))
        assertEquals(Place(RIGHT, 1), EXAMPLE_2.placeOf(ALL_APPS))
        assertEquals(Place(Swipe.UP, 1), EXAMPLE_2.placeOf(QUICK))
        assertEquals(Place(Swipe.UP, 1), EXAMPLE_1.placeOf(ALL_APPS))
    }

    @Test fun `both examples survive a round trip`() {
        for (layout in listOf(EXAMPLE_1, EXAMPLE_2)) {
            assertEquals(layout, Settings.fromJson(Settings(gestures = layout).toJson()).gestures)
        }
    }
}
