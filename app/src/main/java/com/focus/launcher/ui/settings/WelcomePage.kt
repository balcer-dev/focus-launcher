package com.focus.launcher.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focus.launcher.Graph
import com.focus.launcher.R
import com.focus.launcher.data.Settings
import com.focus.launcher.data.Swipe
import com.focus.launcher.data.SwipeTarget
import com.focus.launcher.ui.components.AppPickerDialog
import com.focus.launcher.ui.components.FocusButton
import com.focus.launcher.ui.components.SettingRow
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.VSpace
import com.focus.launcher.ui.describe
import com.focus.launcher.ui.theme.LocalFocusColors

/**
 * The first thing a new user sees, once: the name, what it is for, and two ways on. One screen and
 * no more: a many-page tour was written first and thrown out as far too much to read. What Focus
 * can do is taught afterwards, on the home screen, one line at a time ([com.focus.launcher.data.Tip]).
 * It is shown on the first start, and again whenever asked for from About; never on the way to the
 * home screen: a launcher is opened dozens of times a day. The one choice asked here is the app a
 * swipe right opens (the web search). While swipe right leads to pages instead, it is not asked: a
 * line says what is there and leads to Settings → Gestures ([onOpenGestures]); the welcome screen
 * never replaces pages. [onDone] is told whether to go on to Setup.
 */
@Composable
internal fun WelcomePage(settings: Settings, onOpenGestures: () -> Unit, onDone: (toSetup: Boolean) -> Unit) {
    val c = LocalFocusColors.current
    Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 24.dp)) {
        VSpace(96.dp)
        T(stringResource(R.string.app_name), size = 56.sp, weight = FontWeight.Light)
        VSpace(10.dp)
        T(stringResource(R.string.settings_welcome_tagline), size = 20.sp, lineHeight = 28.sp)
        VSpace(22.dp)
        T(stringResource(R.string.settings_welcome_intro), size = 15.sp, color = c.dim, lineHeight = 23.sp)
        VSpace(36.dp)
        val right = settings.gestures.right
        if (right is SwipeTarget.Pages) {
            val apps by Graph.apps.apps.collectAsStateWithLifecycle()
            T(
                stringResource(R.string.settings_welcome_swipe_right_pages, describe(right, apps)),
                Modifier.clickable(onClick = onOpenGestures).padding(vertical = 6.dp),
                size = 15.sp, color = c.dim, lineHeight = 23.sp,
            )
        } else {
            T(stringResource(R.string.settings_welcome_swipe_right), size = 15.sp, color = c.dim, lineHeight = 23.sp)
            SwipeRightRow(settings)
        }
        VSpace(36.dp)
        FocusButton(stringResource(R.string.settings_welcome_start), Modifier.fillMaxWidth(), primary = true) { onDone(false) }
        VSpace(12.dp)
        FocusButton(stringResource(R.string.settings_welcome_setup_first), Modifier.fillMaxWidth()) { onDone(true) }
    }
}

/** Which app swipe right opens: any app, or the web search. */
@Composable
private fun SwipeRightRow(settings: Settings) {
    val apps by Graph.apps.apps.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val webSearch = stringResource(R.string.settings_web_search)
    SettingRow(
        stringResource(R.string.settings_swipe_right_opens),
        subtitle = stringResource(R.string.settings_swipe_right_opens_summary),
        value = describe(settings.gestures.right, apps),
        onClick = { picking = true },
    )
    if (picking) {
        AppPickerDialog(
            title = stringResource(R.string.settings_swipe_right_opens),
            apps = apps.filter { it.key !in settings.hidden },
            onDismiss = { picking = false },
            leading = listOf(webSearch to { setSwipeRight(SwipeTarget.WebSearch) }),
            onPick = { app -> setSwipeRight(SwipeTarget.App(app.key)) },
        )
    }
}

/**
 * Swipe right as chosen on the welcome screen. Never over a sequence of pages, which only Gestures
 * changes: the settings may have changed between showing the question and the answer.
 */
private fun setSwipeRight(target: SwipeTarget) = Graph.settings.update { s ->
    if (s.gestures.right is SwipeTarget.Pages) s else s.copy(gestures = s.gestures.withTarget(Swipe.RIGHT, target))
}
