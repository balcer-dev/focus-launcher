package com.focus.launcher.data

import androidx.annotation.StringRes
import com.focus.launcher.R
import org.json.JSONArray
import org.json.JSONObject

enum class FontChoice(@StringRes val labelRes: Int) {
    SANS(R.string.font_sans), SERIF(R.string.font_serif), MONO(R.string.font_mono),
}

/**
 * SPLIT: the clock on the left, one section on the right, a single vertical line between them and
 * no frame around it. RING: the clock inside a circle whose arc is the battery (or the day).
 */
enum class ClockStyle(@StringRes val labelRes: Int) {
    SPLIT(R.string.clock_style_split), RING(R.string.clock_style_ring), PLAIN(R.string.clock_style_plain),
}

/** What the right half of the split clock shows. The section shown there is not repeated below. */
enum class SplitSide(@StringRes val labelRes: Int) {
    CALENDAR(R.string.split_side_calendar), SCREEN_TIME(R.string.split_side_screen_time),
}

/** What the arc of the clock ring stands for. */
enum class RingMode(@StringRes val labelRes: Int) { BATTERY(R.string.ring_mode_battery), DAY(R.string.ring_mode_day) }

/** How an app appears when it is opened from the launcher. */
enum class LaunchAnimation(@StringRes val labelRes: Int) {
    FAST(R.string.launch_animation_fast), SYSTEM(R.string.launch_animation_system),
}

enum class HomeAlign(@StringRes val labelRes: Int) {
    LEFT(R.string.align_left), CENTER(R.string.align_center), RIGHT(R.string.align_right),
}

/** Order of the app list in the drawer. */
enum class DrawerSort(@StringRes val labelRes: Int) {
    ALPHA(R.string.sort_alpha), MOST_USED(R.string.sort_most_used), RECENT(R.string.sort_recent),
}

/** How the drawer shows the apps. GRID loads app icons; the text list never does. */
enum class DrawerLayout(@StringRes val labelRes: Int) {
    LIST(R.string.layout_list), GRID(R.string.layout_grid),
}

enum class TimeFormat(@StringRes val labelRes: Int) {
    SYSTEM(R.string.time_format_system), H24(R.string.time_format_24h), H12(R.string.time_format_12h),
}

/** Text size of the fast apps on the home screen, as a share of what the layout would pick by itself. */
enum class FavoriteSize(@StringRes val labelRes: Int, val scale: Float) {
    SMALL(R.string.favorite_size_small, 0.75f),
    MEDIUM(R.string.favorite_size_medium, 0.875f),
    LARGE(R.string.favorite_size_large, 1f),
}

/** What each fast app on the home screen shows. An icon is as tall as a line of the name. */
enum class FavoriteStyle(@StringRes val labelRes: Int) {
    NAME(R.string.favorite_style_name), ICON(R.string.favorite_style_icon), BOTH(R.string.favorite_style_both),
}

const val MAX_FAVORITES = 10

/** Up to this many fast apps stand in one column on the home screen; more are split into two. */
const val FAVORITES_PER_COLUMN = 5

/** Corner shortcuts that resolve to whatever the phone's default dialer / camera is. */
const val SHORTCUT_PHONE = "auto:phone"
const val SHORTCUT_CAMERA = "auto:camera"

/** What a tap on the clock does. Anything that is not one of these is an [AppEntry.key] to open. */
const val TAP_ALARMS = "tap:alarms"
const val TAP_CALENDAR = "tap:calendar"
const val TAP_SCREEN_TIME = "tap:screentime"
const val TAP_BATTERY = "tap:battery"
const val TAP_NOTHING = "tap:none"

/**
 * Every user-facing preference of the launcher. Immutable; changed through [SettingsStore.update].
 *
 * App references ([favorites], [hidden], [pinned], [renames], shortcuts) use [AppEntry.key].
 * Timers ([appLimits]) are keyed by package name because usage is tracked per package.
 */
