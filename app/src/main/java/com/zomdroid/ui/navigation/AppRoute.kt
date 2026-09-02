package com.zomdroid.ui.navigation

import kotlinx.serialization.Serializable
import top.yukonga.miuix.kmp.nav.core.NavKey

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable
    data object Instances : AppRoute

    @Serializable
    data object Workshop : AppRoute

    @Serializable
    data object Tools : AppRoute

    @Serializable
    data object Settings : AppRoute

    @Serializable
    data class InstanceDetail(val instanceId: String) : AppRoute

    @Serializable
    data object NewInstance : AppRoute

    @Serializable
    data class WorkshopDetail(val workshopId: Long) : AppRoute

    @Serializable
    data object WorkshopDownloads : AppRoute

    @Serializable
    data object WorkshopLibrary : AppRoute

    @Serializable
    data object WorkshopAccount : AppRoute

    @Serializable
    data class ToolDetail(val toolId: String) : AppRoute

    @Serializable
    data class SettingsCategory(val categoryId: String) : AppRoute
}
