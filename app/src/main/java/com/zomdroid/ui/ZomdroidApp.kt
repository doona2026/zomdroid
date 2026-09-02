package com.zomdroid.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zomdroid.R
import com.zomdroid.ui.common.UiTokens
import com.zomdroid.ui.navigation.AppRoute
import com.zomdroid.ui.navigation.RootDestination
import com.zomdroid.ui.navigation.rootDestination
import com.zomdroid.ui.navigation.selectRoot
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.NavigationBar
import top.yukonga.miuix.kmp.basic.NavigationBarItem
import top.yukonga.miuix.kmp.basic.NavigationRail
import top.yukonga.miuix.kmp.basic.NavigationRailItem
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.Home
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.nav.core.NavBackStack
import top.yukonga.miuix.kmp.nav.core.NavDisplay
import top.yukonga.miuix.kmp.nav.core.rememberNavBackStack

@Composable
fun ZomdroidApp() {
    ZomdroidTheme {
        val backStack = rememberNavBackStack<AppRoute>(AppRoute.Instances)
        val currentDestination = backStack.lastOrNull()?.rootDestination()
            ?: RootDestination.Instances
        val isWide = LocalConfiguration.current.screenWidthDp >= 600

        BackHandler(enabled = backStack.size > 1) {
            backStack.removeLastOrNull()
        }

        if (isWide) {
            WideShell(
                currentDestination = currentDestination,
                onDestinationSelected = backStack::selectRoot,
                backStack = backStack,
            )
        } else {
            CompactShell(
                currentDestination = currentDestination,
                onDestinationSelected = backStack::selectRoot,
                backStack = backStack,
            )
        }
    }
}

@Composable
private fun CompactShell(
    currentDestination: RootDestination,
    onDestinationSelected: (RootDestination) -> Unit,
    backStack: NavBackStack,
) {
    Scaffold(
        topBar = { AppTopBar(currentDestination) },
        bottomBar = {
            NavigationBar {
                RootDestination.values().forEach { destination ->
                    NavigationBarItem(
                        selected = currentDestination == destination,
                        onClick = { onDestinationSelected(destination) },
                        icon = destination.icon,
                        label = destination.label(),
                    )
                }
            }
        },
    ) { paddingValues ->
        ShellContent(backStack, paddingValues)
    }
}

@Composable
private fun WideShell(
    currentDestination: RootDestination,
    onDestinationSelected: (RootDestination) -> Unit,
    backStack: NavBackStack,
) {
    Scaffold(topBar = { AppTopBar(currentDestination) }) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            NavigationRail {
                RootDestination.values().forEach { destination ->
                    NavigationRailItem(
                        selected = currentDestination == destination,
                        onClick = { onDestinationSelected(destination) },
                        icon = destination.icon,
                        label = destination.label(),
                    )
                }
            }
            ShellContent(backStack, PaddingValues(0.dp))
        }
    }
}

@Composable
private fun AppTopBar(destination: RootDestination) {
    TopAppBar(
        title = destination.label(),
        largeTitle = destination.label(),
        subtitle = stringResource(R.string.ui_shell_subtitle),
    )
}

@Composable
private fun ShellContent(backStack: NavBackStack, paddingValues: PaddingValues) {
    NavDisplay(
        backStack = backStack,
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        onBack = { backStack.removeLastOrNull() },
    ) {
        entry<AppRoute.Instances> {
            MigrationPlaceholder(
                title = stringResource(R.string.ui_instances_title),
                body = stringResource(R.string.ui_instances_stage1_body),
            )
        }
        entry<AppRoute.Workshop> {
            MigrationPlaceholder(
                title = stringResource(R.string.ui_workshop_title),
                body = stringResource(R.string.ui_workshop_stage1_body),
            )
        }
        entry<AppRoute.Tools> {
            MigrationPlaceholder(
                title = stringResource(R.string.ui_tools_title),
                body = stringResource(R.string.ui_tools_stage1_body),
            )
        }
        entry<AppRoute.Settings> {
            MigrationPlaceholder(
                title = stringResource(R.string.ui_settings_title),
                body = stringResource(R.string.ui_settings_stage1_body),
            )
        }
    }
}

@Composable
private fun MigrationPlaceholder(title: String, body: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = UiTokens.ContentMaxWidth)
                .fillMaxWidth()
                .padding(UiTokens.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            Text(text = title, fontSize = UiTokens.TitleTextSize)
            Text(text = body)
            Button(
                onClick = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = UiTokens.CompactSpacing),
            ) {
                Text(text = stringResource(R.string.ui_stage1_ready))
            }
        }
    }
}

private val RootDestination.icon
    get() = when (this) {
        RootDestination.Instances -> MiuixIcons.Home
        RootDestination.Workshop -> MiuixIcons.Download
        RootDestination.Tools -> MiuixIcons.File
        RootDestination.Settings -> MiuixIcons.Settings
    }

@Composable
private fun RootDestination.label(): String = when (this) {
    RootDestination.Instances -> stringResource(R.string.ui_nav_instances)
    RootDestination.Workshop -> stringResource(R.string.ui_nav_workshop)
    RootDestination.Tools -> stringResource(R.string.ui_nav_tools)
    RootDestination.Settings -> stringResource(R.string.ui_nav_settings)
}
