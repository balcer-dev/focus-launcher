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
            gestures = GestureLayout.fromLegacy(true, "com.maps/.Main", true, false),
            widgets = listOf(WidgetSlot(12, 260), WidgetSlot(7)),
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

    @Test fun `the app list stays a list unless the grid was chosen`() {
        assertEquals(DrawerLayout.LIST, Settings.fromJson(Settings().toJson().apply { remove("drawerLayout") }).drawerLayout)
        assertEquals(DrawerLayout.LIST, Settings.fromJson(Settings().toJson().put("drawerLayout", "TILES")).drawerLayout)
        val s = Settings(drawerLayout = DrawerLayout.GRID)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    @Test fun `grid icons stay grey unless colour was chosen`() {
        assertEquals(false, Settings.fromJson(Settings().toJson().apply { remove("colourIcons") }).colourIcons)
        val s = Settings(drawerLayout = DrawerLayout.GRID, colourIcons = true)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    @Test fun `fast apps show their names unless icons were chosen`() {
        assertEquals(FavoriteStyle.NAME, Settings.fromJson(Settings().toJson().apply { remove("favoriteStyle") }).favoriteStyle)
        assertEquals(FavoriteStyle.NAME, Settings.fromJson(Settings().toJson().put("favoriteStyle", "LOGO")).favoriteStyle)
        assertEquals(false, Settings.fromJson(Settings().toJson().apply { remove("colourFavoriteIcons") }).colourFavoriteIcons)
        val s = Settings(favoriteStyle = FavoriteStyle.BOTH, colourFavoriteIcons = true)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    @Test fun `fast apps join the top of the app list only once asked to`() {
        assertEquals(false, Settings.fromJson(Settings().toJson().apply { remove("favoritesInDrawer") }).favoritesInDrawer)
        assertEquals(false, Settings.fromJson(Settings().toJson().apply { remove("favoritesAlsoInList") }).favoritesAlsoInList)
        val s = Settings(favoritesInDrawer = true, favoritesAlsoInList = true)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    @Test fun `fast apps stay on the home screen unless taken off it`() {
        assertEquals(true, Settings.fromJson(Settings().toJson().apply { remove("showFavoritesOnHome") }).showFavoritesOnHome)
        val s = Settings(showFavoritesOnHome = false)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    @Test fun `fast apps keep the page alignment they had until given their own`() {
        val before = Settings(homeAlign = HomeAlign.RIGHT).toJson().apply { remove("favoriteAlign") }
        assertEquals(HomeAlign.RIGHT, Settings.fromJson(before).favoriteAlign)
        val s = Settings(homeAlign = HomeAlign.LEFT, favoriteAlign = HomeAlign.CENTER)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    @Test fun `checked tasks leave the home screen unless asked to stay`() {
        assertEquals(false, Settings.fromJson(Settings().toJson().apply { remove("showCheckedTodos") }).showCheckedTodos)
        val s = Settings(showTodo = true, showCheckedTodos = true)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    @Test fun `the search field stays at the top unless moved down`() {
        assertEquals(false, Settings().searchAtBottom)
        assertEquals(false, Settings.fromJson(Settings().toJson().apply { remove("searchAtBottom") }).searchAtBottom)
        val s = Settings(searchAtBottom = true)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    @Test fun `search forgives typos only once asked to`() {
        assertEquals(false, Settings.fromJson(Settings().toJson().apply { remove("typoSearch") }).typoSearch)
        val s = Settings(typoSearch = true)
        assertEquals(s, Settings.fromJson(s.toJson()))
    }

    // ---- gestures: the four fields of before, read into one layout (see GestureLayout) ----

    /** Settings as stored before the gesture layout: no "gestures", the old fields as given. */
    private fun legacy(right: Boolean, app: String, widgets: Boolean, up: Boolean): JSONObject = Settings().toJson().apply {
        remove("gestures")
        put("swipeRightSearch", right)
        put("swipeRightApp", app)
        put("showWidgetPage", widgets)
        put("swipeUpSearch", up)
    }

    private val maps = "com.maps/.Main"

    private fun everyOldVariant(check: (right: Boolean, app: String, widgets: Boolean, up: Boolean) -> Unit) {
        for (right in listOf(false, true)) for (app in listOf("", maps)) for (widgets in listOf(false, true)) for (up in listOf(false, true)) {
            check(right, app, widgets, up)
        }
    }

    @Test fun `swipe right as it was`() {
        assertEquals(SwipeTarget.None, Settings.fromJson(legacy(right = false, app = maps, widgets = false, up = true)).gestures.right)
        assertEquals(SwipeTarget.WebSearch, Settings.fromJson(legacy(right = true, app = "", widgets = false, up = true)).gestures.right)
        assertEquals(SwipeTarget.App(maps), Settings.fromJson(legacy(right = true, app = maps, widgets = false, up = true)).gestures.right)
    }

    @Test fun `the widgets page as it was`() {
        assertEquals(pages(LauncherPage.WIDGETS, LauncherPage.ALL_APPS), Settings.fromJson(legacy(true, "", widgets = true, up = true)).gestures.left)
        assertEquals(pages(LauncherPage.ALL_APPS), Settings.fromJson(legacy(true, "", widgets = false, up = true)).gestures.left)
    }

    @Test fun `swipe up as it was`() {
        assertEquals(pages(LauncherPage.ALL_APPS), Settings.fromJson(legacy(true, "", false, up = true)).gestures.up)
        assertEquals(SwipeTarget.None, Settings.fromJson(legacy(true, "", false, up = false)).gestures.up)
    }

    @Test fun `a fresh install and settings from before have today's gestures`() {
        val today = GestureLayout(SwipeTarget.WebSearch, pages(LauncherPage.ALL_APPS), pages(LauncherPage.ALL_APPS))
        assertEquals(today, Settings().gestures)
        assertEquals(today, GestureLayout.DEFAULT)
        assertEquals(today, Settings.fromJson(JSONObject()).gestures)
    }

    @Test fun `every old variant comes back from a backup file`() = everyOldVariant { right, app, widgets, up ->
        val file = legacy(right, app, widgets, up).toString(2)
        assertEquals(GestureLayout.fromLegacy(right, app, widgets, up), Settings.fromBackup(JSONObject(file))!!.gestures)
    }

    @Test fun `a stored layout wins over the old fields next to it`() {
        val stored = Settings(gestures = EXAMPLE_2).toJson()
            .put("swipeRightSearch", true).put("swipeRightApp", maps).put("showWidgetPage", false).put("swipeUpSearch", true)
        assertEquals(EXAMPLE_2, Settings.fromJson(stored).gestures)
    }

    @Test fun `the old fields written next to a layout describe it for an older version`() {
        val o = Settings(gestures = EXAMPLE_1).toJson()
        assertEquals(false, o.getBoolean("swipeRightSearch"))
        assertEquals("", o.getString("swipeRightApp"))
        assertEquals(true, o.getBoolean("showWidgetPage"))
        assertEquals(true, o.getBoolean("swipeUpSearch"))
        val withApp = Settings(gestures = GestureLayout(SwipeTarget.App(maps), pages(LauncherPage.ALL_APPS), SwipeTarget.None)).toJson()
        assertEquals(true, withApp.getBoolean("swipeRightSearch"))
        assertEquals(maps, withApp.getString("swipeRightApp"))
        assertEquals(false, withApp.getBoolean("showWidgetPage"))
        assertEquals(false, withApp.getBoolean("swipeUpSearch"))
    }

    @Test fun `old-shaped layouts survive the trip through the old fields`() = everyOldVariant { right, app, widgets, up ->
        val layout = GestureLayout.fromLegacy(right, app, widgets, up)
        val olderVersion = Settings(gestures = layout).toJson().apply { remove("gestures") }
        assertEquals(layout, Settings.fromJson(olderVersion).gestures)
    }

    @Test fun `a file that is not a backup is refused`() {
        assertNull(Settings.fromBackup(JSONObject("""{"name": "something else", "v": 1}""")))
        assertNull(Settings.fromBackup(Settings().toJson().apply { remove("v") }))
    }

    @Test fun `a backup from a newer schema is refused`() {
        assertNull(Settings.fromBackup(Settings().toJson().put("v", Settings.SCHEMA + 1)))
    }
}
