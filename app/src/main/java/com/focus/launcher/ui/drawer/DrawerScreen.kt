package com.focus.launcher.ui.drawer

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focus.launcher.Graph
import com.focus.launcher.R
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.DayUsage
import com.focus.launcher.data.DrawerLayout
import com.focus.launcher.data.DrawerSort
import com.focus.launcher.data.Settings
import com.focus.launcher.data.movedAmong
import com.focus.launcher.ui.components.AppIcon
import com.focus.launcher.ui.components.ChoiceDialog
import com.focus.launcher.ui.components.Label
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.TabChip
import com.focus.launcher.ui.components.VSpace
import com.focus.launcher.ui.components.WorkBadge
import com.focus.launcher.ui.components.focusTextStyle
import com.focus.launcher.ui.components.hasColourGlyphs
import com.focus.launcher.ui.components.monochrome
import com.focus.launcher.ui.components.press
import com.focus.launcher.ui.theme.LocalFocusColors
import com.focus.launcher.util.formatDuration
import com.focus.launcher.util.formatMinutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer
import kotlin.math.abs

private const val DAY_MS = 86_400_000L

/** Apps to a row in the grid view. */
private const val GRID_COLUMNS = 4

/** Icons in the grid view. Not scaled with the text size: the cache holds one size of each. */
private val ICON_SIZE = 40.dp

/**
 * Page two of the launcher: a search bar, optionally the home screen's fast apps, the pinned apps,
 * the apps installed in the last 24 hours, and every other app as a plain list. With a work profile
 * the list splits into Personal / Work tabs; "Sort" orders it A–Z, by most used or by last used,
 * below the pinned apps. Search always covers
 * both profiles and ranks by match, pinned or not. No icons, unless the grid view is chosen in
 * settings: the same sections, four apps to a row with small icons, grey unless colour is chosen too.
 * Long-press an app for its menu.
 *
 * In [DrawerMode.QUICK] it is the fast-and-pinned page: those two sections alone, of both profiles,
 * with no search, tabs, sort, recent installs or A–Z.
 */
