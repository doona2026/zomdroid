package com.zomdroid.ui.model

sealed interface AppAction {
    data class NavigateToModule(val module: AppModule) : AppAction
    data class OpenDestination(val destination: AppDestination) : AppAction
    data class OpenLegacyModule(val module: AppModule, val legacyDestination: LegacyDestination) : AppAction
    data object Back : AppAction
    data class SetAppearanceMode(val mode: AppearanceMode) : AppAction
    data class ShowSnackbar(val message: String) : AppAction
    data class RequestPermission(val permission: String) : AppAction
    data class PublishInstallTask(val title: String, val message: String? = null) : AppAction
    data class PublishDownloadTask(val title: String, val message: String? = null) : AppAction
    data class ConsumeEvent(val eventId: Long) : AppAction
    data class ShowConfirmation(val title: String, val message: String) : AppAction
    data object DismissDialog : AppAction
}
