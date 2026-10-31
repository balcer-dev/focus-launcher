package com.focus.launcher.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * Single source of truth for [Settings]. The launcher UI, the settings screens and the
 * accessibility service all live in one process, so an in-memory StateFlow backed by
 * SharedPreferences keeps every one of them in sync without any IPC.
 */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("focus_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())

    val flow: StateFlow<Settings> = _flow.asStateFlow()
    val value: Settings get() = _flow.value

    @Synchronized
    fun update(transform: (Settings) -> Settings) {
        val next = transform(_flow.value)
        if (next == _flow.value) return
        _flow.value = next
        prefs.edit { putString(KEY, next.toJson().toString()) }
    }

    /** Drops the to-do tasks checked [TodoItem.RETENTION_MS] or longer before [now] (ms). Writes nothing when none is due. */
    fun dropExpiredTodos(now: Long) = update { it.copy(todos = it.todos.withoutExpiredTodos(now)) }

    /**
     * Every setting as indented JSON, for a backup file; read back with [Settings.fromBackup].
     * Permissions and screen time history are not settings, so they stay out of it.
     */
    fun exportBackup(): String = value.toJson().toString(2)

    private fun load(): Settings {
        val raw = prefs.getString(KEY, null) ?: return Settings()
        return try {
            Settings.fromJson(JSONObject(raw))
        } catch (_: Exception) {
            Settings()
        }
    }

    private companion object {
        const val KEY = "settings_json"
    }
}
