package com.zomdroid.ui.model

import com.zomdroid.ui.settings.SettingsTool

enum class AppModule {
    Launcher,
    Workshop,
    Downloads,
    ModLibrary,
    Settings,
}

enum class LegacyDestination {
    Settings,
    Workshop,
    Downloads,
    ModLibrary,
}

sealed interface AppDestination {
    val module: AppModule

    data class ModuleHome(override val module: AppModule) : AppDestination
    data object NewGameInstance : AppDestination { override val module = AppModule.Launcher }
    data object GameSettings : AppDestination { override val module = AppModule.Launcher }
    data class SettingsToolPage(val tool: SettingsTool) : AppDestination { override val module = AppModule.Settings }
    data class WorkshopDetail(val workshopId: Long) : AppDestination { override val module = AppModule.Workshop }
    data object WorkshopAccount : AppDestination { override val module = AppModule.Workshop }
    data object SteamDownload : AppDestination { override val module = AppModule.Downloads }
    data class DownloadTaskDetail(val taskId: String) : AppDestination { override val module = AppModule.Downloads }
    data class ModDetail(val workshopId: Long) : AppDestination { override val module = AppModule.ModLibrary }
}
