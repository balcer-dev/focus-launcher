package com.focus.launcher

import com.focus.launcher.data.Tip
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.Settings
import com.focus.launcher.service.WeeklyReview
import com.focus.launcher.ui.drawer.AppMenu
import com.focus.launcher.ui.drawer.DrawerScreen
import com.focus.launcher.ui.home.HomeScreen
import com.focus.launcher.ui.launchApp
import com.focus.launcher.ui.openWebSearch
import com.focus.launcher.ui.theme.FocusTheme
import com.focus.launcher.ui.theme.applyFocusWindow
import com.focus.launcher.ui.widgets.WidgetController
import com.focus.launcher.ui.widgets.WidgetPage
import com.focus.launcher.util.Perms
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

/**
 * The home screen (page 0) and, one swipe to the left, the app drawer. With the widgets page on,
 * that page comes between them (see [PagerLayout]).
 */
class MainActivity : ComponentActivity() {
    private val homePresses = MutableSharedFlow<Unit>(extraBufferCapacity = 4)

    // Made on first use: while the widgets page is off, nothing here talks to the widget service.
    private val widgetsLazy = lazy { WidgetController(this) }
    private val widgets: WidgetController by widgetsLazy

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Graph.settings.value.let { applyFocusWindow(it.dark, it.hideStatusBar) }
        // First start ever: the introduction, once. Marked as seen here already, so that pressing
        // Home in the middle of it never brings it back.
        if (savedInstanceState == null && !Graph.state.tutorialSeen) {
            Graph.state.tutorialSeen = true
            startActivity(SettingsActivity.intent(this, "welcome"))
        }
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle()
            LaunchedEffect(settings.dark, settings.hideStatusBar) { applyFocusWindow(settings.dark, settings.hideStatusBar) }
            FocusTheme(settings) { Launcher(settings, homePresses, widgetsLazy) }
        }
    }

    override fun onStart() {
        super.onStart()
        // Widgets on the page only redraw while their host listens to the widget service.
        if (Graph.settings.value.showWidgetPage) widgets.startListening()
    }

    override fun onResume() {
        super.onResume()
        // A restored backup from another phone, a widget whose app was uninstalled: drop what is not there.
        if (Graph.settings.value.showWidgetPage) widgets.dropUnbound()
    }

    override fun onStop() {
        if (widgetsLazy.isInitialized()) widgets.stopListening()
        super.onStop()
    }

    // Adding a widget goes through system screens that answer by request code: the widget host's
    // startAppWidgetConfigureActivityForResult has no Activity Result API counterpart.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (WidgetController.owns(requestCode)) widgets.onActivityResult(requestCode, resultCode)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The Home button while the launcher is already showing: go back to page 0.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (widgetsLazy.isInitialized()) widgets.onHomePressed()
            homePresses.tryEmit(Unit)
        }
    }
}

/** Which page of the launcher is where: home first, then the widgets page while it is on, then the app drawer. */
internal data class PagerLayout(val showWidgets: Boolean) {
    val pageCount: Int get() = if (showWidgets) 3 else 2

    /** -1 while the widgets page is off: no page has that number. */
    val widgetsPage: Int get() = if (showWidgets) 1 else -1

    val drawerPage: Int get() = pageCount - 1
}

private var lastBackfill = 0L

