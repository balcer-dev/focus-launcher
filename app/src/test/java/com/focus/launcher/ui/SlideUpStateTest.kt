package com.focus.launcher.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page under swipe up: open or not, and how it was closed. Closed while the launcher is out of
 * sight, it must not be seen sliding away on the way back; while it slides away, it takes no taps.
 */
class SlideUpStateTest {

    @Test fun `closing it in sight keeps the page, so it slides away`() {
        val s = SlideUpState()
        s.open()
        val before = s.generation
        s.close()
        assertFalse(s.isOpen)
        assertEquals(before, s.generation)
    }

    @Test fun `closing it out of sight starts the page afresh, so nothing slides on the way back`() {
        val s = SlideUpState()
        s.open()
        val before = s.generation
        s.closeAtOnce()
        assertFalse(s.isOpen)
        assertNotEquals(before, s.generation)
    }

    @Test fun `closing a page that is not open changes nothing`() {
        val s = SlideUpState()
        val before = s.generation
        s.closeAtOnce()
        s.close()
        assertEquals(before, s.generation)
        assertFalse(s.isOpen)
    }

    @Test fun `only an open page takes taps`() {
        val s = SlideUpState()
        assertFalse(s.takesTaps)
        s.open()
        assertTrue(s.takesTaps)
        s.close()
        assertFalse(s.takesTaps)
    }
}
