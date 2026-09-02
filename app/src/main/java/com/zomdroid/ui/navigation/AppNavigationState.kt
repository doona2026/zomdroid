package com.zomdroid.ui.navigation

import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavKey

enum class RootDestination {
    Instances,
    Workshop,
    Tools,
    Settings,
}

fun NavKey.rootDestination(): RootDestination = when (this) {
    is AppRoute.Instances,
    is AppRoute.InstanceDetail,
    AppRoute.NewInstance,
    -> RootDestination.Instances

    is AppRoute.Workshop,
    is AppRoute.WorkshopDetail,
    AppRoute.WorkshopDownloads,
    AppRoute.WorkshopLibrary,
    AppRoute.WorkshopAccount,
    -> RootDestination.Workshop

    is AppRoute.Tools,
    is AppRoute.ToolDetail,
    -> RootDestination.Tools

    is AppRoute.Settings,
    is AppRoute.SettingsCategory,
    -> RootDestination.Settings

    else -> RootDestination.Instances
}

fun NavBackStack.selectRoot(destination: RootDestination) {
    val current = lastOrNull()?.rootDestination()
    if (current == destination && size == 1) return

    clear()
    add(
        when (destination) {
            RootDestination.Instances -> AppRoute.Instances
            RootDestination.Workshop -> AppRoute.Workshop
            RootDestination.Tools -> AppRoute.Tools
            RootDestination.Settings -> AppRoute.Settings
        },
    )
}
