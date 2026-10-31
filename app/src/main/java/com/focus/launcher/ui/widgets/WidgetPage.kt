package com.focus.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.focus.launcher.Graph
import com.focus.launcher.R
import com.focus.launcher.data.WidgetSlot
import com.focus.launcher.data.moved
import com.focus.launcher.data.resized
import com.focus.launcher.ui.components.ConfirmDialog
import com.focus.launcher.ui.components.FocusDialog
import com.focus.launcher.ui.components.Label
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.UnderlinedField
import com.focus.launcher.ui.theme.LocalFocusColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The optional page between the home screen and the app list: Android widgets one under another,
 * each as wide as the page, on the launcher's plain background (the wallpaper is the home screen's
 * alone). Above each widget, its name and the controls to move it, change its height or remove it.
 * [isActive]: the page is on screen or being swiped to; its dialogs close as soon as it is not.
 */
@Composable
fun WidgetPage(slots: List<WidgetSlot>, controller: WidgetController, isActive: Boolean) {
    val c = LocalFocusColors.current
    var choosing by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<WidgetSlot?>(null) }
    // Home pressed, the screen gone off (the pager then goes home): nothing stays open over another page.
    LaunchedEffect(isActive) {
        if (!isActive) {
            choosing = false
            removing = null
        }
    }

    val listState = rememberLazyListState()
    // A new widget goes to the bottom of the page. Bring it into view, or it looks as if nothing was added.
    var lastCount by remember { mutableIntStateOf(slots.size) }
    LaunchedEffect(slots.size) {
        if (slots.size > lastCount) listState.animateScrollToItem(slots.size) // item 0 is the title row
        lastCount = slots.size
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "title") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Label(stringResource(R.string.widgets_title), Modifier.weight(1f).padding(start = 12.dp))
                if (controller.supported) {
                    T(stringResource(R.string.widgets_add), Modifier.clickable { choosing = true }.padding(12.dp), size = 15.sp)
                }
            }
        }
        val message = when {
            !controller.supported -> R.string.widgets_unsupported
            slots.isEmpty() -> R.string.widgets_empty
            else -> null
        }
        if (message != null) {
            item(key = "message") {
                T(stringResource(message), Modifier.padding(horizontal = 12.dp), size = 15.sp, color = c.dim, lineHeight = 22.sp)
            }
        }
        if (controller.supported) {
            itemsIndexed(slots, key = { _, slot -> slot.id }) { index, slot ->
                WidgetItem(slot, index, slots.size, controller) { removing = slot }
            }
        }
    }

    if (choosing) WidgetChooser(controller) { choosing = false }

    removing?.let { slot ->
        ConfirmDialog(
            title = stringResource(R.string.widgets_remove_title),
            message = stringResource(R.string.widgets_remove_message),
            confirmLabel = stringResource(R.string.widgets_remove),
            onDismiss = { removing = null },
        ) { controller.remove(slot.id) }
    }
}

/** One widget with its control row above it. [onRemove] asks before the widget goes. */
@Composable
private fun WidgetItem(slot: WidgetSlot, index: Int, count: Int, controller: WidgetController, onRemove: () -> Unit) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    // Null when the widget's app was uninstalled while the page is open; the next return to the launcher drops it.
    val info = remember(slot.id) { controller.info(slot.id) }
    val unavailable = stringResource(R.string.widgets_unavailable)
    val label = remember(info, unavailable) { info?.loadLabel(context.packageManager)?.takeIf { it.isNotBlank() } ?: unavailable }

    fun change(transform: (List<WidgetSlot>) -> List<WidgetSlot>) = Graph.settings.update { it.copy(widgets = transform(it.widgets)) }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            T(label, Modifier.weight(1f).padding(start = 12.dp), size = 14.sp, color = c.dim, maxLines = 1)
            Control("↑", R.string.widgets_move_up, enabled = index > 0) { change { it.moved(slot.id, -1) } }
            Control("↓", R.string.widgets_move_down, enabled = index < count - 1) { change { it.moved(slot.id, 1) } }
            Control("−", R.string.widgets_shorter, enabled = slot.heightDp > WidgetSlot.MIN_HEIGHT_DP) {
                change { it.resized(slot.id, -WidgetSlot.HEIGHT_STEP_DP) }
            }
            Control("+", R.string.widgets_taller, enabled = slot.heightDp < WidgetSlot.MAX_HEIGHT_DP) {
                change { it.resized(slot.id, WidgetSlot.HEIGHT_STEP_DP) }
            }
            T(stringResource(R.string.widgets_remove), Modifier.clickable(onClick = onRemove).padding(9.dp), size = 13.sp, color = c.dim)
        }
        if (info != null) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val widthDp = maxWidth.value.roundToInt()
                AndroidView(
                    factory = { viewContext -> controller.createView(viewContext, slot.id, info) },
                    modifier = Modifier.fillMaxWidth().height(slot.heightDp.dp),
                    // Runs again only when the size changes. Each call reaches the widget's app, which may redraw.
                    update = { view -> (view as? AppWidgetHostView)?.tellSize(widthDp, slot.heightDp) },
                )
            }
        }
    }
}

