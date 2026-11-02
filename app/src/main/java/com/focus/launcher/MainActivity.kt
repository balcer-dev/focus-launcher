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
import androidx.compose.runtime.rememberUpdatedState
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
import com.focus.launcher.data.GestureLayout
import com.focus.launcher.data.LauncherPage
import com.focus.launcher.data.Settings
import com.focus.launcher.data.Swipe
import com.focus.launcher.data.SwipeTarget
import com.focus.launcher.service.WeeklyReview
import com.focus.launcher.ui.drawer.AppMenu
import com.focus.launcher.ui.drawer.DrawerMode
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
 * The home screen and the pages either side of it, as the gestures lay them out ([PagerLayout]):
 * by default the app drawer one swipe to the left, and nothing to the right but the web search.
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
        if (Graph.settings.value.gestures.reaches(LauncherPage.WIDGETS)) widgets.startListening()
    }

    override fun onResume() {
        super.onResume()
        // A restored backup from another phone, a widget whose app was uninstalled: drop what is not there.
        if (Graph.settings.value.gestures.reaches(LauncherPage.WIDGETS)) widgets.dropUnbound()
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
        // The Home button while the launcher is already showing: go back to the home screen.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (widgetsLazy.isInitialized()) widgets.onHomePressed()
            homePresses.tryEmit(Unit)
        }
    }
}

/**
 * Which page of the launcher is where, left to right: the pages of swipe right, the nearest one
 * last, then the home screen, then the pages of swipe left. The home screen is page 0 only while
 * swipe right has no pages.
 */
internal data class PagerLayout(val gestures: GestureLayout) {
    /** Left to right as on screen; null is the home screen. */
    val pages: List<LauncherPage?> = sequence(gestures.right).reversed() + listOf(null) + sequence(gestures.left)

    val pageCount: Int get() = pages.size

    val homePage: Int get() = sequence(gestures.right).size

    /** -1 when [page] is not in the pager: under swipe up only, or nowhere. */
    fun indexOf(page: LauncherPage): Int = pages.indexOf(page)

    val drawerPage: Int get() = indexOf(LauncherPage.ALL_APPS)

    val widgetsPage: Int get() = indexOf(LauncherPage.WIDGETS)

    /** The page swipe up slides in from the bottom: its page when that stands in no sequence, else null. */
    val sheet: LauncherPage? get() = (gestures.up as? SwipeTarget.Pages)?.pages?.firstOrNull()?.takeIf { indexOf(it) < 0 }

    /** The app or web search a sideways swipe on the home screen opens; null when pages lie that way, or nothing. */
    fun actionFor(swipe: Swipe): SwipeTarget? =
        if (swipe == Swipe.UP) null else gestures[swipe].takeIf { it is SwipeTarget.WebSearch || it is SwipeTarget.App }

    private companion object {
        fun sequence(target: SwipeTarget): List<LauncherPage> = (target as? SwipeTarget.Pages)?.pages.orEmpty()
    }
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

    val layout = remember(settings.gestures) { PagerLayout(settings.gestures) }
    // The handlers below outlive a recomposition (leaving the launcher, Home, the swipe detector):
    // they read the layout through this, so a change of gestures reaches them.
    val current by rememberUpdatedState(layout)
    val pager = rememberPagerState(initialPage = layout.homePage) { layout.pageCount }
    // A page counts as open from the moment a swipe is let go towards it: not halfway through the
    // drag, where the finger can still turn back (the drawer's keyboard used to pop up and drop
    // again), and not only once the page has settled (the keyboard would come late). So the
    // keyboard rises while the drawer glides in, and falls while it glides out; the widgets page's
    // dialogs close as soon as it is left.
    val pagerDragged by pager.interactionSource.collectIsDraggedAsState()
    val activePage by remember { derivedStateOf { if (pagerDragged) pager.settledPage else pager.targetPage } }
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

    // Leaving the launcher (an app was opened, the screen went off) always resets it to the home
    // screen. Except while a widget is being added: the system's permission screen and the widget's
    // own setup come in between, and the new widget should be in view on the way back.
    LifecycleStartEffect(Unit) {
        onStopOrDispose {
            if (!(widgets.isInitialized() && widgets.value.awaitingResult)) {
                query = ""
                menuApp = null
                wantsSearchFocus = false
                scope.launch { pager.scrollToPage(current.homePage) }
            }
        }
    }

    // The gestures were changed (in Settings, by a restored backup): the pages moved, and the page
    // number the pager is on may now be another page. Back to the home screen, wherever it is now.
    // Only on a change: a layout that is merely composed again leaves the pager where it is.
    var shownLayout by remember { mutableStateOf(layout) }
    LaunchedEffect(layout) {
        if (layout != shownLayout) {
            shownLayout = layout
            pager.scrollToPage(layout.homePage)
        }
    }

    // Arrived in the app list, by whichever way: that tip is learnt. The widgets page, which a swipe
    // left reaches first while it is on, does not count: the tip promises all apps.
    LaunchedEffect(pager.settledPage, layout) { if (pager.settledPage == layout.drawerPage) Graph.state.did(Tip.SWIPE_LEFT) }

    LaunchedEffect(Unit) {
        homePresses.collect {
            menuApp = null
            query = ""
            if (pager.currentPage != current.homePage) pager.animateScrollToPage(current.homePage)
        }
    }

    // A launcher is never "backed out of": back only returns to the home screen.
    BackHandler {
        if (pager.currentPage != current.homePage) scope.launch { pager.animateScrollToPage(current.homePage) }
    }

