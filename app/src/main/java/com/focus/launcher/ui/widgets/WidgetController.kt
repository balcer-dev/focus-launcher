package com.focus.launcher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import com.focus.launcher.Graph
import com.focus.launcher.R
import com.focus.launcher.data.WidgetSlot
import com.focus.launcher.data.unusedIds
import kotlin.math.roundToInt

/**
 * The widget host behind the widgets page, one per [com.focus.launcher.MainActivity]. Binds a new
 * widget (asking the user first when the system wants that), opens the widget's own setup screen
 * when it has one, and gives every id back to the system as soon as nothing shows it any more:
 * a cancelled permission or setup, a removed widget, ids left over from a restored backup. Which
 * widgets the page shows, and how tall, is [com.focus.launcher.data.Settings.widgets].
 */
class WidgetController(private val activity: Activity) {
    // Null on a phone without a widget service (FEATURE_APP_WIDGETS); everything here then does nothing.
    private val manager: AppWidgetManager? = if (isSupported(activity)) AppWidgetManager.getInstance(activity) else null
    private val host: AppWidgetHost? = manager?.let { AppWidgetHost(activity, HOST_ID) }
    private var listening = false

    /** False on a phone that offers no widgets to other launchers; the page says so. */
    val supported: Boolean get() = host != null

    /**
     * True from handing the user to a system screen (the permission to bind, the widget's setup)
     * until its answer. The launcher then stays on the widgets page instead of going back home.
     */
    var awaitingResult = false
        private set

    private var pendingId: Int
        get() = Graph.state.pendingWidgetId
        set(value) {
            Graph.state.pendingWidgetId = value
        }

    /** Widgets only redraw while their host listens. Tied to the activity's start and stop. */
    fun startListening() {
        val host = host ?: return
        if (listening) return
        host.startListening()
        listening = true
    }

    fun stopListening() {
        if (!listening) return
        host?.stopListening()
        listening = false
    }

    /** What the widget [id] is, or null when this phone has no such widget (any more). */
    fun info(id: Int): AppWidgetProviderInfo? = manager?.getAppWidgetInfo(id)

    /** The view that draws the widget [id]; an empty one on a phone without widgets, which never shows any. */
    fun createView(context: Context, id: Int, info: AppWidgetProviderInfo): View =
        host?.createView(context, id, info) ?: View(context)

    /**
     * Every widget the apps of this phone offer, or null when the list could not be read. Blocking
     * (it asks the system): call it off the main thread.
     */
    fun providers(): List<AppWidgetProviderInfo>? {
        val manager = manager ?: return emptyList()
        return try {
            manager.installedProviders.orEmpty()
        } catch (e: RuntimeException) {
            Log.w(TAG, "widget list could not be read", e)
            null
        }
    }

