package com.focus.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** One task of the home-screen checklist. [checkedAt] is when it was checked (ms); null while it is still to do. */
data class TodoItem(val id: String, val text: String, val checkedAt: Long? = null) {

    fun toJson(): JSONObject = JSONObject().put("id", id).put("text", text).apply { checkedAt?.let { put("checkedAt", it) } }

    companion object {
        /** A checked task stays this long, so that a tap by mistake can be undone; then it is dropped. */
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

        /** Longest task kept, in characters. A task is one line on the home screen. */
        const val MAX_TEXT = 200

        /** Most tasks kept, checked ones included. */
        const val MAX_ITEMS = 100

        /** Ids are UUIDs; anything much longer did not come from here. */
        private const val MAX_ID = 64

        private val WHITESPACE = Regex("\\s+")

        fun create(text: String) = TodoItem(UUID.randomUUID().toString(), oneLine(text))

        /** The task stored in [o], or null when it is malformed: no id, no text, or a completion time that is not one. */
        fun fromJson(o: JSONObject): TodoItem? {
            val id = (o.opt("id") as? String)?.trim().orEmpty()
            val text = oneLine((o.opt("text") as? String).orEmpty())
            if (id.isEmpty() || id.length > MAX_ID || text.isEmpty()) return null
            val checkedAt = when {
                o.isNull("checkedAt") -> null
                else -> o.optLong("checkedAt", -1L).takeIf { it > 0L } ?: return null
            }
            return TodoItem(id, text, checkedAt)
        }

        /** The tasks stored in [array]: malformed ones and repeated ids left out, at most [MAX_ITEMS]. */
        fun listFromJson(array: JSONArray?): List<TodoItem> {
            if (array == null) return emptyList()
            return (0 until array.length())
                .mapNotNull { i -> array.optJSONObject(i)?.let { fromJson(it) } }
                .distinctBy { it.id }
                .take(MAX_ITEMS)
        }

        /** Line breaks and runs of spaces become one space, and the text is cut to [MAX_TEXT], never in the middle of a character. */
        private fun oneLine(text: String): String = text.replace(WHITESPACE, " ").trim().take(MAX_TEXT)
            .let { if (it.lastOrNull()?.isHighSurrogate() == true) it.dropLast(1) else it }.trimEnd()
    }
}

/**
 * The tasks without those checked [TodoItem.RETENTION_MS] or longer before [now] (ms). Tasks still
 * to do stay whatever their age. [now] is passed in, so the cut is the same in a test as on the phone.
 */
fun List<TodoItem>.withoutExpiredTodos(now: Long): List<TodoItem> =
    filter { it.checkedAt == null || it.checkedAt > now - TodoItem.RETENTION_MS }
