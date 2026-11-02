package com.focus.launcher.data

import androidx.annotation.StringRes
import com.focus.launcher.R
import org.json.JSONArray
import org.json.JSONObject

/** A page that can stand in a swipe's sequence or under swipe up. The home screen is not one: it is always there. */
enum class LauncherPage(@StringRes val labelRes: Int) {
    WIDGETS(R.string.gestures_page_widgets),
    ALL_APPS(R.string.gestures_page_all_apps),
    QUICK(R.string.gestures_page_quick),
}

/** A swipe on the home screen. RIGHT is the finger moving right: the page beyond comes in from the left. */
enum class Swipe { RIGHT, LEFT, UP }

/** What a swipe on the home screen does. */
sealed interface SwipeTarget {
    data object None : SwipeTarget
    data object WebSearch : SwipeTarget

    /** Opens the app with this [AppEntry.key]. */
    data class App(val key: String) : SwipeTarget

    /**
     * Sideways: one page per further swipe the same way, nearest first. Up: exactly one page.
     * [GestureLayout.normalized] keeps it that way and turns an empty list into [None].
     */
    data class Pages(val pages: List<LauncherPage>) : SwipeTarget

    /**
     * What this does on a phone with [installed] apps: an app that is not there any more opens the
     * web search, as the swipe right did while it could only be an app or the web search.
     */
    fun resolvedAgainst(installed: Set<String>): SwipeTarget =
        if (this is App && key !in installed) WebSearch else this
}

/** Where a page stands: under [swipe], the [position]-th page that way (1 is one swipe away; swipe up has only 1). */
data class Place(val swipe: Swipe, val position: Int)

/**
 * Where [page] stands: in swipe left's sequence, else in swipe right's, else under swipe up; null
 * when no swipe leads to it. The sequences come first: from there swipe up only scrolls to it.
 */
fun GestureLayout.placeOf(page: LauncherPage): Place? {
    for (swipe in listOf(Swipe.LEFT, Swipe.RIGHT, Swipe.UP)) {
        val pages = (this[swipe] as? SwipeTarget.Pages)?.pages ?: continue
        val index = pages.indexOf(page)
        if (index >= 0) return Place(swipe, index + 1)
    }
    return null
}

/** What a tip about a swipe promises: [goal] (an action, or one page) after [times] swipes that way. */
data class SwipeTip(val times: Int, val goal: SwipeTarget)

/**
 * What the tip about [swipe] should promise, or null when nothing is under it and the tip is passed
 * over. A sequence that holds all apps one or two swipes away promises them, as the tip always did
 * (twice with the widgets page in between); any other sequence promises its first page.
 */
fun GestureLayout.tipFor(swipe: Swipe): SwipeTip? = when (val target = this[swipe]) {
    SwipeTarget.None -> null
    is SwipeTarget.Pages -> {
        val allApps = target.pages.indexOf(LauncherPage.ALL_APPS)
        if (swipe != Swipe.UP && allApps in 0..1) SwipeTip(allApps + 1, SwipeTarget.Pages(listOf(LauncherPage.ALL_APPS)))
        else target.pages.firstOrNull()?.let { SwipeTip(1, SwipeTarget.Pages(listOf(it))) }
    }
    else -> SwipeTip(1, target)
}

/**
 * What each swipe on the home screen does: [right], [left] and [up]. Every layout that is stored or
 * shown goes through [normalized], so the rules hold however it was made, in Settings, on the
 * welcome screen or by hand in a backup file: a page stands in the two sequences at most once,
 * swipe up holds one page, and all apps can always be reached.
 */
data class GestureLayout(val right: SwipeTarget, val left: SwipeTarget, val up: SwipeTarget) {

    operator fun get(swipe: Swipe): SwipeTarget = when (swipe) {
        Swipe.RIGHT -> right
        Swipe.LEFT -> left
        Swipe.UP -> up
    }

    /** True when some swipe leads to [page]: from a sequence, or from swipe up. */
    fun reaches(page: LauncherPage): Boolean = listOf(right, left, up).any { it is SwipeTarget.Pages && page in it.pages }

    fun normalized(): GestureLayout {
        val shaped = keepingShape()
        // With the app list nowhere, swipe up gets it: the way to it the launcher always had.
        return if (shaped.reaches(LauncherPage.ALL_APPS)) shaped else shaped.copy(up = SwipeTarget.Pages(listOf(LauncherPage.ALL_APPS)))
    }

    /**
     * Whether [swipe] may be set to [target]: not if that left no way to all apps. Without that
     * list no app outside the fast and pinned ones could be opened, and nothing searched for.
     */
    fun allows(swipe: Swipe, target: SwipeTarget): Boolean = set(swipe, target).keepingShape().reaches(LauncherPage.ALL_APPS)

    /**
     * This layout with [swipe] set to [target]. Pages it takes into a sideways sequence leave the
     * other one: the newer choice wins. Check [allows] first; here a layout without all apps gets
     * them under swipe up, as any other would.
     */
    fun withTarget(swipe: Swipe, target: SwipeTarget): GestureLayout = set(swipe, target).normalized()

    fun toJson(): JSONObject = JSONObject().apply {
        put(KEY_RIGHT, right.toJson())
        put(KEY_LEFT, left.toJson())
        put(KEY_UP, up.toJson())
    }

