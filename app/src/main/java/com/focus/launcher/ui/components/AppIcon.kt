package com.focus.launcher.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.focus.launcher.Graph
import com.focus.launcher.data.AppEntry

/**
 * The app's icon, [side] square, the badged one for a work app, read in the background by
 * [com.focus.launcher.data.AppIcons]. Grey unless [colour] is on; a [dimmed] one, of an app whose
 * time is up, is grey in any case. Until it arrives its space stays empty; when the system has no
 * icon to give, [missing] is drawn in its place.
 */
@Composable
internal fun AppIcon(
    app: AppEntry,
    side: Dp,
    dimmed: Boolean,
    colour: Boolean,
    contentDescription: String? = null,
    missing: @Composable () -> Unit = { Spacer(Modifier.size(side)) },
) {
    val sizePx = with(LocalDensity.current) { side.roundToPx() }
    // updatedAt is a key too: an updated app can bring a new icon.
    var icon by remember(app.key, app.updatedAt, sizePx) { mutableStateOf(Graph.icons.cached(app, sizePx)) }
    var none by remember(app.key, app.updatedAt, sizePx) { mutableStateOf(false) }
    LaunchedEffect(app.key, app.updatedAt, sizePx) {
        if (icon == null) {
            val loaded = Graph.icons.load(app, sizePx)
            if (loaded != null) icon = loaded else none = true
        }
    }
    val bitmap = icon
    when {
        bitmap != null -> Image(
            remember(bitmap) { bitmap.asImageBitmap() },
            contentDescription = contentDescription,
            modifier = Modifier.size(side),
            alpha = if (dimmed) 0.35f else 1f,
            colorFilter = if (colour && !dimmed) null else GREYSCALE,
        )
        none -> missing()
        else -> Spacer(Modifier.size(side))
    }
}
