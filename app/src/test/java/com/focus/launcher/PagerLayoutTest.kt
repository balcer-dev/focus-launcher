package com.focus.launcher

import com.focus.launcher.data.EXAMPLE_1
import com.focus.launcher.data.EXAMPLE_2
import com.focus.launcher.data.GestureLayout
import com.focus.launcher.data.LauncherPage.ALL_APPS
import com.focus.launcher.data.LauncherPage.QUICK
import com.focus.launcher.data.LauncherPage.WIDGETS
import com.focus.launcher.data.SETTINGS_APP
import com.focus.launcher.data.Swipe.LEFT
import com.focus.launcher.data.Swipe.RIGHT
import com.focus.launcher.data.Swipe.UP
import com.focus.launcher.data.SwipeTarget.App
import com.focus.launcher.data.SwipeTarget.None
import com.focus.launcher.data.SwipeTarget.WebSearch
import com.focus.launcher.data.pages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where each page of the launcher is, worked out from the gestures. Whoever changes nothing keeps the
 * pages where they always were; the home screen is page 0 only while no page lies to its left.
 */
class PagerLayoutTest {

    @Test fun `the default is home and the app list, as before`() {
        val l = PagerLayout(GestureLayout.DEFAULT)
        assertEquals(listOf(null, ALL_APPS), l.pages)
        assertEquals(2, l.pageCount)
        assertEquals(0, l.homePage)
        assertEquals(1, l.drawerPage)
        assertEquals(-1, l.widgetsPage)
        assertNull(l.sheet)
        assertEquals(WebSearch, l.actionFor(RIGHT))
        assertNull(l.actionFor(LEFT))
    }

    @Test fun `the widgets page sits between home and the app list, as before`() {
        val l = PagerLayout(GestureLayout.fromLegacy(true, "", true, true))
        assertEquals(listOf(null, WIDGETS, ALL_APPS), l.pages)
        assertEquals(0, l.homePage)
        assertEquals(1, l.widgetsPage)
        assertEquals(2, l.drawerPage)
    }

    @Test fun `example 1 - widgets left of home, the app list slides up, swipe left opens an app`() {
        val l = PagerLayout(EXAMPLE_1)
        assertEquals(listOf(WIDGETS, null), l.pages)
        assertEquals(1, l.homePage)
        assertEquals(0, l.widgetsPage)
        assertEquals(-1, l.drawerPage)
        assertEquals(ALL_APPS, l.sheet)
        assertEquals(App(SETTINGS_APP), l.actionFor(LEFT))
        assertNull(l.actionFor(RIGHT))
    }

    @Test fun `example 2 - the app list nearest on the left, then widgets`() {
        val l = PagerLayout(EXAMPLE_2)
        assertEquals(listOf(WIDGETS, ALL_APPS, null), l.pages)
        assertEquals(2, l.homePage)
        assertEquals(1, l.drawerPage)
        assertEquals(0, l.widgetsPage)
        assertEquals(QUICK, l.sheet)
        assertNull(l.actionFor(LEFT))
        assertNull(l.actionFor(RIGHT))
    }

    @Test fun `swipe up to a page in a sequence scrolls the pager instead`() {
        assertNull(PagerLayout(GestureLayout(None, pages(WIDGETS, ALL_APPS), pages(WIDGETS))).sheet)
    }

    @Test fun `swipe up never acts as a sideways swipe`() {
        assertNull(PagerLayout(GestureLayout(None, pages(ALL_APPS), App(SETTINGS_APP))).actionFor(UP))
    }
}