    val launch: (AppEntry) -> Unit = { entry -> launchApp(context, scope, entry) }
    val installedKeys = remember(apps) { apps.mapTo(HashSet()) { it.key } }
    // A swipe set to an app or the web search. An app that is gone opens the web search, as it always has.
    val runAction: (SwipeTarget) -> Unit = { target ->
        when (val resolved = target.resolvedAgainst(installedKeys)) {
            SwipeTarget.WebSearch -> openWebSearch(context)
            is SwipeTarget.App -> apps.firstOrNull { it.key == resolved.key }?.let(launch) ?: openWebSearch(context)
            else -> Unit
        }
    }
    // To [page] wherever it stands. The search field takes the keyboard only when asked to (swipe up).
    val goTo: (LauncherPage, Boolean) -> Unit = { page, focusSearch ->
        val index = current.indexOf(page)
        if (index >= 0) {
            wantsSearchFocus = focusSearch && page == LauncherPage.ALL_APPS
            scope.launch { pager.animateScrollToPage(index) }
        }
    }
    val swipeUp: () -> Unit = {
        when (val up = current.gestures.up) {
            is SwipeTarget.Pages -> up.pages.firstOrNull()?.let { goTo(it, true) }
            SwipeTarget.None -> Unit
            else -> runAction(up)
        }
    }

    // One composable per page; each page stands in the launcher at most once.
    val pageContent: @Composable (LauncherPage?, Boolean) -> Unit = { page, isActive ->
        when (page) {
            null -> HomeScreen(
                settings = settings,
                apps = apps,
                today = today,
                usageAccess = usageAccess,
                setupIncomplete = setupIncomplete,
                pendingReview = pendingReview,
                resumeCount = resumeCount,
                onLaunch = launch,
                onAppMenu = { Graph.state.did(Tip.APP_MENU); menuApp = it },
                onOpenDrawer = { focusSearch -> goTo(LauncherPage.ALL_APPS, focusSearch) },
                onSwipeUp = swipeUp,
                onOpenSettings = { route -> context.startActivity(SettingsActivity.intent(context, route)) },
                onOpenReview = { week -> context.startActivity(ReviewActivity.intent(context, week)) },
            )
            LauncherPage.WIDGETS -> WidgetPage(slots = settings.widgets, controller = widgets.value, isActive = isActive)
            LauncherPage.ALL_APPS -> DrawerScreen(
                settings = settings,
                apps = apps,
                loaded = loaded,
                today = today,
                query = query,
                onQueryChange = { query = it },
                isActive = isActive,
                wantsSearchFocus = wantsSearchFocus,
                onSearchFocusHandled = { wantsSearchFocus = false },
                onLaunch = launch,
                onAppMenu = { Graph.state.did(Tip.APP_MENU); menuApp = it },
                hint = tip?.takeIf { it == Tip.APP_MENU }?.let {
                    stringResource(R.string.tip_hint, stringResource(it.gestureRes), stringResource(it.resultRes))
                },
            )
            LauncherPage.QUICK -> DrawerScreen(
                settings = settings,
                apps = apps,
                loaded = loaded,
                today = today,
                query = "",
                onQueryChange = {},
                isActive = isActive,
                wantsSearchFocus = false,
                onSearchFocusHandled = {},
                onLaunch = launch,
                onAppMenu = { Graph.state.did(Tip.APP_MENU); menuApp = it },
                mode = DrawerMode.QUICK,
            )
        }
    }

    HorizontalPager(
        state = pager,
        // Where a sideways swipe has an app or the web search instead of pages, there is no page that
        // way and the pager ignores the swipe. Watch it on the way down (Initial pass, nothing
        // consumed) and run the action instead, the way the page left of a stock home screen opens.
        modifier = Modifier.fillMaxSize().pointerInput(layout, installedKeys) {
            val toRight = layout.actionFor(Swipe.RIGHT)
            val toLeft = layout.actionFor(Swipe.LEFT)
            if (toRight == null && toLeft == null) return@pointerInput
            val threshold = 72.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (pager.currentPage != layout.homePage || pager.isScrollInProgress) return@awaitEachGesture
                while (true) {
                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val moved = change.position - down.position
                    val sideways = abs(moved.x) > abs(moved.y) * 2
                    if (sideways && moved.x > threshold && toRight != null) {
                        runAction(toRight)
                        Graph.state.did(Tip.SWIPE_RIGHT)
                        break
                    }
                    if (sideways && -moved.x > threshold && toLeft != null) {
                        runAction(toLeft)
                        break
                    }
                }
            }
        },
        // Every page stays composed, as when there were only two: the app list is ready, search
        // field and all, the moment a swipe up asks for it.
        beyondViewportPageCount = layout.pageCount - 1,
        // By page, not by number: the home screen keeps its state when the pages around it move.
        key = { index -> if (index == layout.homePage) "home" else layout.pages.getOrNull(index)?.name ?: "page:$index" },
    ) { index ->
        if (index !in layout.pages.indices) return@HorizontalPager
        val page = layout.pages[index]
        // The page being left fades and sinks back a touch while the other one arrives. Done in
        // graphicsLayer, which runs in the draw phase: the swipe never triggers a recomposition.
        Box(
            Modifier.fillMaxSize().graphicsLayer {
                val distance = ((pager.currentPage - index) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                // Text on a flat background never overlaps itself, so each draw call can simply be
                // made more transparent. The default would render the whole page into a full-screen
                // off-screen buffer on every frame of the swipe: more memory and GPU for no gain.
                // Widgets are pictures that do overlap, so their page fades as one layer.
                compositingStrategy = if (page == LauncherPage.WIDGETS) CompositingStrategy.Auto else CompositingStrategy.ModulateAlpha
                alpha = (1f - distance * 1.2f).coerceIn(0f, 1f)
                val scale = 1f - 0.05f * distance
                scaleX = scale
                scaleY = scale
            },
        ) {
            pageContent(page, activePage == index)
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
