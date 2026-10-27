package dev.jotalac.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.jotalac.AppContainer
import dev.jotalac.EditorRequest
import dev.jotalac.ui.calendar.CalendarNavigation
import dev.jotalac.ui.calendar.CalendarScreen
import dev.jotalac.ui.calendar.CalendarViewModel
import dev.jotalac.ui.calendar.newEventDraft
import dev.jotalac.ui.calendars.CalendarEditorScreen
import dev.jotalac.ui.calendars.CalendarEditorViewModel
import dev.jotalac.ui.event.EventEditorScreen
import dev.jotalac.ui.event.EventEditorViewModel
import dev.jotalac.ui.settings.FilesScreen
import dev.jotalac.ui.settings.GuideScreen
import dev.jotalac.ui.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable object CalendarRoute
@Serializable object EventEditorRoute
@Serializable data class CalendarEditorRoute(val name: String? = null)
@Serializable object SettingsRoute
@Serializable object GuideRoute
@Serializable object FilesRoute

@Composable
fun AppNavigation(container: AppContainer) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }

    // the widget's new-event button: open the editor with a draft once calendars are loaded
    LaunchedEffect(Unit) {
        container.newEventRequested.collect { requested ->
            if (!requested) return@collect
            container.newEventRequested.value = false
            if (container.ready.await() != null) return@collect
            container.editorRequest.value = EditorRequest(newEventDraft(container.repository.defaultCalendar()), null)
            nav.navigate(EventEditorRoute) { launchSingleTop = true }
        }
    }

    NavHost(nav, startDestination = CalendarRoute) {
        composable<CalendarRoute> {
            val vm = viewModel { CalendarViewModel(container, createSavedStateHandle()) }
            CalendarScreen(
                vm,
                CalendarNavigation(
                    openEditor = { nav.navigate(EventEditorRoute) { launchSingleTop = true } },
                    createCalendar = { nav.navigate(CalendarEditorRoute()) },
                    editCalendar = { nav.navigate(CalendarEditorRoute(it.name)) },
                    settings = { nav.navigate(SettingsRoute) },
                    guide = { nav.navigate(GuideRoute) },
                ),
            )
        }
        composable<EventEditorRoute> {
            val vm = viewModel { EventEditorViewModel(container) }
            val settings by container.settings.settings.collectAsStateWithLifecycle()
            EventEditorScreen(vm, settings, back)
        }
        composable<CalendarEditorRoute> { entry ->
            val route = entry.toRoute<CalendarEditorRoute>()
            val vm = viewModel { CalendarEditorViewModel(container, route.name) }
            CalendarEditorScreen(vm, back)
        }
        composable<SettingsRoute> { SettingsScreen(container, back) { nav.navigate(FilesRoute) } }
        composable<GuideRoute> { GuideScreen(back) }
        composable<FilesRoute> { FilesScreen(container.core.storageRoot, back) }
    }
}
