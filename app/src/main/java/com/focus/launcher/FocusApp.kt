package com.focus.launcher

import android.app.Application
import com.focus.launcher.data.CalendarRepository
import com.focus.launcher.service.WeeklyReview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class FocusApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
        Graph.apps // start scanning installed apps right away

        WeeklyReview.ensureChannel(this)
        // A launcher is started at boot, so re-arming here also covers reboots.
        WeeklyReview.schedule(this)

        Graph.scope.launch(Dispatchers.IO) {
            Graph.limits.prune(LocalDate.now().minusDays(90))
        }
    }

    /**
     * A launcher lives for weeks, mostly behind other apps. Under real memory pressure, drop
     * whatever can be rebuilt on the next visit. Deliberately not on TRIM_MEMORY_UI_HIDDEN: that
     * fires every single time an app is opened from the launcher, and emptying the caches then
     * would make each return home redo the very work they exist to avoid.
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_BACKGROUND) {
            Graph.apps.trimMemory()
            Graph.usage.trimMemory()
            Graph.trimIcons()
            CalendarRepository.invalidate()
        }
    }
}