    /** Starts adding [info] to the bottom of the page: bind it (with the user's permission if needed), then its setup, if any. */
    fun add(info: AppWidgetProviderInfo) {
        val host = host ?: return
        val manager = manager ?: return
        if (Graph.settings.value.widgets.size >= WidgetSlot.MAX_SLOTS) {
            toast(R.string.widgets_full)
            return
        }
        // An earlier attempt whose answer never came: its id is of no use any more.
        pendingId.takeIf { it > 0 }?.let { discard(it) }
        var id = -1
        try {
            id = host.allocateAppWidgetId()
            pendingId = id
            if (manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)) {
                configureOrAdd(id)
            } else {
                // The system asks the user once per app (with "always allow" on offer); the answer comes to onActivityResult.
                val ask = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                awaitingResult = true
                activity.startActivityForResult(ask, BIND_REQUEST)
            }
        } catch (e: Exception) {
            // No permission screen on this phone, the widget's app gone meanwhile, or the system refused.
            Log.w(TAG, "widget ${info.provider} could not be added", e)
            awaitingResult = false
            if (id > 0) discard(id)
            toast(R.string.widgets_add_failed)
        }
    }

    /** The answer of a screen opened by [add]. False when [requestCode] is not one of ours. */
    fun onActivityResult(requestCode: Int, resultCode: Int): Boolean {
        if (!owns(requestCode)) return false
        awaitingResult = false
        // Our own id, not one read from the answer: the other app's screen could return anything there.
        val id = pendingId
        if (id <= 0) return true // already given back (see dropUnbound): nothing left to finish
        when {
            // The user said no to the permission, or backed out of the widget's setup.
            resultCode != Activity.RESULT_OK -> discard(id)
            requestCode == BIND_REQUEST -> configureOrAdd(id)
            else -> {
                val info = info(id)
                if (info == null) {
                    discard(id)
                    toast(R.string.widgets_add_failed)
                } else {
                    finishAdding(id, info)
                }
            }
        }
        return true
    }

    /** Home was pressed while a widget was being added: the launcher no longer holds the widgets page for the answer. */
    fun onHomePressed() {
        awaitingResult = false
    }

    /** Takes the widget [id] off the page and gives its id back. */
    fun remove(id: Int) {
        Graph.settings.update { s -> s.copy(widgets = s.widgets.filterNot { it.id == id }) }
        host?.deleteAppWidgetId(id)
    }

    /**
     * Keeps the page to widgets this phone really has, and gives back every id that nothing shows.
     * Run on each return to the launcher: ids restored from another phone's backup, and widgets
     * whose app was uninstalled, resolve to nothing here.
     */
    fun dropUnbound() {
        val host = host ?: return
        val manager = manager ?: return
        try {
            val gone = Graph.settings.value.widgets.filter { manager.getAppWidgetInfo(it.id) == null }.mapTo(HashSet()) { it.id }
            if (gone.isNotEmpty()) Graph.settings.update { s -> s.copy(widgets = s.widgets.filterNot { it.id in gone }) }
            val inProgress = if (awaitingResult) pendingId else -1
            val unused = Graph.settings.value.widgets.unusedIds(host.appWidgetIds, inProgress)
            unused.forEach(host::deleteAppWidgetId)
            if (pendingId in unused) pendingId = -1
        } catch (e: RuntimeException) {
            // The widget service did not answer. Nothing on screen depends on this; it runs again on the next return.
            Log.w(TAG, "widget ids could not be checked", e)
        }
    }

    private fun configureOrAdd(id: Int) {
        val host = host ?: return
        val info = info(id)
        if (info == null) {
            discard(id)
            toast(R.string.widgets_add_failed)
            return
        }
        if (info.configure == null) {
            finishAdding(id, info)
            return
        }
        try {
            // Through the host, not a plain intent: the system can then open a setup screen the widget's app does not export.
            awaitingResult = true
            host.startAppWidgetConfigureActivityForResult(activity, id, 0, CONFIGURE_REQUEST, null)
        } catch (e: Exception) {
            Log.w(TAG, "setup of widget ${info.provider} could not be opened", e)
            awaitingResult = false
            discard(id)
            toast(R.string.widgets_configure_failed)
        }
    }

    private fun finishAdding(id: Int, info: AppWidgetProviderInfo) {
        // minHeight is in pixels of this screen.
        val minHeightDp = (info.minHeight / activity.resources.displayMetrics.density).roundToInt()
        val slot = WidgetSlot(id, WidgetSlot.initialHeight(minHeightDp))
        Graph.settings.update { s -> s.copy(widgets = s.widgets.filterNot { it.id == id } + slot) }
        pendingId = -1
    }

    /** Gives [id] back to the system and forgets it was being added. */
    private fun discard(id: Int) {
        host?.deleteAppWidgetId(id)
        if (pendingId == id) pendingId = -1
    }

    private fun toast(@StringRes message: Int) {
        Toast.makeText(activity, activity.getString(message), Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "FocusWidgets"

        /** Fixed for good: the widget service keeps this app's widgets under this number. */
        private const val HOST_ID = 19427
        private const val BIND_REQUEST = 6103
        private const val CONFIGURE_REQUEST = 6102

        /** Whether this phone has a widget service at all. */
        fun isSupported(context: Context): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)

        /** Whether an activity result with [requestCode] belongs to a widget being added. */
        fun owns(requestCode: Int): Boolean = requestCode == BIND_REQUEST || requestCode == CONFIGURE_REQUEST
    }
}