@Composable
fun DrawerScreen(
    settings: Settings,
    apps: List<AppEntry>,
    loaded: Boolean,
    today: DayUsage?,
    query: String,
    onQueryChange: (String) -> Unit,
    isActive: Boolean,
    wantsSearchFocus: Boolean,
    onSearchFocusHandled: () -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
    /** A tip that is about this page, shown under the search bar until it has been done. */
    hint: String? = null,
    mode: DrawerMode = DrawerMode.ALL,
) {
    val quick = mode == DrawerMode.QUICK
    val c = LocalFocusColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val searchFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val grid = settings.drawerLayout == DrawerLayout.GRID
    // Only the view on screen is scrolled: the other one's state is not attached to any layout.
    suspend fun scrollTo(index: Int) = if (grid) gridState.scrollToItem(index) else listState.scrollToItem(index)

    val visible = remember(apps, settings.hidden) { apps.filter { it.key !in settings.hidden } }
    // Stripping accents is the expensive part of search; do it once per list, not per keystroke.
    val searchNames = remember(visible) { visible.associate { it.key to normalize(it.label) } }
    val searching = query.isNotBlank()

    val hasWork = remember(visible) { visible.any { it.isWorkProfile } }
    var showWork by remember { mutableStateOf(false) }
    // The tab choice only counts while there is a work profile to show.
    val workTab = hasWork && showWork
    val inProfile = remember(visible, hasWork, workTab) {
        if (hasWork) visible.filter { it.isWorkProfile == workTab } else visible
    }

    val sort = settings.drawerSort
    var sortDialog by remember { mutableStateOf(false) }
    // package -> (foreground ms over the last week, last time used). Only read when a sort needs it.
    var stats by remember { mutableStateOf<Map<String, Pair<Long, Long>>>(emptyMap()) }
    // Scrolling through the list is browsing, not typing: give the keyboard's half of the screen back.
    val listDragged by listState.interactionSource.collectIsDraggedAsState()
    val gridDragged by gridState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(listDragged, gridDragged) {
        if (listDragged || gridDragged) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }
    LaunchedEffect(sort, isActive) {
        if (isActive && !quick && sort != DrawerSort.ALPHA) stats = withContext(Dispatchers.IO) { Graph.usage.sortStats() }
    }
    // Usage inside a work profile is invisible to us, so those apps count as 0 and stay
    // alphabetical. sortedByDescending is stable: ties keep their A–Z order.
    val sorted = remember(inProfile, sort, stats) {
        when (sort) {
            DrawerSort.ALPHA -> inProfile
            DrawerSort.MOST_USED -> inProfile.sortedByDescending { if (it.isWorkProfile) 0L else stats[it.packageName]?.first ?: 0L }
            DrawerSort.RECENT -> inProfile.sortedByDescending { if (it.isWorkProfile) 0L else stats[it.packageName]?.second ?: 0L }
        }
    }
    // First of all the home screen's fast apps, in their order there, then the pinned apps in the
    // order they were pinned; one that is both is listed once, with the fast apps. In the list: of
    // this tab, and the fast apps only once asked for. On the fast-and-pinned page: both profiles
    // together, as on the home screen, and the fast apps whatever the list does.
    val sections = remember(visible, inProfile, settings.favorites, settings.favoritesInDrawer, settings.pinned, quick) {
        if (quick) quickSections(visible, { it.key }, settings.favorites, settings.pinned)
        else quickSections(inProfile, { it.key }, if (settings.favoritesInDrawer) settings.favorites else emptyList(), settings.pinned)
    }
    val favoriteApps = sections.first
    val pinnedApps = sections.second
    // Every app already listed above the main list. The recent installs leave all of them out; the
    // main list too, except the fast apps when they are to keep their place in it as well.
    val above = remember(settings.pinned, favoriteApps) { settings.pinned.toSet() + favoriteApps.map { it.key } }
    val leftOut = if (settings.favoritesAlsoInList) settings.pinned.toSet() else above
    // Search ignores tab, sort, pins and fast apps: it looks through both profiles and ranks by match.
    // Without a query the list is everything not already above it.
    val results = remember(visible, sorted, leftOut, query, searchNames, settings.typoSearch, quick) {
        if (quick) emptyList()
        else if (query.isBlank()) sorted.filter { it.key !in leftOut }
        else searchApps(visible, query, searchNames, typos = settings.typoSearch)
    }
    val recent = remember(inProfile, settings.showRecentInstalls, above, quick) {
        if (quick || !settings.showRecentInstalls) emptyList()
        else {
            val cutoff = System.currentTimeMillis() - DAY_MS
            inProfile.filter { it.firstInstallTime >= cutoff && it.packageName != Graph.app.packageName && it.key !in above }
                .sortedByDescending { it.firstInstallTime }
        }
    }
    // Pinned, fast and recent apps come under their own labels; "All apps" then opens the main list, if anything is left for it.
    val allAppsLabel = !searching && results.isNotEmpty() && (pinnedApps.isNotEmpty() || favoriteApps.isNotEmpty() || recent.isNotEmpty())
    // Number of list items that come before the alphabetical block (section labels, pinned, fast and recent apps).
    val leadingItems = if (searching) 0 else {
        (if (pinnedApps.isNotEmpty()) pinnedApps.size + 1 else 0) +
            (if (favoriteApps.isNotEmpty()) favoriteApps.size + 1 else 0) +
            (if (recent.isNotEmpty()) recent.size + 1 else 0) +
            (if (allAppsLabel) 1 else 0)
    }
    // With every app of the tab pinned (or a fast app) the main list is empty, but the drawer is not.
    val emptyText = when {
        results.isNotEmpty() || (!searching && (pinnedApps.isNotEmpty() || favoriteApps.isNotEmpty())) -> null
        !loaded -> stringResource(R.string.drawer_loading)
        quick -> stringResource(R.string.drawer_quick_empty)
        searching -> stringResource(R.string.drawer_no_match, query.trim())
        else -> stringResource(R.string.drawer_no_apps)
    }
    // Letters only mean something in an alphabetical list.
    val letters = remember(results, sort, leadingItems, searching) {
        if (sort == DrawerSort.ALPHA && !searching) letterIndex(results, leadingItems) else emptyList()
    }

    // The keyboard only ever opens on purpose: by the "open right away" setting, by swiping up on
    // the home screen, or by tapping the search bar. In every other case make sure it is closed.
    // The fast-and-pinned page has no search field: asking it for focus would throw.
    LaunchedEffect(isActive) {
        if (isActive && !quick && (settings.autoKeyboard || wantsSearchFocus)) {
            searchFocus.requestFocus()
            keyboard?.show()
            onSearchFocusHandled()
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
            // Back to the top while nobody is looking, so the next visit starts at A, not where the last one ended.
            scrollTo(0)
        }
    }
    // stats is a key too: when it arrives the list reorders, and a keyed list would follow the old top row.
    // So are the pins: a new one lands at the very top, which a keyed list would otherwise scroll past.
    // As a set: putting them in a new order moves nothing out of sight, so the list stays where it is.
    // And the layout: the list and the grid each keep their own position.
    LaunchedEffect(query, workTab, sort, stats, settings.pinned.toSet(), grid) { scrollTo(0) }

    // The section whose order is being changed with arrows, if any. Ends with a search or on leaving the page.
    var reordering by remember { mutableStateOf<Reorder?>(null) }
    LaunchedEffect(isActive, searching) { if (!isActive || searching) reordering = null }
    // Moves an app one place among those of its section on screen; see movedAmong for the rest.
    val move: (Reorder, String, Int) -> Unit = { section, key, delta ->
        when (section) {
            Reorder.FAVORITES -> {
                val shown = favoriteApps.map { it.key }
                Graph.settings.update { s -> s.copy(favorites = s.favorites.movedAmong(shown, key, delta)) }
            }
            Reorder.PINNED -> {
                val shown = pinnedApps.map { it.key }
                Graph.settings.update { s -> s.copy(pinned = s.pinned.movedAmong(shown, key, delta)) }
            }
        }
    }
    // The "Reorder" / "Done" switch next to a section's label, for a section with something to reorder.
    val reorderLabel = stringResource(R.string.drawer_reorder)
    val reorderDoneLabel = stringResource(R.string.drawer_reorder_done)
    fun reorderAction(section: Reorder, size: Int): String? = when {
        size < 2 -> null
        reordering == section -> reorderDoneLabel
        else -> reorderLabel
    }
    val toggleReorder: (Reorder) -> Unit = { section -> reordering = if (reordering == section) null else section }
    // Optional: open the app as soon as the search narrows down to exactly one. Names only a typo
    // away are not counted: they would keep "map" from opening Maps whenever Mail is one edit off,
    // and opening an app on a guess at what was meant cannot be taken back.
    LaunchedEffect(results, query) {
        if (settings.autoLaunch && isActive && query.trim().length >= 2) {
            val withoutTypos = if (settings.typoSearch) searchApps(visible, query, searchNames) else results
            if (withoutTypos.size == 1) onLaunch(withoutTypos[0])
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        if (quick) VSpace(14.dp)
        // Search bar
        if (!quick) Row(
            Modifier
                .padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 10.dp)
                .fillMaxWidth()
                .border(1.dp, c.faint)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(searchFocus)
                    // This page is composed even while the home page is showing. Without this the
                    // off-screen field takes the window's initial focus and pops the keyboard.
                    .focusProperties { canFocus = isActive }
                    .padding(vertical = 14.dp),
                singleLine = true,
                textStyle = focusTextStyle(size = 18.sp),
                cursorBrush = SolidColor(c.fg),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onAny = { results.firstOrNull()?.takeIf { searching }?.let(onLaunch) }),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) T(stringResource(R.string.drawer_search_hint), size = 18.sp, color = c.faint, maxLines = 1)
                        inner()
                    }
                },
            )
            if (query.isNotEmpty()) {
                T(stringResource(R.string.drawer_search_clear), Modifier.clickable { onQueryChange("") }.padding(start = 12.dp, top = 8.dp, bottom = 8.dp), size = 14.sp, color = c.dim)
            }
        }

        // Tabs and sort. Chips and the sort text carry their own padding, so both edges land on 30dp.
        if (!searching && !quick) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (hasWork) {
                    TabChip(stringResource(R.string.drawer_tab_personal), !workTab) { showWork = false }
                    TabChip(stringResource(R.string.drawer_tab_work), workTab) { showWork = true }
                }
                Spacer(Modifier.weight(1f))
                T(
                    stringResource(R.string.drawer_sort_current, stringResource(sort.labelRes)),
                    Modifier.press { sortDialog = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                    size = 13.sp, color = c.dim, maxLines = 1,
                )
            }
        }

        // Framed like the tips on the home page, so it reads as a tip and not as part of the list.
        if (hint != null && !searching) {
            T(hint, Modifier.padding(horizontal = 24.dp, vertical = 6.dp).border(1.dp, c.fg, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 9.dp), size = 15.sp, lineHeight = 21.sp)
        }

        var scrubbing by remember { mutableStateOf<Char?>(null) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val showIndex = !quick && !searching && sort == DrawerSort.ALPHA && letters.size > 5 && maxHeight > (letters.size * 15).dp

            if (grid) {
                AppGrid(
                    gridState, settings, today, favoriteApps, pinnedApps, recent, results, allAppsLabel, searching, emptyText, showIndex,
                    reordering, ::reorderAction, toggleReorder, move, onLaunch, onAppMenu,
                )
            } else {
                // Clear of the A–Z index on the right edge, like the rows; the arrows bring 12dp of their own.
                val labelEnd = if (showIndex) 42 else 30
                val arrowsEnd = (labelEnd - 12).dp
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    if (!searching && favoriteApps.isNotEmpty()) {
                        item(key = "label:favorites") {
                            SectionLabel(
                                stringResource(R.string.drawer_fast_apps), end = labelEnd,
                                action = reorderAction(Reorder.FAVORITES, favoriteApps.size),
                            ) { toggleReorder(Reorder.FAVORITES) }
                        }
                        itemsIndexed(favoriteApps, key = { _, app -> "favorite:" + app.key }) { i, app ->
                            if (reordering == Reorder.FAVORITES) {
                                ReorderRow(app, i, favoriteApps.lastIndex, 30.dp, arrowsEnd) { d -> move(Reorder.FAVORITES, app.key, d) }
                            } else {
                                AppRow(app, settings, today, showIndex, onLaunch, onAppMenu)
                            }
                        }
                    }
                    if (!searching && pinnedApps.isNotEmpty()) {
                        item(key = "label:pinned") {
                            SectionLabel(
                                stringResource(R.string.drawer_pinned), top = if (favoriteApps.isNotEmpty()) 22 else 10, end = labelEnd,
                                action = reorderAction(Reorder.PINNED, pinnedApps.size),
                            ) { toggleReorder(Reorder.PINNED) }
                        }
                        itemsIndexed(pinnedApps, key = { _, app -> "pinned:" + app.key }) { i, app ->
                            if (reordering == Reorder.PINNED) {
                                ReorderRow(app, i, pinnedApps.lastIndex, 30.dp, arrowsEnd) { d -> move(Reorder.PINNED, app.key, d) }
                            } else {
                                AppRow(app, settings, today, showIndex, onLaunch, onAppMenu)
                            }
                        }
                    }
                    if (!searching && recent.isNotEmpty()) {
                        item(key = "label:recent") {
                            SectionLabel(stringResource(R.string.drawer_recent_installs), top = if (pinnedApps.isNotEmpty() || favoriteApps.isNotEmpty()) 22 else 10)
                        }
                        items(recent, key = { "recent:" + it.key }) { app ->
                            AppRow(app, settings, today, showIndex, onLaunch, onAppMenu)
                        }
                    }
                    if (allAppsLabel) {
                        item(key = "label:all") { SectionLabel(stringResource(R.string.drawer_all_apps), top = 22) }
                    }
                    items(results, key = { it.key }) { app ->
                        AppRow(app, settings, today, showIndex, onLaunch, onAppMenu)
                    }
                    if (emptyText != null) {
                        item(key = "empty") {
                            T(emptyText, Modifier.padding(horizontal = 30.dp, vertical = 20.dp), size = 16.sp, color = c.dim)
                        }
                    }
                    item(key = "inset") {
                        Column {
                            VSpace(24.dp)
                            Box(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                        }
                    }
                }
            }

            // A–Z scrubber on the right edge
            if (showIndex) {
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(34.dp)
                        .padding(vertical = 6.dp)
                        // grid is a key too: the jump goes to whichever view is shown.
                        .pointerInput(letters, grid) {
                            awaitEachGesture {
                                fun jump(y: Float) {
                                    val i = (y / size.height * letters.size).toInt().coerceIn(0, letters.lastIndex)
                                    val (letter, index) = letters[i]
                                    if (scrubbing != letter) {
                                        scrubbing = letter
                                        scope.launch { scrollTo(index) }
                                    }
                                }
                                val down = awaitFirstDown()
                                down.consume()
                                jump(down.position.y)
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!change.pressed) break
                                    change.consume()
                                    jump(change.position.y)
                                }
                                scrubbing = null
                            }
                        },
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    for ((letter, _) in letters) {
                        T(
                            letter.toString(), size = 10.sp,
                            color = if (scrubbing == letter) c.fg else c.faint,
                            weight = if (scrubbing == letter) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }

            var shownLetter by remember { mutableStateOf('A') }
            scrubbing?.let { shownLetter = it }
            val overlay by animateFloatAsState(if (scrubbing != null) 1f else 0f, tween(if (scrubbing != null) 90 else 260), label = "scrub")
            if (overlay > 0.01f) {
                Box(
                    Modifier.align(Alignment.Center).graphicsLayer { alpha = overlay }.size(92.dp).background(c.bg).border(1.dp, c.dim),
                    contentAlignment = Alignment.Center,
                ) {
                    T(shownLetter.toString(), size = 44.sp, weight = FontWeight.Light)
                }
            }
        }
    }

    if (sortDialog) {
        ChoiceDialog(stringResource(R.string.drawer_sort_title), DrawerSort.entries.map { it to stringResource(it.labelRes) }, sort, { sortDialog = false }) { v ->
            Graph.settings.update { it.copy(drawerSort = v) }
        }
    }
}

