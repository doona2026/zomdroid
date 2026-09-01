package com.zomdroid.ui.model

sealed interface AppEvent {
    val id: Long

    data class Snackbar(override val id: Long, val message: String) : AppEvent
    data class PermissionRequest(override val id: Long, val permission: String) : AppEvent
    data class InstallTask(override val id: Long, val title: String, val message: String?) : AppEvent
    data class DownloadTask(override val id: Long, val title: String, val message: String?) : AppEvent
    data class OpenLegacy(override val id: Long, val destination: LegacyDestination) : AppEvent
}

data class AppConfirmationDialog(val title: String, val message: String)

data class AppUiState(
    val selectedModule: AppModule = AppModule.Launcher,
    val backStack: List<AppDestination> = listOf(AppDestination.ModuleHome(AppModule.Launcher)),
    val appearanceMode: AppearanceMode = AppearanceMode.default,
    val events: List<AppEvent> = emptyList(),
    val dialog: AppConfirmationDialog? = null,
    val nextEventId: Long = 1L,
)

object AppUiReducer {
    fun reduce(state: AppUiState, action: AppAction): AppUiState = when (action) {
        is AppAction.NavigateToModule -> state.copy(
            selectedModule = action.module,
            backStack = listOf(AppDestination.ModuleHome(action.module)),
        )
        is AppAction.OpenDestination -> {
            val sameModule = state.backStack.lastOrNull()?.module == action.destination.module
            state.copy(
                selectedModule = action.destination.module,
                backStack = if (state.backStack.lastOrNull() == action.destination) state.backStack
                else if (sameModule) state.backStack + action.destination
                else listOf(AppDestination.ModuleHome(action.destination.module), action.destination),
            )
        }
        is AppAction.OpenLegacyModule -> state.copy(
            selectedModule = action.module,
            backStack = listOf(AppDestination.ModuleHome(action.module)),
        ).withEvent { id -> AppEvent.OpenLegacy(id, action.legacyDestination) }
        AppAction.Back -> if (state.backStack.size > 1) {
            val newStack = state.backStack.dropLast(1)
            state.copy(selectedModule = newStack.last().module, backStack = newStack)
        } else state
        is AppAction.SetAppearanceMode -> state.copy(appearanceMode = action.mode)
        is AppAction.ShowSnackbar -> state.withEvent { id -> AppEvent.Snackbar(id, action.message) }
        is AppAction.RequestPermission -> state.withEvent { id -> AppEvent.PermissionRequest(id, action.permission) }
        is AppAction.PublishInstallTask -> state.withEvent { id -> AppEvent.InstallTask(id, action.title, action.message) }
        is AppAction.PublishDownloadTask -> state.withEvent { id -> AppEvent.DownloadTask(id, action.title, action.message) }
        is AppAction.ConsumeEvent -> state.copy(events = state.events.filterNot { it.id == action.eventId })
        is AppAction.ShowConfirmation -> state.copy(dialog = AppConfirmationDialog(action.title, action.message))
        AppAction.DismissDialog -> state.copy(dialog = null)
    }

    private fun AppUiState.withEvent(factory: (Long) -> AppEvent): AppUiState =
        copy(events = events + factory(nextEventId), nextEventId = nextEventId + 1L)
}
