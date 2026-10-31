package com.focus.launcher.data

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App icons for the optional grid view of the app list; the plain list never asks for one.
 * Read through [LauncherApps] off the main thread, drawn once at the size shown and kept in
 * memory up to [MAX_BYTES], so scrolling back costs nothing. Nothing is written to disk.
 * Work-profile apps get the system's badged icon, as on the phone's own launcher.
 */
class AppIcons(context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    private data class Key(val appKey: String, val packageName: String, val user: UserHandle, val sizePx: Int)

    private val cache = object : LruCache<Key, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: Key, value: Bitmap): Int = value.allocationByteCount
    }

    // Two at a time keep up with a fling without a burst of threads fighting the UI for the CPU.
    private val loader = Dispatchers.IO.limitedParallelism(2)

    // A new or updated app can bring a new icon: forget the old one, the next look reads it again.
    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String?, user: UserHandle?) = forget(packageName, user)
        override fun onPackageAdded(packageName: String?, user: UserHandle?) = forget(packageName, user)
        override fun onPackageChanged(packageName: String?, user: UserHandle?) = forget(packageName, user)
        override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            packageNames?.forEach { forget(it, user) }
        }
        override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
            packageNames?.forEach { forget(it, user) }
        }
    }

    init {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    /** The icon if it is already in memory. Cheap enough for the main thread. */
    fun cached(app: AppEntry, sizePx: Int): Bitmap? = cache.get(key(app, sizePx))

    /** The icon of [app], [sizePx] square, or null when the system has none to give. */
    suspend fun load(app: AppEntry, sizePx: Int): Bitmap? = withContext(loader) {
        val key = key(app, sizePx)
        cache.get(key) ?: read(app, key.sizePx)?.also { cache.put(key, it) }
    }

    /** Lets go of every icon; called when the system is short of memory. */
    fun trimMemory() = cache.evictAll()

    private fun key(app: AppEntry, sizePx: Int) = Key(app.key, app.packageName, app.user, sizePx.coerceIn(1, MAX_SIZE_PX))

    private fun read(app: AppEntry, sizePx: Int): Bitmap? = try {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(app.component)
        val info = launcherApps.resolveActivity(intent, app.user)
        if (info == null) {
            Log.w(TAG, "no launcher activity left for ${app.key}")
            null
        } else {
            // Badged: a work-profile app carries the system's briefcase over its icon.
            info.getBadgedIcon(0).toBitmap(sizePx, sizePx)
        }
    } catch (e: OutOfMemoryError) {
        Log.w(TAG, "no memory for the icon of ${app.key}", e)
        null
    } catch (e: Exception) {
        // A paused work profile, an app removed a moment ago, a broken icon resource.
        Log.w(TAG, "icon of ${app.key} could not be loaded", e)
        null
    }

    private fun forget(packageName: String?, user: UserHandle?) {
        if (packageName == null) return
        for (key in cache.snapshot().keys) {
            if (key.packageName == packageName && (user == null || key.user == user)) cache.remove(key)
        }
    }

    private companion object {
        const val TAG = "FocusIcons"

        /** A few hundred icons at the grid's size; never more than a sixteenth of the heap. */
        val MAX_BYTES = minOf(Runtime.getRuntime().maxMemory() / 16, 12L * 1024 * 1024).toInt()

        /** Far above any phone's 40 dp; guards against a size computed from a broken density. */
        const val MAX_SIZE_PX = 512
    }
}
