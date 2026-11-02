package com.focus.launcher.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.focus.launcher.R
import com.focus.launcher.data.AppEntry
import com.focus.launcher.data.Place
import com.focus.launcher.data.Swipe
import com.focus.launcher.data.SwipeTarget

/** A swipe's name as a settings row has it: "Swipe left". */
@Composable
internal fun swipeLabel(swipe: Swipe): String = stringResource(
    when (swipe) {
        Swipe.RIGHT -> R.string.settings_swipe_right
        Swipe.LEFT -> R.string.settings_swipe_left
        Swipe.UP -> R.string.settings_swipe_up
    },
)

/** A swipe as an arrow, the finger's way: →, ←, ↑. */
internal fun swipeArrow(swipe: Swipe): String = when (swipe) {
    Swipe.RIGHT -> "→"
    Swipe.LEFT -> "←"
    Swipe.UP -> "↑"
}

/**
 * What a swipe does, in a few words: "Nothing", "Web search", an app's name, "Widgets, All apps".
 * An app that is gone reads as the web search, which is what the swipe then opens.
 */
@Composable
internal fun describe(target: SwipeTarget, apps: List<AppEntry>): String =
    when (val shown = target.resolvedAgainst(apps.mapTo(HashSet()) { it.key })) {
        SwipeTarget.None -> stringResource(R.string.settings_gesture_nothing)
        SwipeTarget.WebSearch -> stringResource(R.string.settings_web_search)
        is SwipeTarget.App -> apps.firstOrNull { it.key == shown.key }?.label ?: stringResource(R.string.settings_web_search)
        is SwipeTarget.Pages -> shown.pages.map { stringResource(it.labelRes) }.joinToString(", ")
    }

/** Where a page stands, short enough for a settings row's value: "← page 2", "Under ↑", "Off". */
@Composable
internal fun pagePlace(place: Place?): String = when (place?.swipe) {
    null -> stringResource(R.string.settings_page_place_off)
    Swipe.LEFT -> stringResource(R.string.settings_page_place_left, place.position)
    Swipe.RIGHT -> stringResource(R.string.settings_page_place_right, place.position)
    Swipe.UP -> stringResource(R.string.settings_page_place_up)
}
