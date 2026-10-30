package com.focus.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Rect
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import com.focus.launcher.R
import java.io.File
import java.io.IOException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The picture behind the home screen: the app's own copy of an image the user picked, upright,
 * scaled to the screen and cut to its shape. The copy outlives the original. It is not part of
 * the settings backup, where [Settings.wallpaperStamp] only records that a picture was chosen;
 * on a phone without the file the home screen simply has no wallpaper.
 */
object WallpaperStore {
    private const val TAG = "FocusWallpaper"
    private const val FILE_NAME = "wallpaper.jpg"
    private const val JPEG_QUALITY = 90

    /** No photo needs more; a bigger file is refused before anything is decoded. */
    private const val MAX_INPUT_BYTES = 40L * 1024 * 1024

    /** A small file can still claim enormous dimensions. 200 MP cameras exist, so a little above that. */
    private const val MAX_INPUT_PIXELS = 250_000_000L

    /** EXIF orientations that turn the picture on its side, swapping width and height. */
    private val TURNED = setOf(
        ExifInterface.ORIENTATION_TRANSPOSE, ExifInterface.ORIENTATION_ROTATE_90,
        ExifInterface.ORIENTATION_TRANSVERSE, ExifInterface.ORIENTATION_ROTATE_270,
    )

    /** What came of copying a picked picture. */
    sealed interface Import {
        /** [stamp] goes to [Settings.wallpaperStamp], which tells the home screen to load the new picture. */
        class Ok(val stamp: Long) : Import
        class Failed(@StringRes val messageRes: Int) : Import
    }

    /** A picture refused for a reason the user is told about. */
    private class Refused(@StringRes val messageRes: Int) : Exception()

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    fun exists(context: Context): Boolean = file(context).isFile

