package com.focus.launcher.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focus.launcher.R
import com.focus.launcher.data.CALENDAR_ALL
import com.focus.launcher.data.CALENDAR_AUTO
import com.focus.launcher.data.CalendarInfo
import com.focus.launcher.data.calendarSelection
import com.focus.launcher.data.isCalendarKey
import com.focus.launcher.ui.components.DialogButtons
import com.focus.launcher.ui.components.FocusDialog
import com.focus.launcher.ui.components.Hairline
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.UnderlinedField
import com.focus.launcher.ui.theme.LocalFocusColors

/**
 * Picks the calendars shown together on the home screen, or the automatic choice, or all of them;
 * the choice is kept on Done. Phones with a few Google accounts easily carry a dozen calendars,
 * half of them called "Holidays", so the list can be searched by calendar or account name, and
 * every entry says how many events it has coming up: the quickest way to recognise the ones you
 * live in.
 */
@Composable
internal fun CalendarPickerDialog(
    calendars: List<CalendarInfo>,
    upcomingCounts: Map<String, Int>,
    selectedKeys: Set<String>,
    workProfileBlocked: Boolean,
    onDismiss: () -> Unit,
    onPick: (Set<String>) -> Unit,
) {
    val c = LocalFocusColors.current
    var query by remember { mutableStateOf(TextFieldValue("")) }
    // Ticked calendars that are not listed right now (a paused Work profile) stay ticked, so they
    // come back with their calendars. While none of the ticked ones is listed, the automatic
    // choice stands in on the home screen, and the list says so.
    var current by remember { mutableStateOf(calendarSelection(selectedKeys)) }
    val listedKeys = remember(calendars) { calendars.map { it.key }.toSet() }
    val automatic = CALENDAR_ALL !in current && current.none { it in listedKeys }
    val shown = remember(calendars, query.text) {
        val q = query.text.trim()
        if (q.isEmpty()) calendars
        else calendars.filter { it.name.contains(q, ignoreCase = true) || it.account.contains(q, ignoreCase = true) }
    }

    FocusDialog(
        onDismiss,
        title = stringResource(R.string.settings_calendar_to_show),
        subtitle = stringResource(R.string.settings_calendar_picker_subtitle),
        tall = true,
    ) {
        UnderlinedField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.settings_calendar_search),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            imeAction = ImeAction.Search,
        )
        LazyColumn(Modifier.weight(1f)) {
            if (workProfileBlocked) {
                item(key = "work-note") {
                    Column {
                        T(
                            stringResource(R.string.settings_calendar_picker_work_note),
                            Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                            size = 13.sp, color = c.dim, lineHeight = 19.sp,
                        )
                        Hairline()
                    }
                }
            }
            items(shown, key = { it.key }) { calendar ->
                val count = upcomingCounts[calendar.key] ?: 0
                CalendarRow(
                    name = calendar.name,
                    account = calendar.account.takeIf { it.isNotEmpty() && it != calendar.name },
                    detail = if (count > 0) pluralStringResource(R.plurals.settings_calendar_upcoming, count, count) else stringResource(R.string.settings_calendar_empty),
                    selected = calendar.key in current,
                ) {
                    val ticked = current.filter(::isCalendarKey).toSet()
                    current = calendarSelection(if (calendar.key in ticked) ticked - calendar.key else ticked + calendar.key)
                }
            }
            if (shown.isEmpty()) {
                item(key = "none") {
                    T(stringResource(R.string.settings_calendar_no_match, query.text.trim()), Modifier.padding(horizontal = 24.dp, vertical = 16.dp), size = 15.sp, color = c.dim)
                }
            }
            if (query.text.isBlank()) {
                item(key = "auto") {
                    CalendarRow(
                        stringResource(R.string.settings_calendar_auto),
                        account = stringResource(R.string.settings_calendar_auto_summary),
                        detail = null,
                        selected = automatic,
                    ) { current = setOf(CALENDAR_AUTO) }
                }
                item(key = "all") {
                    CalendarRow(stringResource(R.string.settings_calendar_all_together), account = null, detail = null, selected = CALENDAR_ALL in current) {
                        current = setOf(CALENDAR_ALL)
                    }
                }
            }
        }
        DialogButtons(onDismiss, confirmLabel = stringResource(R.string.common_done)) {
            onPick(current)
            onDismiss()
        }
    }
}

@Composable
private fun CalendarRow(name: String, account: String?, detail: String?, selected: Boolean, onClick: () -> Unit) {
    val c = LocalFocusColors.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            T(name, size = 17.sp, maxLines = 1)
            if (account != null) T(account, Modifier.padding(top = 2.dp), size = 12.sp, color = c.faint, maxLines = 1)
        }
        if (detail != null) T(detail, size = 12.sp, color = c.dim, maxLines = 1)
        if (selected) Box(Modifier.size(8.dp).background(c.fg, CircleShape))
    }
}
