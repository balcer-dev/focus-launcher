package com.focus.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The widgets page: kept with the settings (and so in their backup) as ids and heights. Ids are the
 * phone's own; whether one still stands for a widget is checked by the launcher, not when loading.
 */
class WidgetSettingsTest {

    @Test fun `widgets survive a round trip, order and heights included`() {
        val slots = listOf(WidgetSlot(40, 500), WidgetSlot(3), WidgetSlot(17, WidgetSlot.MIN_HEIGHT_DP))
        val restored = Settings.fromJson(Settings(showWidgetPage = true, widgets = slots).toJson())
        assertEquals(slots, restored.widgets)
        assertEquals(true, restored.showWidgetPage)
    }

    @Test fun `settings from before the widgets page have it off and empty`() {
        val old = Settings.fromJson(Settings().toJson().apply { remove("showWidgetPage"); remove("widgets") })
        assertEquals(false, old.showWidgetPage)
        assertEquals(emptyList<WidgetSlot>(), old.widgets)
    }

    @Test fun `malformed widgets are left out and heights brought into range`() {
        val stored = Settings().toJson().put(
            "widgets",
            JSONArray()
                .put(JSONObject().put("id", 5).put("height", 300))
                .put(JSONObject().put("height", 300))
                .put(JSONObject().put("id", 0))
                .put(JSONObject().put("id", -4))
                .put(JSONObject().put("id", "nine"))
                .put(JSONObject().put("id", 6).put("height", 5))
                .put(JSONObject().put("id", 8).put("height", 99_999))
                .put(JSONObject().put("id", 9))
                .put(JSONObject().put("id", 5).put("height", 200))
                .put("not a widget"),
        )
        assertEquals(
            listOf(
                WidgetSlot(5, 300),
                WidgetSlot(6, WidgetSlot.MIN_HEIGHT_DP),
                WidgetSlot(8, WidgetSlot.MAX_HEIGHT_DP),
                WidgetSlot(9, WidgetSlot.DEFAULT_HEIGHT_DP),
            ),
            Settings.fromJson(stored).widgets,
        )
        assertEquals(emptyList<WidgetSlot>(), Settings.fromJson(Settings().toJson().put("widgets", "not a list")).widgets)
    }

    @Test fun `a long list is cut to the page's maximum`() {
        val many = JSONArray().apply { repeat(WidgetSlot.MAX_SLOTS + 5) { put(WidgetSlot(it + 1).toJson()) } }
        val loaded = Settings.fromJson(Settings().toJson().put("widgets", many)).widgets
        assertEquals(WidgetSlot.MAX_SLOTS, loaded.size)
        assertEquals(1, loaded.first().id)
    }

    @Test fun `a new widget starts between the default and the largest height`() {
        assertEquals(WidgetSlot.DEFAULT_HEIGHT_DP, WidgetSlot.initialHeight(40))
        assertEquals(250, WidgetSlot.initialHeight(250))
        assertEquals(WidgetSlot.MAX_HEIGHT_DP, WidgetSlot.initialHeight(5_000))
    }

    @Test fun `widgets move one place at a time and never off the list`() {
        val slots = listOf(WidgetSlot(1), WidgetSlot(2), WidgetSlot(3))
        assertEquals(listOf(2, 1, 3), slots.moved(2, -1).map { it.id })
        assertEquals(listOf(1, 3, 2), slots.moved(2, 1).map { it.id })
        assertSame(slots, slots.moved(1, -1))
        assertSame(slots, slots.moved(3, 1))
        assertSame(slots, slots.moved(42, 1))
    }

    @Test fun `heights change in steps within the limits`() {
        val slots = listOf(WidgetSlot(1, 200), WidgetSlot(2, 700))
        assertEquals(listOf(280, 700), slots.resized(1, WidgetSlot.HEIGHT_STEP_DP).map { it.heightDp })
        assertEquals(listOf(200, WidgetSlot.MAX_HEIGHT_DP), slots.resized(2, WidgetSlot.HEIGHT_STEP_DP).map { it.heightDp })
        assertEquals(listOf(WidgetSlot.MIN_HEIGHT_DP, 700), slots.resized(1, -WidgetSlot.HEIGHT_STEP_DP).map { it.heightDp })
    }

    @Test fun `ids that nothing shows are given back, except a widget being added`() {
        val slots = listOf(WidgetSlot(4), WidgetSlot(9))
        // 11 was left over (a backup restored over this page), 12 is being added right now.
        assertEquals(listOf(11), slots.unusedIds(intArrayOf(4, 9, 11, 12), inProgress = 12))
        assertEquals(listOf(11, 12), slots.unusedIds(intArrayOf(4, 9, 11, 12), inProgress = -1))
        assertEquals(emptyList<Int>(), slots.unusedIds(intArrayOf(4, 9), inProgress = -1))
        // Ids from another phone's backup that this phone's host does not hold: nothing to give back.
        assertEquals(emptyList<Int>(), listOf(WidgetSlot(500)).unusedIds(intArrayOf(), inProgress = -1))
    }
}
