package com.focus.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focus.launcher.Graph
import com.focus.launcher.R
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.LauncherPage
import com.focus.launcher.data.ListKeyboard
import com.focus.launcher.data.Settings
import com.focus.launcher.data.Swipe
import com.focus.launcher.data.SwipeTarget
import com.focus.launcher.ui.components.AppPickerDialog
import com.focus.launcher.ui.components.ChoiceDialog
import com.focus.launcher.ui.components.FocusDialog
import com.focus.launcher.ui.components.MenuRow
import com.focus.launcher.ui.components.SettingRow
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.ToggleRow
import com.focus.launcher.ui.describe
import com.focus.launcher.ui.swipeArrow
import com.focus.launcher.ui.swipeLabel
import com.focus.launcher.ui.theme.LocalFocusColors

/** The pages in the order they are offered: the one that matters most first. */
private val PAGE_CHOICES = listOf(LauncherPage.ALL_APPS, LauncherPage.QUICK, LauncherPage.WIDGETS)

/** Sets [swipe] to [target], through the layout's rules. Check [com.focus.launcher.data.GestureLayout.allows] first. */
private fun setGesture(swipe: Swipe, target: SwipeTarget) =
    Graph.settings.update { it.copy(gestures = it.gestures.withTarget(swipe, target)) }

/** When the app list's search field takes the keyboard. Shared by Settings → Gestures and App drawer. */
@Composable
internal fun ListKeyboardRow(settings: Settings) {
    var choosing by remember { mutableStateOf(false) }
    SettingRow(
        stringResource(R.string.settings_list_keyboard),
        subtitle = stringResource(R.string.settings_list_keyboard_summary),
        value = stringResource(settings.listKeyboard.labelRes),
        onClick = { choosing = true },
    )
    if (choosing) {
        ChoiceDialog(
            stringResource(R.string.settings_list_keyboard),
            ListKeyboard.entries.map { it to stringResource(it.labelRes) },
            settings.listKeyboard,
            { choosing = false },
        ) { v -> Graph.settings.update { it.copy(listKeyboard = v) } }
    }
}

internal fun gesturePagesRoute(swipe: Swipe): String =
    if (swipe == Swipe.RIGHT) Routes.GESTURE_PAGES_RIGHT else Routes.GESTURE_PAGES_LEFT

/**
 * Settings → Gestures: what swiping right, left and up on the home screen does, one row each, and
 * the switches that are not about a page: swipe down, the keyboard, double tap.
 */
@Composable
internal fun GesturesPage(settings: Settings, apps: List<AppEntry>, status: SetupStatus, onBack: () -> Unit, go: (String) -> Unit) {
    var choosing by remember { mutableStateOf<Swipe?>(null) }
    Page(stringResource(R.string.settings_gestures), onBack) {
        Note(stringResource(R.string.settings_gestures_note))
        for (swipe in Swipe.entries) {
            SettingRow(swipeLabel(swipe), value = describe(settings.gestures[swipe], apps), onClick = { choosing = swipe })
        }
        ToggleRow(stringResource(R.string.settings_swipe_down_notifications), settings.swipeDownNotifications) { v ->
            Graph.settings.update { it.copy(swipeDownNotifications = v) }
        }
        // Also under App drawer. It is looked for here too: it is what swiping to the drawer does.
        ListKeyboardRow(settings)
        ToggleRow(
            stringResource(R.string.settings_double_tap_lock), settings.doubleTapLock,
            subtitle = stringResource(R.string.settings_double_tap_lock_summary),
        ) { v -> Graph.settings.update { it.copy(doubleTapLock = v) } }
        if (settings.doubleTapLock && !status.timerService) {
            Note(stringResource(R.string.settings_double_tap_service_off)) { go(Routes.SETUP) }
        }
    }

    choosing?.let { swipe ->
        SwipeTargetDialog(
            swipe, settings, apps,
            onDismiss = { choosing = null },
            onPages = {
                choosing = null
                go(gesturePagesRoute(swipe))
            },
        )
    }
}

/**
 * What [swipe] does: nothing, the web search, an app, or pages (a sequence sideways, one page up).
 * A choice that would leave all apps under no swipe is refused, and the dialog says why.
 */
