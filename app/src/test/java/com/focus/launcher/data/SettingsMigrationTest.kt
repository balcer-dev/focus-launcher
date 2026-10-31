package com.focus.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Schema 2 made the split clock the default. Settings are stored with every field written out, so
 * a stored "RING" from before is the old default rather than a choice, and moves along once.
 * Everything a user did choose has to survive.
 */
class SettingsMigrationTest {

    private fun stored(version: Int?, style: String?): JSONObject = Settings().toJson().apply {
        if (version == null) remove("v") else put("v", version)
        if (style == null) remove("clockStyle") else put("clockStyle", style)
    }

    @Test fun `a fresh install gets the split clock`() {
        assertEquals(ClockStyle.SPLIT, Settings().clockStyle)
        assertEquals(ClockStyle.SPLIT, Settings.fromJson(JSONObject()).clockStyle)
    }

    @Test fun `a ring stored before schema 2 becomes the split clock`() {
        assertEquals(ClockStyle.SPLIT, Settings.fromJson(stored(version = null, style = "RING")).clockStyle)
        assertEquals(ClockStyle.SPLIT, Settings.fromJson(stored(version = 1, style = "RING")).clockStyle)
    }

    @Test fun `a plain clock chosen before schema 2 is kept`() {
        assertEquals(ClockStyle.PLAIN, Settings.fromJson(stored(version = null, style = "PLAIN")).clockStyle)
    }

    @Test fun `a ring chosen after the change is kept`() {
        val chosen = Settings(clockStyle = ClockStyle.RING)
        assertEquals(ClockStyle.RING, Settings.fromJson(chosen.toJson()).clockStyle)
    }

    @Test fun `settings survive a round trip`() {
        val s = Settings(clockStyle = ClockStyle.SPLIT, splitSide = SplitSide.SCREEN_TIME, showCalendar = true, doubleTapLock = false, musicAutoHide = false)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    @Test fun `a calendar chosen before several could be is still the one shown`() {
        fun single(key: String) = Settings().toJson().apply { remove("calendarKeys"); put("calendarKey", key) }
        assertEquals(setOf("p:12"), Settings.fromJson(single("p:12")).calendarKeys)
        assertEquals(setOf("w:3"), Settings.fromJson(single("w:3")).calendarKeys)
        assertEquals(setOf(CALENDAR_AUTO), Settings.fromJson(single(CALENDAR_AUTO)).calendarKeys)
        assertEquals(setOf(CALENDAR_ALL), Settings.fromJson(single(CALENDAR_ALL)).calendarKeys)
        val bareId = Settings().toJson().apply { remove("calendarKeys"); put("calendarId", 7L) }
        assertEquals(setOf("p:7"), Settings.fromJson(bareId).calendarKeys)
        assertEquals(setOf(CALENDAR_AUTO), Settings.fromJson(Settings().toJson().apply { remove("calendarKeys") }).calendarKeys)
    }

    @Test fun `several calendars survive a round trip, and odd stored keys fall back sensibly`() {
        val s = Settings(calendarKeys = setOf("p:4", "w:2", "p:9"), compactCalendar = true)
        assertEquals(s, Settings.fromJson(s.toJson()))
        fun withKeys(vararg keys: String) = Settings().toJson().put("calendarKeys", JSONArray(keys.toList()))
        assertEquals(setOf(CALENDAR_ALL), Settings.fromJson(withKeys("p:4", CALENDAR_ALL)).calendarKeys)
        assertEquals(setOf("p:4"), Settings.fromJson(withKeys(CALENDAR_AUTO, "p:4", "x:1", "p:")).calendarKeys)
        assertEquals(setOf(CALENDAR_AUTO), Settings.fromJson(withKeys()).calendarKeys)
        assertEquals(setOf(CALENDAR_AUTO), Settings.fromJson(withKeys("Family")).calendarKeys)
    }

    @Test fun `an install from before the music section could hide gets the hiding`() {
        val old = Settings(showMusic = true).toJson().apply { remove("musicAutoHide") }
        assertEquals(true, Settings.fromJson(old).musicAutoHide)
    }

    @Test fun `an unknown stored value falls back to the default`() {
        assertEquals(SplitSide.CALENDAR, Settings.fromJson(stored(version = 2, style = "SPLIT").put("splitSide", "MAIL")).splitSide)
    }

    @Test fun `a backup file brings every setting back`() {
        val s = Settings(
            favorites = listOf("com.mail/.Main", "com.maps/.Main#10"), hidden = setOf("com.game/.Main"),
            pinned = listOf("com.maps/.Main#10", "com.mail/.Main"),
            renames = mapOf("com.mail/.Main" to "Mail"), appLimits = mapOf("com.video" to 20),
            showNote = true, note = "Call mum", showSeconds = true, favoriteSize = FavoriteSize.SMALL,
            showWallpaper = true, wallpaperStamp = 1_761_900_000_000L, wallpaperVisibility = 60,
            showTodo = true, todos = listOf(TodoItem("a", "Buy milk"), TodoItem("b", "Pay the bill", 1_761_900_000_000L)),
        )
        val file = s.toJson().toString(2)
        assertEquals(s, Settings.fromBackup(JSONObject(file)))
    }

    @Test fun `a wallpaper is off unless chosen, and a hand-edited visibility stays in range`() {
        assertEquals(false, Settings.fromJson(Settings().toJson().apply { remove("showWallpaper") }).showWallpaper)
        assertEquals(10, Settings.fromJson(Settings().toJson().put("wallpaperVisibility", -5)).wallpaperVisibility)
        assertEquals(0L, Settings.fromJson(Settings().toJson().put("wallpaperStamp", -1L)).wallpaperStamp)
    }

    @Test fun `pinned apps keep the order they were pinned in, once each`() {
        val stored = Settings().toJson().put("pinned", JSONArray(listOf("com.b/.Main", "com.a/.Main", "", "com.b/.Main")))
        assertEquals(listOf("com.b/.Main", "com.a/.Main"), Settings.fromJson(stored).pinned)
        assertEquals(emptyList<String>(), Settings.fromJson(Settings().toJson().apply { remove("pinned") }).pinned)
    }

    @Test fun `a file that is not a backup is refused`() {
        assertNull(Settings.fromBackup(JSONObject("""{"name": "something else", "v": 1}""")))
        assertNull(Settings.fromBackup(Settings().toJson().apply { remove("v") }))
    }

    @Test fun `a backup from a newer schema is refused`() {
        assertNull(Settings.fromBackup(Settings().toJson().put("v", Settings.SCHEMA + 1)))
    }
}
