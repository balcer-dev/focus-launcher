package com.focus.launcher

import org.junit.Assert.assertEquals
import org.junit.Test

/** Where each page of the launcher is. Without the widgets page nothing may move from where it always was. */
class PagerLayoutTest {

    @Test fun `without the widgets page the pager is home and the app drawer, as before`() {
        val layout = PagerLayout(showWidgets = false)
        assertEquals(2, layout.pageCount)
        assertEquals(1, layout.drawerPage)
        assertEquals(-1, layout.widgetsPage)
    }

    @Test fun `the widgets page sits between home and the app drawer`() {
        val layout = PagerLayout(showWidgets = true)
        assertEquals(3, layout.pageCount)
        assertEquals(1, layout.widgetsPage)
        assertEquals(2, layout.drawerPage)
    }

    @Test fun `the app drawer is always the last page, the one swipe up and the tip lead to`() {
        for (showWidgets in listOf(false, true)) {
            val layout = PagerLayout(showWidgets)
            assertEquals(layout.pageCount - 1, layout.drawerPage)
        }
    }
}