@Composable
private fun Launcher(settings: Settings, homePresses: Flow<Unit>, widgets: Lazy<WidgetController>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val apps by Graph.apps.apps.collectAsStateWithLifecycle()
    val loaded by Graph.apps.loaded.collectAsStateWithLifecycle()
    val today by Graph.usage.today.collectAsStateWithLifecycle()
    val pendingReview by Graph.state.pendingReview.collectAsStateWithLifecycle()
    val tip by Graph.state.tip.collectAsStateWithLifecycle()

    val layout = PagerLayout(settings.showWidgetPage)
    val pager = rememberPagerState { layout.pageCount }
    // The drawer counts as open from the moment a swipe is let go towards it: not halfway through
    // the drag, where the finger can still turn back (the keyboard used to pop up and drop again),
    // and not only once the page has settled (the keyboard would come late). So the keyboard
    // rises while the page glides in, and falls while it glides out.
    val pagerDragged by pager.interactionSource.collectIsDraggedAsState()
    val drawerActive by remember(layout) {
        derivedStateOf { if (pagerDragged) pager.settledPage == layout.drawerPage else pager.targetPage == layout.drawerPage }
    }
    // The same for the widgets page, whose dialogs close as soon as it is left.
    val widgetsActive by remember(layout) {
        derivedStateOf { if (pagerDragged) pager.settledPage == layout.widgetsPage else pager.targetPage == layout.widgetsPage }
    }
    var query by remember { mutableStateOf("") }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var wantsSearchFocus by remember { mutableStateOf(false) }
    var resumeCount by remember { mutableIntStateOf(0) }
    var usageAccess by remember { mutableStateOf(Perms.hasUsageAccess()) }
    var setupIncomplete by remember { mutableStateOf(false) }

    // Every return to the launcher: re-read permissions, refresh today's numbers (then keep them
    // ticking once a minute), and see whether a weekly review has come due.
    LifecycleResumeEffect(settings.timersEnabled) {
        resumeCount++
        usageAccess = Perms.hasUsageAccess()
        setupIncomplete = !Perms.isDefaultLauncher(context) || !usageAccess ||
            (settings.timersEnabled && !Perms.isTimerServiceEnabled(context))
        val job = scope.launch {
            WeeklyReview.checkDue()
            val now = System.currentTimeMillis()
            if (usageAccess && now - lastBackfill > 6 * 3_600_000L) {
                lastBackfill = now
                launch { Graph.usage.backfill() }
            }
            while (true) {
                Graph.usage.refreshToday()
                delay(60_000)
            }
        }
        onPauseOrDispose { job.cancel() }
    }

    // Leaving the launcher (an app was opened, the screen went off) always resets it to page 0.
    // Except while a widget is being added: the system's permission screen and the widget's own
    // setup come in between, and the new widget should be in view on the way back.
    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            if (!(widgets.isInitialized() && widgets.value.awaitingResult)) {
                query = ""
                menuApp = null
                wantsSearchFocus = false
                scope.launch { pager.scrollToPage(0) }
            }
        }
    }

    // Arrived in the app list, by whichever way: that tip is learnt. The widgets page, which a swipe
    // left reaches first while it is on, does not count: the tip promises all apps.
    LaunchedEffect(pager.settledPage, layout) { if (pager.settledPage == layout.drawerPage) Graph.state.did(Tip.SWIPE_LEFT) }

    LaunchedEffect(Unit) {
        homePresses.collect {
            menuApp = null
            query = ""
            if (pager.currentPage != 0) pager.animateScrollToPage(0)
        }
    }

    // A launcher is never "backed out of": back only returns from the drawer to home.
    BackHandler {
        if (pager.currentPage != 0) scope.launch { pager.animateScrollToPage(0) }
    }

    val launch: (AppEntry) -> Unit = { entry -> launchApp(context, scope, entry) }
    // The app swipe right opens, if the user chose one; null means the web search.
    val swipeRightEntry = remember(apps, settings.swipeRightApp) { apps.firstOrNull { it.key == settings.swipeRightApp } }

    HorizontalPager(
        state = pager,
        // There is no page to the left of home, so the pager ignores that swipe. Watch it on the
        // way down (Initial pass, nothing consumed) and open the web search instead, the way the
        // page left of a stock home screen does.
        modifier = Modifier.fillMaxSize().pointerInput(settings.swipeRightSearch, swipeRightEntry) {
            if (!settings.swipeRightSearch) return@pointerInput
            val threshold = 72.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (pager.currentPage != 0 || pager.isScrollInProgress) return@awaitEachGesture
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val moved = change.position - down.position
                    if (moved.x > threshold && moved.x > abs(moved.y) * 2) {
                        val entry = swipeRightEntry
                        if (entry != null) launch(entry) else openWebSearch(context)
                        Graph.state.did(Tip.SWIPE_RIGHT)
                        break
                    }
                }
            }
        },
        // Every page stays composed, as when there were only two: the app list is ready, search
        // field and all, the moment a swipe up asks for it.
        beyondViewportPageCount = layout.pageCount - 1,
        key = { it },
    ) { page ->
        // The page being left fades and sinks back a touch while the other one arrives. Done in
        // graphicsLayer, which runs in the draw phase: the swipe never triggers a recomposition.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val distance = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                // Text on a flat background never overlaps itself, so each draw call can simply be
                // made more transparent. The default would render the whole page into a full-screen
                // off-screen buffer on every frame of the swipe: more memory and GPU for no gain.
                // Widgets are pictures that do overlap, so their page fades as one layer.
                compositingStrategy = if (page == layout.widgetsPage) CompositingStrategy.Auto else CompositingStrategy.ModulateAlpha
                alpha = (1f - distance * 1.2f).coerceIn(0f, 1f)
                val scale = 1f - 0.05f * distance
                scaleX = scale
                scaleY = scale
            },
        ) {
            if (page == 0) {
                HomeScreen(
                    settings = settings,
                    apps = apps,
                    today = today,
                    usageAccess = usageAccess,
                    setupIncomplete = setupIncomplete,
                    pendingReview = pendingReview,
                    resumeCount = resumeCount,
                    onLaunch = launch,
                    onAppMenu = { Graph.state.did(Tip.APP_MENU); menuApp = it },
                    onOpenDrawer = { focusSearch ->
                        wantsSearchFocus = focusSearch
                        scope.launch { pager.animateScrollToPage(layout.drawerPage) }
                    },
                    onOpenSettings = { route -> context.startActivity(SettingsActivity.intent(context, route)) },
                    onOpenReview = { week -> context.startActivity(ReviewActivity.intent(context, week)) },
                )
            } else if (page == layout.widgetsPage) {
                WidgetPage(slots = settings.widgets, controller = widgets.value, isActive = widgetsActive)
            } else {
                DrawerScreen(
                    settings = settings,
                    apps = apps,
                    loaded = loaded,
                    today = today,
                    query = query,
                    onQueryChange = { query = it },
                    isActive = drawerActive,
                    wantsSearchFocus = wantsSearchFocus,
                    onSearchFocusHandled = { wantsSearchFocus = false },
                    onLaunch = launch,
                    onAppMenu = { Graph.state.did(Tip.APP_MENU); menuApp = it },
                    hint = tip?.takeIf { it == Tip.APP_MENU }?.let {
                        stringResource(R.string.tip_hint, stringResource(it.gestureRes), stringResource(it.resultRes))
                    },
                )
            }
        }
    }

    menuApp?.let { app ->
        AppMenu(
            app = app,
            settings = settings,
            today = today,
            onDismiss = { menuApp = null },
            onOpenSetup = { context.startActivity(SettingsActivity.intent(context, "setup")) },
        )
    }
}