/** ALL: the app list, search and all. QUICK: the fast and pinned apps alone, a page of their own. */
enum class DrawerMode { ALL, QUICK }

/**
 * The two sections above the app list, which are the whole of the fast-and-pinned page: the [apps]
 * among [favorites] in that order, then those among [pinned] not already with them, in that order.
 * Keys of apps not in [apps] (gone, hidden, of the other tab) find nothing.
 */
internal fun <T> quickSections(apps: List<T>, key: (T) -> String, favorites: List<String>, pinned: List<String>): Pair<List<T>, List<T>> {
    val byKey = apps.associateBy(key)
    val fast = favorites.mapNotNull { byKey[it] }.distinct()
    val fastKeys = fast.mapTo(HashSet(), key)
    return fast to pinned.filter { it !in fastKeys }.mapNotNull { byKey[it] }.distinct()
}

/** Sections of the list whose order can be changed right there, with arrows. */
private enum class Reorder { FAVORITES, PINNED }

/** A section's label, with [action] (a small word to tap) at the other end when there is one. */
@Composable
private fun SectionLabel(
    text: String,
    top: Int = 10,
    start: Int = 30,
    end: Int = 30,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    if (action == null) {
        Label(text, Modifier.padding(start = start.dp, end = end.dp, top = top.dp, bottom = 6.dp))
        return
    }
    Row(Modifier.fillMaxWidth().padding(start = start.dp, top = top.dp), verticalAlignment = Alignment.CenterVertically) {
        Label(text, Modifier.weight(1f).padding(bottom = 6.dp))
        // Padded all round so the small word is an easy target; the end padding keeps the list's edge.
        T(
            action,
            Modifier.press(onClick = onAction).padding(start = 12.dp, end = end.dp, top = 6.dp, bottom = 12.dp),
            size = 13.sp, color = LocalFocusColors.current.fg, maxLines = 1,
        )
    }
}

