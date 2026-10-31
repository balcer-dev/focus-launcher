package com.focus.launcher.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

/** The home-screen checklist: kept with the settings (and so in their backup), checked tasks dropped after a week. */
class TodoSettingsTest {

    private val now = 1_761_900_000_000L

    @Test fun `tasks survive a round trip, completion time included`() {
        // Checked long before today: loading must not drop it, only the cleanup does.
        val items = listOf(TodoItem("a", "Buy milk"), TodoItem("b", "Pay the bill", now))
        val restored = Settings.fromJson(Settings(showTodo = true, todos = items).toJson())
        assertEquals(items, restored.todos)
        assertEquals(true, restored.showTodo)
    }

    @Test fun `settings from before the checklist have it off and empty`() {
        val old = Settings.fromJson(Settings().toJson().apply { remove("showTodo"); remove("todos") })
        assertEquals(false, old.showTodo)
        assertEquals(emptyList<TodoItem>(), old.todos)
    }

    @Test fun `checked tasks expire after seven days and tasks still to do remain`() {
        val items = listOf(
            TodoItem("active", "Keep", checkedAt = null),
            TodoItem("ancient", "Keep, never checked"),
            TodoItem("old", "Remove", now - TodoItem.RETENTION_MS - 1),
            TodoItem("edge", "Remove, exactly seven days", now - TodoItem.RETENTION_MS),
            TodoItem("recent", "Can uncheck", now - TodoItem.RETENTION_MS + 1),
        )
        assertEquals(listOf("active", "ancient", "recent"), items.withoutExpiredTodos(now).map { it.id })
    }

    @Test fun `malformed tasks are left out`() {
        val stored = Settings().toJson().put(
            "todos",
            JSONArray()
                .put(JSONObject().put("id", "ok").put("text", "  Call mum  "))
                .put(JSONObject().put("text", "No id"))
                .put(JSONObject().put("id", "x".repeat(65)).put("text", "Id far too long"))
                .put(JSONObject().put("id", "blank").put("text", "   "))
                .put(JSONObject().put("id", "number").put("text", 42))
                .put(JSONObject().put("id", "bad-time").put("text", "Bad time").put("checkedAt", "yesterday"))
                .put(JSONObject().put("id", "negative").put("text", "Negative time").put("checkedAt", -5))
                .put(JSONObject().put("id", "ok").put("text", "Same id again"))
                .put("not a task"),
        )
        assertEquals(listOf(TodoItem("ok", "Call mum")), Settings.fromJson(stored).todos)
        assertEquals(emptyList<TodoItem>(), Settings.fromJson(Settings().toJson().put("todos", "not a list")).todos)
    }

    @Test fun `long tasks and long lists are cut to size`() {
        val many = JSONArray().apply { repeat(TodoItem.MAX_ITEMS + 5) { put(TodoItem("id$it", "Task $it").toJson()) } }
        val loaded = Settings.fromJson(Settings().toJson().put("todos", many)).todos
        assertEquals(TodoItem.MAX_ITEMS, loaded.size)
        assertEquals("id0", loaded.first().id)
        assertEquals(TodoItem.MAX_TEXT, TodoItem.create("x".repeat(TodoItem.MAX_TEXT + 50)).text.length)
    }

    @Test fun `a new task is one line`() {
        assertEquals("Buy milk and bread", TodoItem.create("  Buy milk\nand   bread ").text)
    }
}