data class Settings(
    // Appearance
    val dark: Boolean = true,
    val font: FontChoice = FontChoice.SANS,
    val textScale: Float = 1f,
    val hideStatusBar: Boolean = false,
    val launchAnimation: LaunchAnimation = LaunchAnimation.FAST,
    /** A picture of the user's choice behind the home screen ([WallpaperStore]); the app list and settings stay plain. */
    val showWallpaper: Boolean = false,
    /**
     * When the current picture was chosen (ms), so the home screen knows to load it again; 0 = none.
     * The picture itself is a private file, not a setting: restored on another phone, this points
     * at nothing and the home screen has no wallpaper.
     */
    val wallpaperStamp: Long = 0L,
    /** How much of the picture shows through the theme's black or white, in percent. */
    val wallpaperVisibility: Int = 40,

    // Home
    val clockStyle: ClockStyle = ClockStyle.SPLIT,
    /** Right half of the split clock. CALENDAR needs [showCalendar]; without it screen time is shown. */
    val splitSide: SplitSide = SplitSide.CALENDAR,
    val ringMode: RingMode = RingMode.BATTERY,
    val clockTap: String = TAP_ALARMS,
    val timeFormat: TimeFormat = TimeFormat.SYSTEM,
    /** Seconds after the minutes, smaller. The clock then redraws every second while the home screen is shown. */
    val showSeconds: Boolean = false,
    val showDate: Boolean = true,
    val showCalendar: Boolean = false,
    /**
     * [CalendarInfo.key]s of the calendars shown together on the home screen, or [CALENDAR_AUTO] or
     * [CALENDAR_ALL] alone; kept in that shape by [calendarSelection].
     */
    val calendarKeys: Set<String> = setOf(CALENDAR_AUTO),
    /** Smaller event text in the calendar section and next to the split clock, so more events fit. */
    val compactCalendar: Boolean = false,
    /** The Mon-Sun strip with today marked. Off: the ring already carries the date. */
    val showWeekStrip: Boolean = false,
    /** Previous, play or pause, next, and what is playing, as a section of the home screen. */
    val showMusic: Boolean = false,
    /**
     * The music section leaves the home screen while nothing is playing and comes back with the
     * music (the owner's wish). Off = it is always there, as a way into the music app.
     */
    val musicAutoHide: Boolean = true,
    /** A few lines of the user's own as a section of the home screen; [note] is the text. */
    val showNote: Boolean = false,
    val note: String = "",
    /** [AppEntry.key] opened by a tap on the music section while nothing is playing. "" = not chosen yet: the first tap asks. */
    val musicApp: String = "",
    /** [AppEntry.key] of the notes app offered next to the note's title; "" = none. */
    val noteApp: String = "",
    /** With a notes app chosen: a link to one page in it (what its "copy link" gives). Blank = the app's own start screen. */
    val noteLink: String = "",
    /** A short checklist as a section of the home screen; a checked task is dropped [TodoItem.RETENTION_MS] later. */
    val showTodo: Boolean = false,
    val todos: List<TodoItem> = emptyList(),
    val homeAlign: HomeAlign = HomeAlign.CENTER,
    val favorites: List<String> = emptyList(),
    /** Off: no fast apps on the home screen, nor the hint to add some. They stay set for [favoritesInDrawer]. */
    val showFavoritesOnHome: Boolean = true,
    /** The fast apps' own alignment; [homeAlign] is the clock's and the sections'. Older settings take [homeAlign]. */
    val favoriteAlign: HomeAlign = HomeAlign.CENTER,
    val favoriteSize: FavoriteSize = FavoriteSize.LARGE,
    val favoriteStyle: FavoriteStyle = FavoriteStyle.NAME,
    /** Fast app icons in the apps' own colours. An app whose time is up stays grey either way. */
    val colourFavoriteIcons: Boolean = false,
    val showShortcuts: Boolean = true,
    val leftShortcut: String = SHORTCUT_PHONE,
    val rightShortcut: String = SHORTCUT_CAMERA,

    // Widgets page
    /** A page of Android widgets between the home screen and the app list. Off: the pager is the two of them only. */
    val showWidgetPage: Boolean = false,
    /**
     * The widgets on that page, top to bottom. Their ids belong to this phone's widget service: from
     * a backup restored on another phone they stand for nothing, and the launcher drops them the next
     * time it comes on screen.
     */
    val widgets: List<WidgetSlot> = emptyList(),

    // Drawer
    val autoKeyboard: Boolean = false,
    val autoLaunch: Boolean = false,
    /** Search also lists names one or two edits away, below every closer match; [autoLaunch] ignores them. */
    val typoSearch: Boolean = false,
    /** The home screen's [favorites] also at the very top of the app list, above the [pinned] ones. */
    val favoritesInDrawer: Boolean = false,
    /** With [favoritesInDrawer], the fast apps also keep their place in the main list below. */
    val favoritesAlsoInList: Boolean = false,
    val showRecentInstalls: Boolean = true,
    val showUsageInDrawer: Boolean = true,
    val drawerSort: DrawerSort = DrawerSort.ALPHA,
    /** LIST is text only. GRID: four apps to a row, each with a small icon ([AppIcons]), grey unless [colourIcons]. */
    val drawerLayout: DrawerLayout = DrawerLayout.LIST,
    /** The grid's icons in the apps' own colours. An app whose time is up stays grey either way. */
    val colourIcons: Boolean = false,
    val hidden: Set<String> = emptySet(),
    /** Apps listed above all others in the app list, in the order they were pinned. */
    val pinned: List<String> = emptyList(),
    val renames: Map<String, String> = emptyMap(),

    // App timers
    val timersEnabled: Boolean = true,
    /** Daily minutes applied to every social app without its own limit. 0 = off. */
    val socialDefaultMin: Int = 30,
    /** Daily minutes applied to every game without its own limit. 0 = off. */
    val gameDefaultMin: Int = 30,
    /** Daily minutes applied to every video / streaming app without its own limit. 0 = off. */
    val videoDefaultMin: Int = 0,
    /** package -> daily minutes. 0 means "explicitly unlimited", overriding the category default. */
    val appLimits: Map<String, Int> = emptyMap(),
    val allowContinue: Boolean = true,
    val continueOptions: List<Int> = listOf(1, 5, 15),
    val allowBypass: Boolean = true,
    /** After "ignore the limit for today", still ask before every single open of that app. */
    val askAfterBypass: Boolean = true,
    /** Seconds the "continue" choices stay locked after the wall appears. */
    val frictionSeconds: Int = 5,
    /** Heads-up this many minutes before a limit runs out. 0 = off. */
    val warnMinutes: Int = 1,

    // Weekly review
    val weeklyEnabled: Boolean = true,
    val weekStartsMonday: Boolean = true,
    val reviewHour: Int = 20,

    // Gestures
    val swipeDownNotifications: Boolean = true,
    val swipeUpSearch: Boolean = true,
    /** Swipe towards the page left of home (finger moves right): the phone's web search. */
    val swipeRightSearch: Boolean = true,
    /** [AppEntry.key] of the app that swipe right opens. "" = the web search. */
    val swipeRightApp: String = "",
    val doubleTapLock: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("v", SCHEMA)
        put("dark", dark)
        put("font", font.name)
        put("textScale", textScale.toDouble())
        put("hideStatusBar", hideStatusBar)
        put("launchAnimation", launchAnimation.name)
        put("showWallpaper", showWallpaper)
        put("wallpaperStamp", wallpaperStamp)
        put("wallpaperVisibility", wallpaperVisibility)

        put("clockStyle", clockStyle.name)
        put("splitSide", splitSide.name)
        put("ringMode", ringMode.name)
        put("clockTap", clockTap)
        put("timeFormat", timeFormat.name)
        put("showSeconds", showSeconds)
        put("showDate", showDate)
        put("showCalendar", showCalendar)
        put("calendarKeys", JSONArray(calendarKeys.sorted()))
        put("compactCalendar", compactCalendar)
        put("showWeekStrip", showWeekStrip)
        put("showMusic", showMusic)
        put("musicAutoHide", musicAutoHide)
        put("showNote", showNote)
        put("note", note)
        put("musicApp", musicApp)
        put("noteApp", noteApp)
        put("noteLink", noteLink)
        put("showTodo", showTodo)
        put("todos", JSONArray().apply { todos.forEach { put(it.toJson()) } })
        put("homeAlign", homeAlign.name)
        put("favorites", JSONArray(favorites))
        put("showFavoritesOnHome", showFavoritesOnHome)
        put("favoriteAlign", favoriteAlign.name)
        put("favoriteSize", favoriteSize.name)
        put("favoriteStyle", favoriteStyle.name)
        put("colourFavoriteIcons", colourFavoriteIcons)
        put("showShortcuts", showShortcuts)
        put("leftShortcut", leftShortcut)
        put("rightShortcut", rightShortcut)

        put("showWidgetPage", showWidgetPage)
        put("widgets", JSONArray().apply { widgets.forEach { put(it.toJson()) } })

        put("autoKeyboard", autoKeyboard)
        put("autoLaunch", autoLaunch)
        put("typoSearch", typoSearch)
        put("favoritesInDrawer", favoritesInDrawer)
        put("favoritesAlsoInList", favoritesAlsoInList)
        put("showRecentInstalls", showRecentInstalls)
        put("showUsageInDrawer", showUsageInDrawer)
        put("drawerSort", drawerSort.name)
        put("drawerLayout", drawerLayout.name)
        put("colourIcons", colourIcons)
        put("hidden", JSONArray(hidden.toList()))
        put("pinned", JSONArray(pinned))
        put("renames", JSONObject(renames))

        put("timersEnabled", timersEnabled)
        put("socialDefaultMin", socialDefaultMin)
        put("gameDefaultMin", gameDefaultMin)
        put("videoDefaultMin", videoDefaultMin)
        put("appLimits", JSONObject(appLimits))
        put("allowContinue", allowContinue)
        put("continueOptions", JSONArray(continueOptions))
        put("allowBypass", allowBypass)
        put("askAfterBypass", askAfterBypass)
        put("frictionSeconds", frictionSeconds)
        put("warnMinutes", warnMinutes)

        put("weeklyEnabled", weeklyEnabled)
        put("weekStartsMonday", weekStartsMonday)
        put("reviewHour", reviewHour)

        put("swipeDownNotifications", swipeDownNotifications)
        put("swipeUpSearch", swipeUpSearch)
        put("swipeRightSearch", swipeRightSearch)
        put("swipeRightApp", swipeRightApp)
        put("doubleTapLock", doubleTapLock)
    }

    companion object {
        /** Tolerant of missing keys, so older saved settings survive app updates. */
        /** Version of the stored settings. Bump it only when an old stored value has to be reinterpreted. */
        const val SCHEMA = 2

        fun fromJson(o: JSONObject): Settings {
            val d = Settings()
            return Settings(
                dark = o.optBoolean("dark", d.dark),
                font = enumOr(o.optString("font"), d.font),
                // A hand-edited backup can hold "NaN", which coerceIn would let through.
                textScale = o.optDouble("textScale", d.textScale.toDouble()).toFloat()
                    .let { if (it.isNaN()) d.textScale else it.coerceIn(0.8f, 1.4f) },
                hideStatusBar = o.optBoolean("hideStatusBar", d.hideStatusBar),
                launchAnimation = enumOr(o.optString("launchAnimation"), d.launchAnimation),
                showWallpaper = o.optBoolean("showWallpaper", d.showWallpaper),
                wallpaperStamp = o.optLong("wallpaperStamp", d.wallpaperStamp).coerceAtLeast(0L),
                wallpaperVisibility = o.optInt("wallpaperVisibility", d.wallpaperVisibility).coerceIn(10, 100),

                // Schema 2 made the split clock the default, at the owner's request. A ring stored by an
                // older version was the old default, not a choice, so it moves along once; the ring stays
                // one tap away in Settings. A stored "Plain" was a choice and is kept.
                clockStyle = enumOr(o.optString("clockStyle"), d.clockStyle)
                    .let { if (o.optInt("v", 1) < 2 && it == ClockStyle.RING) ClockStyle.SPLIT else it },
                splitSide = enumOr(o.optString("splitSide"), d.splitSide),
                ringMode = enumOr(o.optString("ringMode"), d.ringMode),
                clockTap = o.optString("clockTap", d.clockTap).ifEmpty { d.clockTap },
                timeFormat = enumOr(o.optString("timeFormat"), d.timeFormat),
                showSeconds = o.optBoolean("showSeconds", d.showSeconds),
                showDate = o.optBoolean("showDate", d.showDate),
                showCalendar = o.optBoolean("showCalendar", d.showCalendar),
                calendarKeys = calendarSelection(
                    o.optJSONArray("calendarKeys")?.strings()
                        // Stored before several calendars could be chosen: one key, or auto / all.
                        ?: listOf(
                            o.optString("calendarKey").ifEmpty {
                                // Written for a few hours by an earlier build as a bare personal-calendar id.
                                when (val legacy = o.optLong("calendarId", -1L)) {
                                    -1L -> CALENDAR_AUTO
                                    -2L -> CALENDAR_ALL
                                    else -> "p:$legacy"
                                }
                            },
                        ),
                ),
                compactCalendar = o.optBoolean("compactCalendar", d.compactCalendar),
                showWeekStrip = o.optBoolean("showWeekStrip", d.showWeekStrip),
                showMusic = o.optBoolean("showMusic", d.showMusic),
                musicAutoHide = o.optBoolean("musicAutoHide", d.musicAutoHide),
                showNote = o.optBoolean("showNote", d.showNote),
                note = o.optString("note", d.note),
                musicApp = o.optString("musicApp", d.musicApp),
                noteApp = o.optString("noteApp", d.noteApp),
                noteLink = o.optString("noteLink", d.noteLink),
                showTodo = o.optBoolean("showTodo", d.showTodo),
                // Not cleaned of expired tasks here: loading does not depend on the time (see SettingsStore.dropExpiredTodos).
                todos = TodoItem.listFromJson(o.optJSONArray("todos")),
                homeAlign = enumOr(o.optString("homeAlign"), d.homeAlign),
                favorites = o.optJSONArray("favorites").strings().take(MAX_FAVORITES),
                showFavoritesOnHome = o.optBoolean("showFavoritesOnHome", d.showFavoritesOnHome),
                // Settings from before had one alignment for the whole page: the fast apps keep it.
                favoriteAlign = enumOr(o.optString("favoriteAlign"), enumOr(o.optString("homeAlign"), d.homeAlign)),
                favoriteSize = enumOr(o.optString("favoriteSize"), d.favoriteSize),
                favoriteStyle = enumOr(o.optString("favoriteStyle"), d.favoriteStyle),
                colourFavoriteIcons = o.optBoolean("colourFavoriteIcons", d.colourFavoriteIcons),
                showShortcuts = o.optBoolean("showShortcuts", d.showShortcuts),
                leftShortcut = o.optString("leftShortcut", d.leftShortcut),
                rightShortcut = o.optString("rightShortcut", d.rightShortcut),

                showWidgetPage = o.optBoolean("showWidgetPage", d.showWidgetPage),
                // Not checked against the phone here: loading has no access to the widget service (see WidgetController.dropUnbound).
                widgets = WidgetSlot.listFromJson(o.optJSONArray("widgets")),

                autoKeyboard = o.optBoolean("autoKeyboard", d.autoKeyboard),
                autoLaunch = o.optBoolean("autoLaunch", d.autoLaunch),
                typoSearch = o.optBoolean("typoSearch", d.typoSearch),
                favoritesInDrawer = o.optBoolean("favoritesInDrawer", d.favoritesInDrawer),
                favoritesAlsoInList = o.optBoolean("favoritesAlsoInList", d.favoritesAlsoInList),
                showRecentInstalls = o.optBoolean("showRecentInstalls", d.showRecentInstalls),
                showUsageInDrawer = o.optBoolean("showUsageInDrawer", d.showUsageInDrawer),
                drawerSort = enumOr(o.optString("drawerSort"), d.drawerSort),
                drawerLayout = enumOr(o.optString("drawerLayout"), d.drawerLayout),
                colourIcons = o.optBoolean("colourIcons", d.colourIcons),
                hidden = o.optJSONArray("hidden").strings().toSet(),
                pinned = o.optJSONArray("pinned").strings().distinct(),
                renames = o.optJSONObject("renames").stringMap(),

                timersEnabled = o.optBoolean("timersEnabled", d.timersEnabled),
                socialDefaultMin = o.optInt("socialDefaultMin", d.socialDefaultMin),
                gameDefaultMin = o.optInt("gameDefaultMin", d.gameDefaultMin),
                videoDefaultMin = o.optInt("videoDefaultMin", d.videoDefaultMin),
                appLimits = o.optJSONObject("appLimits").intMap(),
                allowContinue = o.optBoolean("allowContinue", d.allowContinue),
                continueOptions = o.optJSONArray("continueOptions").ints().ifEmpty { d.continueOptions },
                allowBypass = o.optBoolean("allowBypass", d.allowBypass),
                askAfterBypass = o.optBoolean("askAfterBypass", d.askAfterBypass),
                frictionSeconds = o.optInt("frictionSeconds", d.frictionSeconds),
                warnMinutes = o.optInt("warnMinutes", d.warnMinutes),

                weeklyEnabled = o.optBoolean("weeklyEnabled", d.weeklyEnabled),
                weekStartsMonday = o.optBoolean("weekStartsMonday", d.weekStartsMonday),
                reviewHour = o.optInt("reviewHour", d.reviewHour).coerceIn(0, 23),

                swipeDownNotifications = o.optBoolean("swipeDownNotifications", d.swipeDownNotifications),
                swipeUpSearch = o.optBoolean("swipeUpSearch", d.swipeUpSearch),
                swipeRightSearch = o.optBoolean("swipeRightSearch", d.swipeRightSearch),
                swipeRightApp = o.optString("swipeRightApp", d.swipeRightApp),
                doubleTapLock = o.optBoolean("doubleTapLock", d.doubleTapLock),
            )
        }

        /**
         * Settings from a backup file written from [toJson], or null when [o] is not one. A backup from a
         * newer [SCHEMA] is refused too: this version could misread its values.
         */
        fun fromBackup(o: JSONObject): Settings? {
            // Every backup has these; a JSON file of anything else almost surely lacks them.
            if (o.optInt("v", -1) !in 1..SCHEMA || !o.has("dark") || !o.has("favorites")) return null
            return fromJson(o)
        }

        private inline fun <reified E : Enum<E>> enumOr(name: String?, fallback: E): E =
            enumValues<E>().firstOrNull { it.name == name } ?: fallback

        private fun JSONArray?.strings(): List<String> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotEmpty() } }

        private fun JSONArray?.ints(): List<Int> =
            if (this == null) emptyList() else (0 until length()).map { optInt(it) }.filter { it > 0 }

        private fun JSONObject?.stringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            val out = LinkedHashMap<String, String>()
            for (k in keys()) optString(k).takeIf { it.isNotEmpty() }?.let { out[k] = it }
            return out
        }

        private fun JSONObject?.intMap(): Map<String, Int> {
            if (this == null) return emptyMap()
            val out = LinkedHashMap<String, Int>()
            for (k in keys()) out[k] = optInt(k, 0)
            return out
        }
    }
}
