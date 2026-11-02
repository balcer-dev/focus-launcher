package com.focus.launcher.data

import com.focus.launcher.data.LauncherPage.ALL_APPS
import com.focus.launcher.data.LauncherPage.QUICK
import com.focus.launcher.data.LauncherPage.WIDGETS

/** Gesture layouts shared by the tests: the owner's two examples, written out once. */
fun pages(vararg p: LauncherPage) = SwipeTarget.Pages(p.toList())

/** The phone's Settings app, as its [AppEntry.key] (a flattened component). */
const val SETTINGS_APP = "com.android.settings/com.android.settings.Settings"

/** Swipe right: the widgets. Swipe up: all apps. Swipe left: the phone's Settings. */
val EXAMPLE_1 = GestureLayout(right = pages(WIDGETS), left = SwipeTarget.App(SETTINGS_APP), up = pages(ALL_APPS))

/** Swipe right: all apps, and once more the widgets. Swipe up: the fast and pinned apps. Swipe left: nothing. */
val EXAMPLE_2 = GestureLayout(right = pages(ALL_APPS, WIDGETS), left = SwipeTarget.None, up = pages(QUICK))