    /** Copies the picture at [uri] (from the photo picker) into the app's files. Blocking: call it off the main thread. */
    @Synchronized
    fun import(context: Context, uri: Uri): Import {
        val source = File(context.cacheDir, "wallpaper-source")
        return try {
            copyCapped(context, uri, source)
            val (screenWidth, screenHeight) = screenSize(context)
            val picture = decodeForScreen(source, screenWidth, screenHeight)
            try {
                save(context, picture)
            } finally {
                picture.recycle()
            }
            Import.Ok(System.currentTimeMillis())
        } catch (e: Refused) {
            Import.Failed(e.messageRes)
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "picked picture too large to decode", e)
            Import.Failed(R.string.settings_wallpaper_too_large)
        } catch (e: Exception) {
            // A broken or unsupported file: the decoders say so with assorted exceptions.
            Log.w(TAG, "picked picture could not be decoded", e)
            Import.Failed(R.string.settings_wallpaper_unreadable)
        } finally {
            source.delete()
        }
    }

    /**
     * The stored picture for the home screen, or null when there is none (never chosen, or a backup
     * restored on a phone without it) or it cannot be read. Blocking: call it off the main thread.
     */
    fun load(context: Context): Bitmap? {
        val file = file(context)
        if (!file.isFile) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                Log.w(TAG, "stored wallpaper is not a readable picture")
                return null
            }
            // Saved at screen size; only a changed screen resolution makes it bigger than needed.
            val (screenWidth, screenHeight) = screenSize(context)
            var sample = 1
            while (bounds.outWidth / sample > screenWidth * 2 || bounds.outHeight / sample > screenHeight * 2) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
                .also { if (it == null) Log.w(TAG, "stored wallpaper could not be decoded") }
        } catch (e: OutOfMemoryError) {
            Log.w(TAG, "stored wallpaper too large to load", e)
            null
        } catch (e: Exception) {
            // The home screen must come up whatever is wrong with the file: then without a wallpaper.
            Log.w(TAG, "stored wallpaper could not be loaded", e)
            null
        }
    }

    /** Deletes the stored picture; false when it could not be deleted. */
    @Synchronized
    fun remove(context: Context): Boolean {
        val file = file(context)
        return !file.exists() || file.delete()
    }

    /** Copies [uri] to [target], refusing anything over [MAX_INPUT_BYTES]. The provider is another app and may fail in any way. */
    private fun copyCapped(context: Context, uri: Uri, target: File) {
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw Refused(R.string.settings_wallpaper_read_failed)
            input.use { from ->
                target.outputStream().use { to ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = from.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_INPUT_BYTES) throw Refused(R.string.settings_wallpaper_too_large)
                        to.write(buffer, 0, count)
                    }
                }
            }
        } catch (e: Refused) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "picked picture could not be read", e)
            throw Refused(R.string.settings_wallpaper_read_failed)
        }
    }

    private fun decodeForScreen(source: File, screenWidth: Int, screenHeight: Int): Bitmap =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) decodeWithImageDecoder(source, screenWidth, screenHeight)
        else decodeWithBitmapFactory(source, screenWidth, screenHeight)

    /** Turns the picture upright by itself, reads HEIF too, and scales and cuts while decoding. */
    @RequiresApi(Build.VERSION_CODES.P)
    private fun decodeWithImageDecoder(source: File, screenWidth: Int, screenHeight: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
            checkSize(info.size.width, info.size.height)
            val frame = wallpaperFrame(info.size.width, info.size.height, screenWidth, screenHeight)
            decoder.setTargetSize(frame.width, frame.height)
            decoder.crop = Rect(frame.left, frame.top, frame.left + frame.cropWidth, frame.top + frame.cropHeight)
            // Pixels in ordinary memory: a hardware bitmap cannot be compressed to a file.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }

    /** Android 8: [BitmapFactory] leaves photos as the sensor stored them, so the EXIF orientation is applied here. */
    private fun decodeWithBitmapFactory(source: File, screenWidth: Int, screenHeight: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(source.path, bounds)
        checkSize(bounds.outWidth, bounds.outHeight)
        val orientation = exifOrientation(source)
        val turned = orientation in TURNED
        val width = if (turned) bounds.outHeight else bounds.outWidth
        val height = if (turned) bounds.outWidth else bounds.outHeight
        val frame = wallpaperFrame(width, height, screenWidth, screenHeight)
        // Halve while the result still covers the frame; the exact scale is applied below.
        var sample = 1
        while (width / (sample * 2) >= frame.width && height / (sample * 2) >= frame.height) sample *= 2
        val decoded = BitmapFactory.decodeFile(source.path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw Refused(R.string.settings_wallpaper_unreadable)
        val decodedWidth = if (turned) decoded.height else decoded.width
        val decodedHeight = if (turned) decoded.width else decoded.height
        val matrix = uprightMatrix(orientation)
        matrix.postScale(frame.width.toFloat() / decodedWidth, frame.height.toFloat() / decodedHeight)
        val scaled = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        // Rounding can leave the scaled picture a pixel off the frame: keep the cut inside it.
        val left = frame.left.coerceIn(0, scaled.width - 1)
        val top = frame.top.coerceIn(0, scaled.height - 1)
        return Bitmap.createBitmap(
            scaled, left, top,
            frame.cropWidth.coerceAtMost(scaled.width - left), frame.cropHeight.coerceAtMost(scaled.height - top),
        )
    }

    private fun checkSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) throw Refused(R.string.settings_wallpaper_unreadable)
        if (width.toLong() * height > MAX_INPUT_PIXELS) throw Refused(R.string.settings_wallpaper_too_large)
    }

    private fun exifOrientation(file: File): Int = try {
        ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    } catch (e: Exception) {
        // No EXIF that can be read (a PNG, for one): the picture is taken the way it is stored.
        Log.i(TAG, "no EXIF orientation in the picked picture", e)
        ExifInterface.ORIENTATION_NORMAL
    }

    /** The turn and mirror that bring a picture with this EXIF [orientation] upright. */
    private fun uprightMatrix(orientation: Int) = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(-90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(-90f)
        }
    }

    /** Writes [picture] beside the stored one, then swaps it in, so a failure never leaves half a file. */
    private fun save(context: Context, picture: Bitmap) {
        val temp = File(context.filesDir, "$FILE_NAME.tmp")
        try {
            val written = temp.outputStream().use { picture.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it) }
            if (!written || !temp.renameTo(file(context))) throw Refused(R.string.settings_wallpaper_save_failed)
        } catch (e: IOException) {
            Log.w(TAG, "wallpaper could not be saved", e)
            throw Refused(R.string.settings_wallpaper_save_failed)
        } finally {
            temp.delete() // still there only when something failed
        }
    }

    /** The screen in pixels, short side first: the home screen is portrait only. */
    private fun screenSize(context: Context): Pair<Int, Int> {
        var width = context.resources.displayMetrics.widthPixels
        var height = context.resources.displayMetrics.heightPixels
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // The whole screen, system bars included: the home screen draws under them.
            val windows = context.getSystemService(WindowManager::class.java)
            if (windows != null) {
                val bounds = windows.maximumWindowMetrics.bounds
                width = bounds.width()
                height = bounds.height()
            }
        }
        return min(width, height).coerceAtLeast(1) to max(width, height).coerceAtLeast(1)
    }
}

/** How a picture becomes a wallpaper: the size to scale it to, and the part of that to keep. */
internal data class WallpaperFrame(val width: Int, val height: Int, val left: Int, val top: Int, val cropWidth: Int, val cropHeight: Int)

/**
 * A [width] x [height] picture on a [screenWidth] x [screenHeight] screen: scaled to just cover it
 * (never enlarged), then the centre cut to the screen's proportions, as the home screen would show it.
 */
internal fun wallpaperFrame(width: Int, height: Int, screenWidth: Int, screenHeight: Int): WallpaperFrame {
    val scale = min(1.0, max(screenWidth.toDouble() / width, screenHeight.toDouble() / height))
    val w = (width * scale).roundToInt().coerceAtLeast(1)
    val h = (height * scale).roundToInt().coerceAtLeast(1)
    // Wider than the screen: full height, the middle of the width. Otherwise the other way round.
    val wider = w.toLong() * screenHeight > h.toLong() * screenWidth
    val cropWidth = if (wider) (h.toLong() * screenWidth / screenHeight).toInt().coerceIn(1, w) else w
    val cropHeight = if (wider) h else (w.toLong() * screenHeight / screenWidth).toInt().coerceIn(1, h)
    return WallpaperFrame(w, h, (w - cropWidth) / 2, (h - cropHeight) / 2, cropWidth, cropHeight)
}