/**
 * An app of a section being put in order: its name, and arrows that move it a place up or down.
 * A tap on the name does nothing here, so nothing opens by mistake while arranging.
 */
@Composable
private fun ReorderRow(app: AppEntry, position: Int, last: Int, start: Dp, end: Dp, onMove: (Int) -> Unit) {
    val c = LocalFocusColors.current
    val up = stringResource(R.string.drawer_move_up)
    val down = stringResource(R.string.drawer_move_down)
    Row(Modifier.fillMaxWidth().padding(start = start, end = end), verticalAlignment = Alignment.CenterVertically) {
        val name = if (hasColourGlyphs(app.label)) Modifier.weight(1f).monochrome() else Modifier.weight(1f)
        T(app.label, name, size = 20.sp, color = c.fg, maxLines = 1)
        val canUp = position > 0
        val canDown = position < last
        T(
            "↑",
            Modifier.semantics { contentDescription = up }.clickable(enabled = canUp) { onMove(-1) }.padding(12.dp),
            size = 20.sp, color = if (canUp) c.fg else c.line, maxLines = 1,
        )
        T(
            "↓",
            Modifier.semantics { contentDescription = down }.clickable(enabled = canDown) { onMove(1) }.padding(12.dp),
            size = 20.sp, color = if (canDown) c.fg else c.line, maxLines = 1,
        )
    }
}

