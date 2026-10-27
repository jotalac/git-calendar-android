package dev.jotalac.ui.calendar

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.jotalac.R
import dev.jotalac.core.Calendar
import dev.jotalac.data.CalendarViewType
import dev.jotalac.data.SyncStatus
import dev.jotalac.ui.common.MessageDialog
import dev.jotalac.ui.common.currentLocale
import dev.jotalac.ui.common.title
import dev.jotalac.ui.common.userMessage
import kotlinx.coroutines.launch
import java.time.YearMonth

private const val PAGE_COUNT = 20_000
private const val INITIAL_PAGE = PAGE_COUNT / 2

class CalendarNavigation(
    val openEditor: () -> Unit,
    val createCalendar: () -> Unit,
    val editCalendar: (Calendar) -> Unit,
    val settings: () -> Unit,
    val guide: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(vm: CalendarViewModel, nav: CalendarNavigation) {
    val context = LocalContext.current
    val view by vm.view.collectAsStateWithLifecycle()
    val date by vm.date.collectAsStateWithLifecycle()
    val anchor by vm.anchor.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val calendars by vm.calendars.collectAsStateWithLifecycle()
    val hidden by vm.hiddenTags.collectAsStateWithLifecycle()
    val revision by vm.revision.collectAsStateWithLifecycle()
    val syncStatus by vm.syncStatus.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val startupError by vm.startupError.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var tagTarget by remember { mutableStateOf<TagEditTarget?>(null) }
    var syncErrorDetails by rememberSaveable { mutableStateOf<String?>(null) }

    val syncFailed = stringResource(R.string.sync_failed)
    val details = stringResource(R.string.details)
    LaunchedEffect(Unit) {
        vm.syncErrors.collect { e ->
            val text = e.userMessage(context)
            val result = snackbar.showSnackbar(syncFailed, actionLabel = details, withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) syncErrorDetails = text
        }
    }

    val actions = CalendarActions(
        open = { event ->
            vm.prepareEditor(event)
            nav.openEditor()
        },
        create = { draft ->
            vm.prepareEditor(draft)
            nav.openEditor()
        },
        changed = { updated, original -> vm.applyDrag(updated, original, nav.openEditor) },
        openWeek = { day ->
            vm.setView(CalendarViewType.Week)
            vm.jumpTo(day)
        },
    )

    val closeDrawer: () -> Unit = { scope.launch { drawerState.close() } }
    val drawerActions = DrawerActions(
        pickDate = { vm.jumpTo(it); closeDrawer() },
        createCalendar = { closeDrawer(); nav.createCalendar() },
        editCalendar = { closeDrawer(); nav.editCalendar(it) },
        editTag = { tagTarget = it },
        toggleTag = vm::setTagHidden,
        settings = { closeDrawer(); nav.settings() },
        guide = { closeDrawer(); nav.guide() },
        sync = { vm.requestSync(); closeDrawer() },
        sourceCode = {
            context.startActivity(Intent(Intent.ACTION_VIEW, "https://github.com/git-calendar".toUri()))
        },
    )

    // Hardware keyboard shortcuts from web-client useKeyboard.ts.
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val shortcuts = Modifier
        .focusRequester(focus)
        .focusable()
        .onKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (e.key) {
                Key.N -> actions.create(newEventDraft(vm.defaultCalendar()))
                Key.T -> vm.today()
                Key.Four, Key.NumPad4 -> vm.setView(CalendarViewType.FourDays)
                Key.W -> vm.setView(CalendarViewType.Week)
                Key.M -> vm.setView(CalendarViewType.Month)
                Key.DirectionLeft -> vm.shift(-1)
                Key.DirectionRight -> vm.shift(1)
                else -> return@onKeyEvent false
            }
            true
        }

    BoxWithConstraints(Modifier.fillMaxSize().then(shortcuts)) {
        val wide = maxWidth >= 840.dp
        val drawer: @Composable () -> Unit = {
            DrawerContent(view, date, settings, calendars, hidden, drawerActions)
        }
        val content: @Composable () -> Unit = {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                YearMonth.from(viewStart(view, date, settings.weekStart)).title(currentLocale()),
                                maxLines = 1,
                            )
                        },
                        navigationIcon = {
                            if (!wide) {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, stringResource(R.string.open_drawer))
                                }
                            }
                        },
                        actions = {
                            SyncStatusButton(syncStatus, vm::requestSync)
                            if (wide) {
                                IconButton(onClick = { vm.shift(-1) }) {
                                    Icon(Icons.Filled.ChevronLeft, stringResource(R.string.previous_period))
                                }
                                IconButton(onClick = { vm.shift(1) }) {
                                    Icon(Icons.Filled.ChevronRight, stringResource(R.string.next_period))
                                }
                            }
                            IconButton(onClick = vm::today) {
                                Icon(Icons.Outlined.Today, stringResource(R.string.today))
                            }
                            ViewSwitcher(view, wide, vm::setView)
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    )
                },
                floatingActionButton = {
                    FloatingActionButton(onClick = { actions.create(newEventDraft(vm.defaultCalendar())) }) {
                        Icon(Icons.Filled.Add, stringResource(R.string.new_event))
                    }
                },
                snackbarHost = { SnackbarHost(snackbar) },
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    CalendarPager(view, anchor, vm, settings, revision, hidden, actions)
                }
            }
        }

        if (wide) {
            Row(Modifier.fillMaxSize()) {
                PermanentDrawerSheet(Modifier.width(300.dp)) { drawer() }
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            BackHandler(enabled = drawerState.isOpen) { closeDrawer() }
            ModalNavigationDrawer(
                drawerState = drawerState,
                // Edge swipes would fight the calendar's own horizontal paging.
                gesturesEnabled = drawerState.isOpen,
                drawerContent = { ModalDrawerSheet { drawer() } },
            ) { content() }
        }
    }

    tagTarget?.let { target ->
        TagDialog(
            target = target,
            defaultColor = settings.defaultEventColor,
            onSave = { name, color -> vm.saveTag(target, name, color) },
            onDelete = { vm.removeTag(target) },
            onDismiss = { tagTarget = null },
        )
    }
    error?.let { MessageDialog(it.userMessage(context), vm::dismissError) }
    startupError?.let {
        MessageDialog(it.userMessage(context), { vm.startupError.value = null }, stringResource(R.string.startup_failed))
    }
    syncErrorDetails?.let { MessageDialog(it, { syncErrorDetails = null }, syncFailed) }
}

