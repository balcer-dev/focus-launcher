package com.focus.launcher

import android.app.Application
import com.focus.launcher.data.AppIcons
import com.focus.launcher.data.AppRepository
import com.focus.launcher.data.AppState
import com.focus.launcher.data.LimitManager
import com.focus.launcher.data.SettingsStore
import com.focus.launcher.data.UsageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Hand-rolled service locator. The launcher activities and the accessibility service share one
 * process, so these singletons are the shared state between them.
 */
object Graph {
    lateinit var app: Application
        private set

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings: SettingsStore by lazy { SettingsStore(app) }
    val apps: AppRepository by lazy { AppRepository(app, settings, scope) }
    val usage: UsageRepository by lazy { UsageRepository(app) { apps } }
    val limits: LimitManager by lazy { LimitManager(app, settings, apps) }
    val state: AppState by lazy { AppState(app) }

    private val iconsLazy = lazy { AppIcons(app) }

    /** Created on first use: only the optional grid view of the app list loads icons. */
    val icons: AppIcons by iconsLazy

    /** Empties the icon cache if there is one, without creating it just for that. */
    fun trimIcons() {
        if (iconsLazy.isInitialized()) icons.trimMemory()
    }

    fun init(application: Application) {
        app = application
    }
}