@Composable
private fun AppRow(
    app: AppEntry,
    settings: Settings,
    today: DayUsage?,
    indexShown: Boolean,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    val c = LocalFocusColors.current
    val res = LocalResources.current
    val limit = remember(app.packageName, settings.appLimits, settings.timersEnabled, settings.socialDefaultMin, settings.gameDefaultMin) {
        Graph.limits.limitFor(app.packageName, settings)
    }
    val used = today?.perApp?.get(app.packageName) ?: 0L
    val spent = limit != null && used >= limit.millis && !Graph.limits.hasFreePass(app.packageName)

    Row(
        Modifier
            .fillMaxWidth()
            .press(onLongClick = { onAppMenu(app) }) { onLaunch(app) }
            .padding(start = 30.dp, end = if (indexShown) 42.dp else 30.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val name = if (hasColourGlyphs(app.label)) Modifier.weight(1f).monochrome() else Modifier.weight(1f)
        T(app.label, name, size = 20.sp, color = if (spent) c.faint else c.fg, maxLines = 1)
        if (app.isWorkProfile) WorkBadge()
        if (limit != null && settings.showUsageInDrawer) {
            T(stringResource(R.string.drawer_usage_of_limit, formatDuration(res, used), formatMinutes(res, limit.minutes)), size = 13.sp, color = if (spent) c.faint else c.dim, maxLines = 1)
        }
    }
}

// ---- grid view -------------------------------------------------------------------------------

/**
 * The grid view: the list's sections in the list's order, item for item, so the A–Z index jumps
 * to the row of a letter's first app. Labels, the empty note and the bottom inset take a whole row.
 */
@Composable
private fun AppGrid(
    state: LazyGridState,
    settings: Settings,
    today: DayUsage?,
    favoriteApps: List<AppEntry>,
    pinnedApps: List<AppEntry>,
    recent: List<AppEntry>,
    results: List<AppEntry>,
    allAppsLabel: Boolean,
    searching: Boolean,
    emptyText: String?,
    indexShown: Boolean,
    reordering: Reorder?,
    reorderAction: (Reorder, Int) -> String?,
    onToggleReorder: (Reorder) -> Unit,
    onMove: (Reorder, String, Int) -> Unit,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    val c = LocalFocusColors.current
    // A section being put in order takes a whole row per app, the arrows next to the name.
    fun span(section: Reorder) = if (reordering == section) GRID_COLUMNS else 1
    LazyVerticalGrid(
        columns = GridCells.Fixed(GRID_COLUMNS),
        modifier = Modifier.fillMaxSize(),
        state = state,
        // Labels and the empty note sit 12dp further in, on the list's 30dp edge. The index takes the right edge.
        contentPadding = PaddingValues(start = 18.dp, end = if (indexShown) 34.dp else 18.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (!searching && favoriteApps.isNotEmpty()) {
            item(key = "label:favorites", span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(
                    stringResource(R.string.drawer_fast_apps), start = 12, end = 12,
                    action = reorderAction(Reorder.FAVORITES, favoriteApps.size),
                ) { onToggleReorder(Reorder.FAVORITES) }
            }
            itemsIndexed(favoriteApps, key = { _, app -> "favorite:" + app.key }, span = { _, _ -> GridItemSpan(span(Reorder.FAVORITES)) }) { i, app ->
                if (reordering == Reorder.FAVORITES) {
                    ReorderRow(app, i, favoriteApps.lastIndex, 12.dp, 0.dp) { d -> onMove(Reorder.FAVORITES, app.key, d) }
                } else {
                    AppTile(app, settings, today, onLaunch, onAppMenu)
                }
            }
        }
        if (!searching && pinnedApps.isNotEmpty()) {
            item(key = "label:pinned", span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(
                    stringResource(R.string.drawer_pinned), top = if (favoriteApps.isNotEmpty()) 22 else 10, start = 12, end = 12,
                    action = reorderAction(Reorder.PINNED, pinnedApps.size),
                ) { onToggleReorder(Reorder.PINNED) }
            }
            itemsIndexed(pinnedApps, key = { _, app -> "pinned:" + app.key }, span = { _, _ -> GridItemSpan(span(Reorder.PINNED)) }) { i, app ->
                if (reordering == Reorder.PINNED) {
                    ReorderRow(app, i, pinnedApps.lastIndex, 12.dp, 0.dp) { d -> onMove(Reorder.PINNED, app.key, d) }
                } else {
                    AppTile(app, settings, today, onLaunch, onAppMenu)
                }
            }
        }
        if (!searching && recent.isNotEmpty()) {
            item(key = "label:recent", span = { GridItemSpan(maxLineSpan) }) {
                SectionLabel(
                    stringResource(R.string.drawer_recent_installs),
                    top = if (pinnedApps.isNotEmpty() || favoriteApps.isNotEmpty()) 22 else 10, start = 12,
                )
            }
            items(recent, key = { "recent:" + it.key }) { app ->
                AppTile(app, settings, today, onLaunch, onAppMenu)
            }
        }
        if (allAppsLabel) {
            item(key = "label:all", span = { GridItemSpan(maxLineSpan) }) { SectionLabel(stringResource(R.string.drawer_all_apps), top = 22, start = 12) }
        }
        items(results, key = { it.key }) { app ->
            AppTile(app, settings, today, onLaunch, onAppMenu)
        }
        if (emptyText != null) {
            item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                T(emptyText, Modifier.padding(horizontal = 12.dp, vertical = 20.dp), size = 16.sp, color = c.dim)
            }
        }
        item(key = "inset", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                VSpace(24.dp)
                Box(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }
    }
}

/** One app of the grid: its icon, its name below, and the timer if the list would show one. */
@Composable
private fun AppTile(
    app: AppEntry,
    settings: Settings,
    today: DayUsage?,
    onLaunch: (AppEntry) -> Unit,
    onAppMenu: (AppEntry) -> Unit,
) {
    val c = LocalFocusColors.current
    val res = LocalResources.current
    val limit = remember(app.packageName, settings.appLimits, settings.timersEnabled, settings.socialDefaultMin, settings.gameDefaultMin, settings.videoDefaultMin) {
        Graph.limits.limitFor(app.packageName, settings)
    }
    val used = today?.perApp?.get(app.packageName) ?: 0L
    val spent = limit != null && used >= limit.millis && !Graph.limits.hasFreePass(app.packageName)

    Column(
        Modifier
            .fillMaxWidth()
            .press(onLongClick = { onAppMenu(app) }) { onLaunch(app) }
            .padding(horizontal = 2.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // No description: the name is right below. A work app whose icon could not be read gets the list's marker.
        Box(Modifier.size(ICON_SIZE), contentAlignment = Alignment.Center) {
            AppIcon(
                app, ICON_SIZE, dimmed = spent, colour = settings.colourIcons,
                missing = { if (app.isWorkProfile) WorkBadge(side = 18.dp) },
            )
        }
        VSpace(6.dp)
        val name = if (hasColourGlyphs(app.label)) Modifier.fillMaxWidth().monochrome() else Modifier.fillMaxWidth()
        T(app.label, name, size = 13.sp, color = if (spent) c.faint else c.fg, align = TextAlign.Center, maxLines = 1)
        if (limit != null && settings.showUsageInDrawer) {
            T(
                stringResource(R.string.drawer_usage_of_limit, formatDuration(res, used), formatMinutes(res, limit.minutes)),
                Modifier.fillMaxWidth(), size = 11.sp, color = if (spent) c.faint else c.dim, align = TextAlign.Center, maxLines = 1,
            )
        }
    }
}

// ---- search ----------------------------------------------------------------------------------

private val MARKS = Regex("\\p{Mn}+")

/**
 * Latin letters that Unicode does not decompose into a base letter plus a mark, so stripping marks
 * leaves them as they are: "Łódź" would sort under '#' and never match "lodz" without this.
 */
private val UNDECOMPOSED = mapOf(
    'ł' to "l", 'đ' to "d", 'ø' to "o", 'ħ' to "h", 'ı' to "i",
    'ß' to "ss", 'æ' to "ae", 'œ' to "oe", 'þ' to "th",
)

private fun normalize(text: String): String {
    val plain = MARKS.replace(Normalizer.normalize(text, Normalizer.Form.NFD), "").lowercase().trim()
    if (plain.none { it in UNDECOMPOSED }) return plain
    return buildString(plain.length) { plain.forEach { c -> append(UNDECOMPOSED[c] ?: c) } }
}

/**
 * Filters [apps] by [query]. Best matches first: name starts with the query, then a word in the
 * name starts with it, then it appears anywhere, then initials ("gm" finds Google Maps), then,
 * with [typos], names one or two edits away, and finally a loose in-order match for typos of three
 * letters or more.
 */
fun searchApps(
    apps: List<AppEntry>,
    query: String,
    names: Map<String, String> = emptyMap(),
    typos: Boolean = false,
): List<AppEntry> {
    val q = normalize(query)
    if (q.isEmpty()) return apps
    val ranked = ArrayList<Pair<Int, AppEntry>>()
    for (app in apps) {
        val name = names[app.key] ?: normalize(app.label)
        val rank = searchRank(q, name, typos) ?: continue
        ranked += rank to app
    }
    return ranked.sortedBy { it.first }.map { it.second } // stable: alphabetical within a rank
}

/** Both arguments have already been normalized. Smaller ranks always appear first; null: no match. */
internal fun searchRank(query: String, name: String, typos: Boolean): Int? {
    val words = name.split(' ', '-', '_', '.', ':').filter { it.isNotEmpty() }
    return when {
        name.startsWith(query) -> 0
        words.any { it.startsWith(query) } -> 1
        name.contains(query) -> 2
        words.size > 1 && words.map { it[0] }.joinToString("").startsWith(query) -> 3
        else -> when (if (typos) fuzzyDistance(query, name, words) else Int.MAX_VALUE) {
            1 -> 4
            2 -> 5
            else -> if (query.length >= 3 && isSubsequence(query, name)) 6 else null
        }
    }
}

/**
 * Edit distance of [query] to the whole [name] or to the start of one of its [words], so a partial
 * search also tolerates typos. Anything past the limit (one edit for three letters, two for more)
 * comes back as more than the limit.
 */
internal fun fuzzyDistance(query: String, name: String, words: List<String>): Int {
    // Two edits in a two-letter search would include most of the drawer.
    if (query.length < 3) return 3
    val limit = if (query.length == 3) 1 else 2
    var best = editDistanceAtMost(query, name, limit)
    for (word in words) {
        for (length in (query.length - limit).coerceAtLeast(1)..(query.length + limit).coerceAtMost(word.length)) {
            best = minOf(best, editDistanceAtMost(query, word.substring(0, length), limit))
            if (best == 1) return best
        }
    }
    return best
}

/** Levenshtein distance (two swapped letters count as two edits), capped at [limit] + 1. */
private fun editDistanceAtMost(a: String, b: String, limit: Int): Int {
    if (abs(a.length - b.length) > limit) return limit + 1
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in a.indices) {
        current[0] = i + 1
        var rowMin = current[0]
        for (j in b.indices) {
            current[j + 1] = minOf(
                previous[j + 1] + 1, current[j] + 1,
                previous[j] + if (a[i] == b[j]) 0 else 1,
            )
            rowMin = minOf(rowMin, current[j + 1])
        }
        // No later row can go below this one's smallest cell, so the limit is already out of reach.
        if (rowMin > limit) return limit + 1
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length].coerceAtMost(limit + 1)
}

private fun isSubsequence(needle: String, haystack: String): Boolean {
    var i = 0
    for (ch in haystack) if (i < needle.length && ch == needle[i]) i++
    return i == needle.length
}

/** First list position of every initial letter, in list order ('#' collects everything else). */
private fun letterIndex(apps: List<AppEntry>, offset: Int): List<Pair<Char, Int>> {
    val out = ArrayList<Pair<Char, Int>>()
    var last: Char? = null
    apps.forEachIndexed { i, app ->
        val first = normalize(app.label).firstOrNull()?.uppercaseChar()
        val letter = if (first != null && first in 'A'..'Z') first else '#'
        if (letter != last) {
            if (out.none { it.first == letter }) out += letter to (i + offset)
            last = letter
        }
    }
    return out
}
