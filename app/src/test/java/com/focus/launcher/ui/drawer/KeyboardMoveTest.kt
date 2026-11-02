package com.focus.launcher.ui.drawer

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a drawer page does with the keyboard as it comes and goes. The fast-and-pinned page has no
 * search field: it must not ask for the keyboard, nor take it from the app list next to it, which
 * opens it in the same frame the fast-and-pinned page is left.
 */
class KeyboardMoveTest {

    @Test fun `the fast-and-pinned page leaves the keyboard alone, coming or going`() {
        for (active in listOf(true, false)) for (auto in listOf(true, false)) for (wants in listOf(true, false)) {
            assertEquals(KeyboardMove.NONE, keyboardMove(isActive = active, quick = true, autoKeyboard = auto, wantsSearchFocus = wants))
        }
    }

    @Test fun `the app list opens the keyboard when set to, or when swipe up asked for it`() {
        assertEquals(KeyboardMove.SHOW, keyboardMove(isActive = true, quick = false, autoKeyboard = true, wantsSearchFocus = false))
        assertEquals(KeyboardMove.SHOW, keyboardMove(isActive = true, quick = false, autoKeyboard = false, wantsSearchFocus = true))
    }

    @Test fun `otherwise the app list closes it`() {
        assertEquals(KeyboardMove.HIDE, keyboardMove(isActive = true, quick = false, autoKeyboard = false, wantsSearchFocus = false))
        assertEquals(KeyboardMove.HIDE, keyboardMove(isActive = false, quick = false, autoKeyboard = true, wantsSearchFocus = true))
    }
}