/** A one-glyph button of the control row; [description] is what a screen reader says for it. */
@Composable
private fun Control(glyph: String, @StringRes description: Int, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalFocusColors.current
    val spoken = stringResource(description)
    T(
        glyph,
        Modifier.semantics { contentDescription = spoken }.clickable(enabled = enabled, onClick = onClick).padding(9.dp),
        size = 18.sp, color = if (enabled) c.fg else c.line,
    )
}

/** The size the widget is drawn at, so that it can pick the layout that fits. */
private fun AppWidgetHostView.tellSize(widthDp: Int, heightDp: Int) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        updateAppWidgetSize(Bundle(), listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())))
    } else {
        @Suppress("DEPRECATION")
        updateAppWidgetSize(null, widthDp, heightDp, widthDp, heightDp)
    }
}

/** One row of the chooser: the app that offers the widget, the widget's name and its smallest size. */
private class WidgetChoice(
    val info: AppWidgetProviderInfo,
    val key: String,
    val app: String,
    val widget: String,
    val minWidthDp: Int,
    val minHeightDp: Int,
)

/** Every widget on the phone, by app and then by name, searchable; a tap adds one to the page. */
@Composable
private fun WidgetChooser(controller: WidgetController, onDismiss: () -> Unit) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    val density = LocalDensity.current.density
    var failed by remember { mutableStateOf(false) }
    // Null while the list is read: a label per widget means reading every app's resources.
    val choices by produceState<List<WidgetChoice>?>(null) {
        val read = withContext(Dispatchers.IO) { controller.providers()?.let { choicesOf(it, context.packageManager, density) } }
        if (read == null) failed = true else value = read
    }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    val shown = remember(choices, query.text) {
        val all = choices.orEmpty()
        val term = query.text.trim()
        if (term.isEmpty()) all else all.filter { it.app.contains(term, ignoreCase = true) || it.widget.contains(term, ignoreCase = true) }
    }
    FocusDialog(onDismiss, title = stringResource(R.string.widgets_choose_title), tall = true) {
        val searchFocus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        UnderlinedField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.widgets_search),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp).focusRequester(searchFocus),
            imeAction = ImeAction.Search,
        )
        val message = when {
            failed -> R.string.widgets_list_failed
            choices == null -> R.string.widgets_loading
            choices.isNullOrEmpty() -> R.string.widgets_none_installed
            shown.isEmpty() -> R.string.widgets_no_match
            else -> null
        }
        LazyColumn(Modifier.weight(1f)) {
            if (message != null) item(key = "message") { T(stringResource(message), Modifier.padding(24.dp), color = c.dim) }
            items(shown, key = { it.key }) { choice ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            controller.add(choice.info)
                        }
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                ) {
                    T(choice.app, size = 17.sp, maxLines = 1)
                    T(
                        stringResource(R.string.widgets_choice_detail, choice.widget, choice.minWidthDp, choice.minHeightDp),
                        size = 14.sp, color = c.dim, maxLines = 2,
                    )
                }
            }
        }
        // Inside the dialog: its field only exists once the dialog's own window has composed it.
        LaunchedEffect(Unit) {
            searchFocus.requestFocus()
            keyboard?.show()
        }
    }
}

/** The chooser's rows for [providers]: each once, sorted by app and then by widget name. Blocking. */
private fun choicesOf(providers: List<AppWidgetProviderInfo>, pm: PackageManager, density: Float): List<WidgetChoice> {
    val appNames = HashMap<String, String>()
    return providers.map { info ->
        val packageName = info.provider.packageName
        val app = appNames.getOrPut(packageName) {
            try {
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                packageName // uninstalled since the list was read; adding it will then fail with a message
            }
        }
        WidgetChoice(
            info = info,
            key = "${info.provider.flattenToString()}#${info.profile.hashCode()}",
            app = app,
            widget = info.loadLabel(pm)?.takeIf { it.isNotBlank() } ?: app,
            minWidthDp = (info.minWidth / density).roundToInt().coerceAtLeast(0),
            minHeightDp = (info.minHeight / density).roundToInt().coerceAtLeast(0),
        )
    }
        .distinctBy { it.key } // the list's keys must be unique
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, WidgetChoice::app).thenBy(String.CASE_INSENSITIVE_ORDER, WidgetChoice::widget))
}