    /**
     * The four fields that stood for the gestures before this layout, worked out from it and written
     * next to it, so that a version from before restores a backup to something close.
     */
    fun putLegacyFields(o: JSONObject) {
        o.put("swipeRightSearch", right is SwipeTarget.WebSearch || right is SwipeTarget.App)
        o.put("swipeRightApp", (right as? SwipeTarget.App)?.key ?: "")
        o.put("showWidgetPage", reaches(LauncherPage.WIDGETS))
        o.put("swipeUpSearch", up == SwipeTarget.Pages(listOf(LauncherPage.ALL_APPS)))
    }

    private fun set(swipe: Swipe, target: SwipeTarget): GestureLayout {
        val taken = (target as? SwipeTarget.Pages)?.pages.orEmpty().toSet()
        return when (swipe) {
            Swipe.RIGHT -> copy(right = target, left = left.without(taken))
            Swipe.LEFT -> copy(left = target, right = right.without(taken))
            Swipe.UP -> copy(up = target)
        }
    }

    /** Every rule but the last one, which [normalized] adds: all apps reachable. */
    private fun keepingShape(): GestureLayout {
        val leftPages = (left as? SwipeTarget.Pages)?.pages.orEmpty().distinct()
        return GestureLayout(
            // Swipe left keeps a page that both sequences hold: that is where the pages always stood.
            right = right.cleaned().without(leftPages.toSet()),
            left = left.cleaned(),
            up = up.cleaned().let { if (it is SwipeTarget.Pages) SwipeTarget.Pages(it.pages.take(1)) else it },
        )
    }

    companion object {
        private const val KEY_RIGHT = "right"
        private const val KEY_LEFT = "left"
        private const val KEY_UP = "up"

        /** The gestures of a fresh install, the same as before they could be set. */
        val DEFAULT: GestureLayout = fromLegacy(swipeRightSearch = true, swipeRightApp = "", showWidgetPage = false, swipeUpSearch = true)

        /**
         * The layout stored under "gestures", or null when there is none (settings from before it).
         * Anything of the wrong shape reads as nothing under that swipe; not yet [normalized].
         */
        fun fromJson(o: JSONObject?): GestureLayout? {
            if (o == null) return null
            return GestureLayout(
                right = targetFromJson(o.optJSONObject(KEY_RIGHT)),
                left = targetFromJson(o.optJSONObject(KEY_LEFT)),
                up = targetFromJson(o.optJSONObject(KEY_UP)),
            )
        }

        /** The layout the four fields of before always gave: the migration table in the design note. */
        fun fromLegacy(swipeRightSearch: Boolean, swipeRightApp: String, showWidgetPage: Boolean, swipeUpSearch: Boolean) = GestureLayout(
            right = when {
                !swipeRightSearch -> SwipeTarget.None
                swipeRightApp.isEmpty() -> SwipeTarget.WebSearch
                else -> SwipeTarget.App(swipeRightApp)
            },
            left = SwipeTarget.Pages(
                if (showWidgetPage) listOf(LauncherPage.WIDGETS, LauncherPage.ALL_APPS) else listOf(LauncherPage.ALL_APPS),
            ),
            up = if (swipeUpSearch) SwipeTarget.Pages(listOf(LauncherPage.ALL_APPS)) else SwipeTarget.None,
        )

        private fun SwipeTarget.toJson(): JSONObject = when (this) {
            SwipeTarget.None -> JSONObject().put("type", "none")
            SwipeTarget.WebSearch -> JSONObject().put("type", "search")
            is SwipeTarget.App -> JSONObject().put("type", "app").put("key", key)
            is SwipeTarget.Pages -> JSONObject().put("type", "pages").put("pages", JSONArray(pages.map { it.name }))
        }

        private fun targetFromJson(o: JSONObject?): SwipeTarget = when (o?.optString("type")) {
            "search" -> SwipeTarget.WebSearch
            "app" -> SwipeTarget.App(o.optString("key"))
            "pages" -> SwipeTarget.Pages(o.optJSONArray("pages").pageNames())
            else -> SwipeTarget.None
        }

        private fun JSONArray?.pageNames(): List<LauncherPage> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { i -> LauncherPage.entries.firstOrNull { it.name == optString(i) } }

        /** One target on its own: no page twice in a sequence, no empty sequence, no app without a key. */
        private fun SwipeTarget.cleaned(): SwipeTarget = when (this) {
            is SwipeTarget.Pages -> pages.distinct().let { if (it.isEmpty()) SwipeTarget.None else SwipeTarget.Pages(it) }
            // A blank key was how "the web search" was stored before, next to the app it could be.
            is SwipeTarget.App -> if (key.isBlank()) SwipeTarget.WebSearch else this
            else -> this
        }

        /** This sequence without [pages]; nothing, once none is left. Anything else as it is. */
        private fun SwipeTarget.without(pages: Set<LauncherPage>): SwipeTarget {
            if (this !is SwipeTarget.Pages || pages.isEmpty()) return this
            val left = this.pages.filterNot { it in pages }
            return if (left.isEmpty()) SwipeTarget.None else SwipeTarget.Pages(left)
        }
    }
}
