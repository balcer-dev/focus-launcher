package com.focus.launcher.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app list's search field takes the keyboard: always, only after swipe up (how it always
 * was with the switch off), or never. Settings from before keep exactly what they did.
 */
class ListKeyboardTest {

    private fun before(autoKeyboard: Boolean?): JSONObject = Settings().toJson().apply {
        remove("listKeyboard")
        if (autoKeyboard == null) remove("autoKeyboard") else put("autoKeyboard", autoKeyboard)
    }

    @Test fun `the switch of before becomes the choice that does the same`() {
        assertEquals(ListKeyboard.ALWAYS, Settings.fromJson(before(autoKeyboard = true)).listKeyboard)
        assertEquals(ListKeyboard.SWIPE_UP, Settings.fromJson(before(autoKeyboard = false)).listKeyboard)
        assertEquals(ListKeyboard.SWIPE_UP, Settings.fromJson(before(autoKeyboard = null)).listKeyboard)
    }

    @Test fun `a fresh install opens it only after swipe up, as before`() {
        assertEquals(ListKeyboard.SWIPE_UP, Settings().listKeyboard)
        assertEquals(ListKeyboard.SWIPE_UP, Settings.fromJson(JSONObject()).listKeyboard)
    }

    @Test fun `every choice survives a round trip, and an older version reads the switch`() {
        for (choice in ListKeyboard.entries) {
            val o = Settings(listKeyboard = choice).toJson()
            assertEquals(choice, Settings.fromJson(o).listKeyboard)
            assertEquals(choice == ListKeyboard.ALWAYS, o.getBoolean("autoKeyboard"))
        }
    }

    @Test fun `an unknown stored choice falls back to the switch next to it`() {
        val o = Settings().toJson().put("listKeyboard", "SOMETIMES").put("autoKeyboard", true)
        assertEquals(ListKeyboard.ALWAYS, Settings.fromJson(o).listKeyboard)
    }

    @Test fun `what each choice does on the way to the list`() {
        assertTrue(ListKeyboard.ALWAYS.opensOnArrival)
        assertFalse(ListKeyboard.SWIPE_UP.opensOnArrival)
        assertFalse(ListKeyboard.NEVER.opensOnArrival)
        assertTrue(ListKeyboard.ALWAYS.opensOnSwipeUp)
        assertTrue(ListKeyboard.SWIPE_UP.opensOnSwipeUp)
        assertFalse(ListKeyboard.NEVER.opensOnSwipeUp)
    }
}