@Composable
private fun SwipeTargetDialog(swipe: Swipe, settings: Settings, apps: List<AppEntry>, onDismiss: () -> Unit, onPages: () -> Unit) {
    val c = LocalFocusColors.current
    val gestures = settings.gestures
    val current = gestures[swipe]
    var refused by remember { mutableStateOf(false) }
    var pickingApp by remember { mutableStateOf(false) }

    fun choose(target: SwipeTarget) {
        if (!gestures.allows(swipe, target)) {
            refused = true
            return
        }
        setGesture(swipe, target)
        onDismiss()
    }

    if (pickingApp) {
        AppPickerDialog(
            title = swipeLabel(swipe),
            apps = apps.filter { it.key !in settings.hidden },
            onDismiss = onDismiss,
            onPick = { app -> choose(SwipeTarget.App(app.key)) },
        )
        return
    }

    FocusDialog(onDismiss, title = swipeLabel(swipe)) {
        if (refused) {
            T(
                stringResource(R.string.settings_gesture_keeps_all_apps),
                Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                size = 14.sp, color = c.fg, lineHeight = 20.sp,
            )
        }
        MenuRow(stringResource(R.string.settings_gesture_nothing), selected = current == SwipeTarget.None) { choose(SwipeTarget.None) }
        MenuRow(stringResource(R.string.settings_web_search), selected = current == SwipeTarget.WebSearch) { choose(SwipeTarget.WebSearch) }
        MenuRow(
            stringResource(R.string.settings_gesture_app),
            detail = (current as? SwipeTarget.App)?.let { describe(it, apps) },
            selected = current is SwipeTarget.App,
        ) {
            // Any app takes the swipe's place the same way the web search does: ask before the list.
            if (gestures.allows(swipe, SwipeTarget.WebSearch)) pickingApp = true else refused = true
        }
        if (swipe == Swipe.UP) {
            for (page in PAGE_CHOICES) {
                val target = SwipeTarget.Pages(listOf(page))
                MenuRow(stringResource(page.labelRes), selected = current == target) { choose(target) }
            }
        } else {
            MenuRow(
                stringResource(R.string.settings_gesture_pages),
                detail = (current as? SwipeTarget.Pages)?.let { describe(it, apps) },
                selected = current is SwipeTarget.Pages,
                onClick = onPages,
            )
        }
    }
}

/**
 * The sequence of pages of a sideways [swipe]: each further swipe the same way goes one page on.
 * Put in order with arrows, as the fast apps are. A page added here leaves the other side's
 * sequence; taking away the last place of all apps is refused, and the page says why.
 */
@Composable
internal fun GesturePagesPage(swipe: Swipe, settings: Settings, apps: List<AppEntry>, onBack: () -> Unit) {
    val c = LocalFocusColors.current
    val gestures = settings.gestures
    val current = gestures[swipe]
    val pages = (current as? SwipeTarget.Pages)?.pages.orEmpty()
    val other = if (swipe == Swipe.RIGHT) Swipe.LEFT else Swipe.RIGHT
    val otherPages = (gestures[other] as? SwipeTarget.Pages)?.pages.orEmpty()
    var adding by remember { mutableStateOf(false) }
    var refused by remember { mutableStateOf(false) }

    fun set(next: List<LauncherPage>) {
        val target = if (next.isEmpty()) SwipeTarget.None else SwipeTarget.Pages(next)
        if (!gestures.allows(swipe, target)) {
            refused = true
            return
        }
        refused = false
        setGesture(swipe, target)
    }

    fun move(index: Int, delta: Int) {
        val to = index + delta
        if (to !in pages.indices) return
        set(pages.toMutableList().apply { add(to, removeAt(index)) })
    }

    Page(stringResource(R.string.settings_gesture_pages_title, swipeLabel(swipe)), onBack) {
        Note(stringResource(R.string.settings_gesture_pages_note))
        if (current is SwipeTarget.WebSearch || current is SwipeTarget.App) {
            Note(stringResource(R.string.settings_gesture_replaces_action, describe(current, apps)))
        }
        if (refused) Note(stringResource(R.string.settings_gesture_keeps_all_apps))
        pages.forEachIndexed { i, page ->
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                T(stringResource(page.labelRes), Modifier.weight(1f).padding(vertical = 15.dp), size = 18.sp, maxLines = 1)
                T("↑", Modifier.clickable(enabled = i > 0) { move(i, -1) }.padding(12.dp), size = 18.sp, color = if (i > 0) c.fg else c.line)
                T("↓", Modifier.clickable(enabled = i < pages.lastIndex) { move(i, 1) }.padding(12.dp), size = 18.sp, color = if (i < pages.lastIndex) c.fg else c.line)
                T(stringResource(R.string.settings_remove), Modifier.clickable { set(pages - page) }.padding(12.dp), size = 14.sp, color = c.dim)
            }
        }
        if (pages.size < PAGE_CHOICES.size) {
            SettingRow(stringResource(R.string.settings_gesture_add_page), onClick = { adding = true })
        }
    }

    if (adding) {
        FocusDialog({ adding = false }, title = stringResource(R.string.settings_gesture_add_page)) {
            for (page in PAGE_CHOICES.filter { it !in pages }) {
                MenuRow(
                    stringResource(page.labelRes),
                    detail = if (page in otherPages) stringResource(R.string.settings_gesture_now_under, swipeArrow(other)) else null,
                ) {
                    adding = false
                    set(pages + page)
                }
            }
        }
    }
}