@Composable
private fun CalendarPager(
    view: CalendarViewType,
    anchor: Pair<java.time.LocalDate, Int>,
    vm: CalendarViewModel,
    settings: dev.jotalac.data.Settings,
    revision: Long,
    hidden: dev.jotalac.data.HiddenTags,
    actions: CalendarActions,
) {
    // Re-create the pager around the new date whenever the date is set from outside (today, mini month).
    key(anchor, view) {
        val anchorDate = anchor.first
        val pager = rememberPagerState(INITIAL_PAGE) { PAGE_COUNT }
        LaunchedEffect(pager) {
            snapshotFlow { pager.settledPage }.collect { page ->
                vm.onPageDate(shiftPeriod(view, anchorDate, (page - INITIAL_PAGE).toLong()))
            }
        }
        HorizontalPager(pager, beyondViewportPageCount = 1, key = { it }) { page ->
            val date = shiftPeriod(view, anchorDate, (page - INITIAL_PAGE).toLong())
            val start = viewStart(view, date, settings.weekStart)
            when (view) {
                CalendarViewType.Month -> MonthView(start, vm, settings, revision, hidden, actions)
                else -> XDaysView(start, view.days, vm, settings, revision, hidden, actions)
            }
        }
    }
}

@Composable
private fun SyncStatusButton(status: SyncStatus, onSync: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "sync")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "sync-rotation",
    )
    val (icon, label) = when (status) {
        SyncStatus.Offline -> Icons.Outlined.CloudOff to R.string.sync_status_offline
        SyncStatus.Idle -> Icons.Outlined.CloudDone to R.string.sync_status_idle
        SyncStatus.Syncing -> Icons.Outlined.Sync to R.string.sync_status_syncing
    }
    IconButton(onClick = onSync, enabled = status != SyncStatus.Syncing) {
        Icon(
            icon,
            stringResource(label),
            Modifier.rotate(if (status == SyncStatus.Syncing) angle else 0f),
            tint = when (status) {
                SyncStatus.Offline -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private val CalendarViewType.label: Int
    get() = when (this) {
        CalendarViewType.FourDays -> R.string.view_4d
        CalendarViewType.Week -> R.string.view_week
        CalendarViewType.Month -> R.string.view_month
    }

private val CalendarViewType.icon: ImageVector
    get() = when (this) {
        CalendarViewType.FourDays -> Icons.Outlined.ViewWeek
        CalendarViewType.Week -> Icons.Outlined.CalendarViewWeek
        CalendarViewType.Month -> Icons.Outlined.CalendarMonth
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ViewSwitcher(view: CalendarViewType, wide: Boolean, onSelect: (CalendarViewType) -> Unit) {
    if (wide) {
        SingleChoiceSegmentedButtonRow(Modifier.padding(end = 8.dp)) {
            CalendarViewType.entries.forEachIndexed { i, option ->
                SegmentedButton(
                    selected = option == view,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(i, CalendarViewType.entries.size),
                ) { Text(stringResource(option.label)) }
            }
        }
        return
    }
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(view.icon, stringResource(view.label)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            CalendarViewType.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.label)) },
                    leadingIcon = { Icon(option.icon, null) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
