package com.focus.launcher.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * One Android widget on the widgets page: the id the phone's widget service gave it when it was
 * added, and how tall it is drawn. It is always as wide as the page. The id only means something
 * on the phone that gave it out; see [Settings.widgets].
 */
data class WidgetSlot(val id: Int, val heightDp: Int = DEFAULT_HEIGHT_DP) {

    fun toJson(): JSONObject = JSONObject().put("id", id).put("height", heightDp)

    companion object {
        const val MIN_HEIGHT_DP = 120
        const val MAX_HEIGHT_DP = 720

        /** A new widget starts at least this tall, whatever smaller minimum it asks for. */
        const val DEFAULT_HEIGHT_DP = 180

        /** One tap on − or + changes the height by this much. */
        const val HEIGHT_STEP_DP = 80

        /** Most widgets on the page. */
        const val MAX_SLOTS = 20

        /** The widget stored in [o], or null when it has no usable id. A height out of range is brought into it. */
        fun fromJson(o: JSONObject): WidgetSlot? {
            val id = o.optInt("id", 0)
            if (id <= 0) return null // 0 is AppWidgetManager.INVALID_APPWIDGET_ID
            return WidgetSlot(id, o.optInt("height", DEFAULT_HEIGHT_DP).coerceIn(MIN_HEIGHT_DP, MAX_HEIGHT_DP))
        }

        /** The widgets stored in [array]: malformed ones and repeated ids left out, at most [MAX_SLOTS]. */
        fun listFromJson(array: JSONArray?): List<WidgetSlot> {
            if (array == null) return emptyList()
            return (0 until array.length())
                .mapNotNull { i -> array.optJSONObject(i)?.let { fromJson(it) } }
                .distinctBy { it.id }
                .take(MAX_SLOTS)
        }

        /** The height a new widget starts at, from the smallest height it asks for (in dp). */
        fun initialHeight(minHeightDp: Int): Int = minHeightDp.coerceIn(DEFAULT_HEIGHT_DP, MAX_HEIGHT_DP)
    }
}

/** The list with the widget [id] moved [by] places (negative: up). Unchanged when that would leave the list. */
fun List<WidgetSlot>.moved(id: Int, by: Int): List<WidgetSlot> {
    val from = indexOfFirst { it.id == id }
    val to = from + by
    if (from < 0 || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

/** The list with the widget [id] made [byDp] taller (negative: shorter), within the page's limits. */
fun List<WidgetSlot>.resized(id: Int, byDp: Int): List<WidgetSlot> = map {
    if (it.id == id) it.copy(heightDp = (it.heightDp + byDp).coerceIn(WidgetSlot.MIN_HEIGHT_DP, WidgetSlot.MAX_HEIGHT_DP)) else it
}

/**
 * The ids among [hostIds] (every id the launcher's widget host holds) that none of these widgets
 * uses, so they can be given back to the system. [inProgress] is a widget being added right now,
 * not on the page yet; -1 for none.
 */
fun List<WidgetSlot>.unusedIds(hostIds: IntArray, inProgress: Int): List<Int> {
    val used = mapTo(HashSet()) { it.id }
    return hostIds.filter { it > 0 && it !in used && it != inProgress }
}
