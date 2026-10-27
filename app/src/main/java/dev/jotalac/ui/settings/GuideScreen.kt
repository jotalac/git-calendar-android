package dev.jotalac.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.jotalac.R

private data class GuideSection(val title: String, val steps: List<String>, val note: String? = null)

// Like the web client's Guide.vue, the guide is English only; its gesture tips are adapted to touch.
private val SECTIONS = listOf(
    GuideSection(
        "Quick start",
        listOf(
            "Add a repository: open the menu and tap + next to Calendars.",
            "Create an event: tap the + button, or press and hold an empty time range and drag.",
            "Sync changes: all changes are synced automatically when a calendar has a Git remote.",
        ),
    ),
    GuideSection(
        "Create a repository",
        listOf(
            "Tap + next to Calendars and choose Init.",
            "Enter a name, such as My Calendar.",
            "For remote syncing, create an empty repository on your Git provider and paste its HTTPS URL. The URL must end in .git.",
            "For a private remote, enter your username and/or access token/password.",
            "Enable encryption if desired, save the key somewhere safe, and create the repository.",
        ),
    ),
    GuideSection(
        "Clone a repository",
        listOf(
            "Copy the HTTPS clone URL, for example https://host/user/calendar.git.",
            "Tap + next to Calendars and choose Clone.",
            "Paste the URL and add credentials if the repository is private.",
            "Enable Read-only if you only want to view the calendar.",
            "If the repository is encrypted, enable encryption and enter its existing key.",
        ),
        "Encryption keys cannot be recovered from the repository. You need the same key on every device that clones it.",
    ),
    GuideSection(
        "Importing iCalendar",
        listOf(
            "Import a file: events from an .ics file are copied into a writable repository and become regular Git Calendar events. Later changes to the original file are not synchronized.",
            "Subscribe to a feed: an HTTPS URL to an .ics feed creates a read-only calendar. The feed is refreshed during sync, and the last downloaded copy stays available offline.",
        ),
        "To change events in a subscribed calendar, edit them in the service that publishes the feed.",
    ),
    GuideSection(
        "Gestures",
        listOf(
            "Open: tap an event to see or edit it.",
            "Create: press and hold empty time or the all-day row, drag over the range, then release to open the event form.",
            "Move: press and hold an event, then drag it vertically to change its time or sideways to another day. In the month view, drag it to another day.",
            "Resize: press and hold near the top or bottom edge of an event and drag.",
            "Precision: change the drag interval in Settings.",
            "Next and previous period: swipe left or right across the calendar.",
            "Day: tap a day in the month view to open it on its own; Back returns to the month.",
            "Zoom: pinch the timeline to make hours taller, then scroll vertically. Swiping to another period keeps the zoom and scroll position.",
        ),
        "Events in read-only repositories cannot be dragged or edited.",
    ),
    GuideSection(
        "Keyboard shortcuts",
        listOf(
            "N: create a new event",
            "T: go to today",
            "D / 4 / W / M: switch to the day, four-day, week or month view",
            "← / →: move to the previous or next period",
        ),
        "Shortcuts work with a hardware keyboard while the calendar is open.",
    ),
    GuideSection(
        "Other controls",
        listOf(
            "Filter: use the checkboxes under each repository to show or hide its tags.",
            "Month map: use the small calendar in the menu to jump directly to a date.",
            "Edit: tap an event to update its details, repeat rule, repository, or tag.",
            "Reminders: add reminders to an event to get a notification before it starts.",
            "Private remotes: prefer an access token over your Git provider account password.",
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("How to use Git Calendar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Repositories are your calendars, and events are stored in them. You can work locally or connect a remote Git repository to sync your calendars.",
                style = MaterialTheme.typography.bodyLarge,
            )
            SECTIONS.forEach { section ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(section.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    section.steps.forEachIndexed { i, step ->
                        Row {
                            Text("${i + 1}.", Modifier.width(24.dp), style = MaterialTheme.typography.bodyMedium)
                            Text(step, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    section.note?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
