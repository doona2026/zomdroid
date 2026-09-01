package com.zomdroid.ui

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zomdroid.ui.component.ZomdroidBackdropScaffold
import com.zomdroid.ui.model.AppAction
import com.zomdroid.ui.model.AppEvent
import com.zomdroid.ui.model.AppModule
import com.zomdroid.ui.model.AppDestination
import com.zomdroid.ui.model.LegacyDestination
import com.zomdroid.ui.launcher.LauncherAction
import com.zomdroid.ui.launcher.LauncherEvent
import com.zomdroid.ui.launcher.LauncherNotice
import com.zomdroid.ui.launcher.GameSettingsScreen
import com.zomdroid.ui.launcher.LauncherScreen
import com.zomdroid.ui.launcher.NewGameInstanceScreen
import com.zomdroid.ui.settings.ControlsEditorLaunchScreen
import com.zomdroid.ui.settings.GamepadMapperScreen
import com.zomdroid.ui.settings.GamepadMapperViewModel
import com.zomdroid.ui.settings.SettingsScreen
import com.zomdroid.ui.settings.SettingsTool
import com.zomdroid.ui.settings.TouchControlsScreen
import com.zomdroid.ui.tools.ExportLogScreen
import com.zomdroid.ui.tools.InstallControlsScreen
import com.zomdroid.ui.tools.InstallDriverScreen
import com.zomdroid.ui.tools.InstallModScreen
import com.zomdroid.ui.tools.InstallNativeLibsScreen
import com.zomdroid.ui.tools.InstallSavesScreen
import com.zomdroid.ui.tools.ModFixesScreen
import com.zomdroid.ui.tools.OptimizationScreen
import com.zomdroid.ui.tools.ToolTaskViewModel
import com.zomdroid.ui.tools.WikiScreen
import com.zomdroid.ui.workshop.WorkshopScreen
import com.zomdroid.ui.workshop.WorkshopDetailScreen
import com.zomdroid.ui.workshop.WorkshopAccountScreen
import com.zomdroid.ui.workshop.WorkshopViewModel
import com.zomdroid.ui.download.DownloadCenterScreen
import com.zomdroid.ui.download.DownloadTaskDetailScreen
import com.zomdroid.ui.download.DownloadViewModel
import com.zomdroid.ui.download.SteamDownloadScreen
import com.zomdroid.ui.download.SteamDownloadViewModel
import com.zomdroid.ui.library.ModLibraryScreen
import com.zomdroid.ui.library.ModDetailScreen
import com.zomdroid.ui.library.ModLibraryViewModel
import com.zomdroid.ui.theme.AppThemeMode
import com.zomdroid.ui.theme.ZomdroidTheme
import com.zomdroid.ui.viewmodel.AppViewModel
import com.zomdroid.ui.viewmodel.GameSettingsViewModel
import com.zomdroid.ui.viewmodel.LauncherViewModel
import com.zomdroid.ui.viewmodel.NewGameInstanceViewModel
import com.zomdroid.ui.settings.SettingsViewModel

interface ZomdroidHostCallbacks {
    fun onOpenLegacy(destination: LegacyDestination)
    fun onRequestPermission(permission: String)
    fun onOpenLegacyMenu()
    fun onLaunchGame(instanceName: String)
    fun onOpenStorage(homePath: String)
    fun onOpenWiki()
    fun onOpenGamepadMapper()
    fun onOpenControlsEditor(instanceName: String, backgroundPath: String?)
    fun onOpenExternalUrl(url: String)
    fun onRequestAllFilesAccess()
}

@Composable
fun ZomdroidApp(
    viewModel: AppViewModel,
    themeMode: AppThemeMode = AppThemeMode.FollowSystem,
    onRequestPermission: (String) -> Unit = {},
    onLaunchGame: (String) -> Unit = {},
    onOpenStorage: (String) -> Unit = {},
    onOpenGamepadMapper: () -> Unit = {},
    onOpenControlsEditor: (String, String?) -> Unit = { _, _ -> },
    onOpenExternalUrl: (String) -> Unit = {},
    onRequestAllFilesAccess: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val launcherViewModel: LauncherViewModel = viewModel(factory = LauncherViewModel.factory(context))
    val newGameViewModel: NewGameInstanceViewModel = viewModel(factory = NewGameInstanceViewModel.factory(context))
    val gameSettingsViewModel: GameSettingsViewModel = viewModel(factory = GameSettingsViewModel.factory(context))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(context))
    val toolTaskViewModel: ToolTaskViewModel = viewModel(factory = ToolTaskViewModel.factory(context))
    val gamepadMapperViewModel = remember { GamepadMapperViewModel(context.applicationContext) }
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val workshopViewModel: WorkshopViewModel = viewModel(factory = WorkshopViewModel.factory(context))
    val downloadViewModel: DownloadViewModel = viewModel(factory = DownloadViewModel.factory(context))
    val steamDownloadViewModel: SteamDownloadViewModel = viewModel(factory = SteamDownloadViewModel.factory(context))
    val libraryViewModel: ModLibraryViewModel = viewModel(factory = ModLibraryViewModel.factory(context))

    LaunchedEffect(state.events) {
        state.events.forEach { event ->
            when (event) {
                is AppEvent.Snackbar -> {
                    snackbarHostState.showSnackbar(event.message)
                    viewModel.dispatch(AppAction.ConsumeEvent(event.id))
                }
                is AppEvent.InstallTask -> {
                    snackbarHostState.showSnackbar(event.message ?: event.title)
                    viewModel.dispatch(AppAction.ConsumeEvent(event.id))
                }
                is AppEvent.DownloadTask -> {
                    snackbarHostState.showSnackbar(event.message ?: event.title)
                    viewModel.dispatch(AppAction.ConsumeEvent(event.id))
                }
                is AppEvent.PermissionRequest -> {
                    onRequestPermission(event.permission)
                    viewModel.dispatch(AppAction.ConsumeEvent(event.id))
                }
                is AppEvent.OpenLegacy -> {
                    viewModel.dispatch(AppAction.NavigateToModule(event.destination.toModule()))
                    viewModel.dispatch(AppAction.ConsumeEvent(event.id))
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        launcherViewModel.events.collect { event ->
            when (event) {
                is LauncherEvent.LaunchGame -> onLaunchGame(event.instanceName)
                is LauncherEvent.OpenStorage -> onOpenStorage(event.homePath)
                LauncherEvent.OpenWiki -> viewModel.dispatch(AppAction.OpenDestination(AppDestination.SettingsToolPage(SettingsTool.Wiki)))
                LauncherEvent.OpenNewGameInstance -> viewModel.dispatch(AppAction.OpenDestination(AppDestination.NewGameInstance))
                LauncherEvent.OpenGameSettings -> viewModel.dispatch(AppAction.OpenDestination(AppDestination.GameSettings))
                is LauncherEvent.ShowNotice -> snackbarHostState.showSnackbar(context.getString(event.notice.stringRes()))
            }
        }
    }

    ZomdroidTheme(themeMode = settingsState.themeMode.toAppThemeMode(), appearanceMode = state.appearanceMode) {
        val shell: @Composable () -> Unit = {
            AppScaffold(
                state = state,
                onModuleSelected = { module ->
                    viewModel.dispatch(AppAction.NavigateToModule(module))
                },
                onBack = { viewModel.dispatch(AppAction.Back) },
                content = { currentState ->
                    when (val destination = currentState.backStack.lastOrNull()) {
                        AppDestination.NewGameInstance -> NewGameInstanceScreen(
                            viewModel = newGameViewModel,
                            onBack = { viewModel.dispatch(AppAction.Back) },
                            onOpenSettings = { viewModel.dispatch(AppAction.OpenDestination(AppDestination.GameSettings)) },
                            onCreated = {
                                launcherViewModel.refresh()
                                viewModel.dispatch(AppAction.Back)
                            },
                        )
                        AppDestination.GameSettings -> GameSettingsScreen(
                            viewModel = gameSettingsViewModel,
                            onBack = { viewModel.dispatch(AppAction.Back) },
                        )
                        is AppDestination.ModuleHome -> if (destination.module == AppModule.Launcher) {
                            LauncherScreen(
                                state = launcherViewModel.uiState.collectAsStateWithLifecycle().value,
                                onAction = { launcherViewModel.dispatch(it) },
                            )
                        } else if (destination.module == AppModule.Settings) {
                            SettingsScreen(
                                viewModel = settingsViewModel,
                                onAppearanceChanged = { viewModel.dispatch(AppAction.SetAppearanceMode(it)) },
                                onOpenTool = { viewModel.dispatch(AppAction.OpenDestination(AppDestination.SettingsToolPage(it))) },
                            )
                        } else if (destination.module == AppModule.Workshop) {
                            WorkshopScreen(workshopViewModel, onOpenDetail = { id -> viewModel.dispatch(AppAction.OpenDestination(AppDestination.WorkshopDetail(id))) }, onOpenAccount = { viewModel.dispatch(AppAction.OpenDestination(AppDestination.WorkshopAccount)) })
                        } else if (destination.module == AppModule.Downloads) {
                            LaunchedEffect(Unit) { downloadViewModel.ensureService() }
                            DownloadCenterScreen(downloadViewModel, onOpenTask = { id -> viewModel.dispatch(AppAction.OpenDestination(AppDestination.DownloadTaskDetail(id))) }, onOpenSteamDownload = { viewModel.dispatch(AppAction.OpenDestination(AppDestination.SteamDownload)) })
                        } else if (destination.module == AppModule.ModLibrary) {
                            ModLibraryScreen(libraryViewModel) { id -> viewModel.dispatch(AppAction.OpenDestination(AppDestination.ModDetail(id))) }
                        } else {
                            CompatModuleContent(destination.module, Modifier.fillMaxSize())
                        }
                        is AppDestination.SettingsToolPage -> when (destination.tool) {
                            SettingsTool.GamepadMapper -> GamepadMapperScreen(gamepadMapperViewModel, onOpenGamepadMapper) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.TouchControls -> TouchControlsScreen(settingsViewModel) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.ControlsEditor -> ControlsEditorLaunchScreen(toolTaskViewModel, onOpenControlsEditor) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.InstallControls -> InstallControlsScreen(toolTaskViewModel) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.InstallDriver -> InstallDriverScreen(toolTaskViewModel) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.InstallNativeLibs -> InstallNativeLibsScreen(toolTaskViewModel) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.InstallSaves -> InstallSavesScreen(toolTaskViewModel) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.InstallMod -> InstallModScreen(toolTaskViewModel) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.ModFixes -> ModFixesScreen(toolTaskViewModel) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.Optimization -> OptimizationScreen(toolTaskViewModel) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.ExportLog -> ExportLogScreen(toolTaskViewModel) { viewModel.dispatch(AppAction.Back) }
                            SettingsTool.Wiki -> WikiScreen { viewModel.dispatch(AppAction.Back) }
                        }
                        AppDestination.WorkshopAccount -> WorkshopAccountScreen(workshopViewModel) { viewModel.dispatch(AppAction.Back) }
                        AppDestination.SteamDownload -> SteamDownloadScreen(steamDownloadViewModel, { viewModel.dispatch(AppAction.Back) }, onRequestAllFilesAccess)
                        is AppDestination.WorkshopDetail -> {
                            LaunchedEffect(destination.workshopId) { workshopViewModel.openDetail(destination.workshopId) }
                            WorkshopDetailScreen(workshopViewModel, { viewModel.dispatch(AppAction.Back) }, onOpenExternalUrl)
                        }
                        is AppDestination.DownloadTaskDetail -> DownloadTaskDetailScreen(downloadViewModel, destination.taskId) { viewModel.dispatch(AppAction.Back) }
                        is AppDestination.ModDetail -> ModDetailScreen(libraryViewModel, destination.workshopId) { viewModel.dispatch(AppAction.Back) }
                        else -> CompatModuleContent(currentState.selectedModule, Modifier.fillMaxSize())
                    }
                },
                snackbarHostState = snackbarHostState,
            )
            GlobalDialogs(state.dialog, viewModel::dispatch)
        }
        if (state.appearanceMode == com.zomdroid.ui.model.AppearanceMode.Classic) {
            Surface(modifier = Modifier.fillMaxSize(), content = shell)
        } else {
            ZomdroidBackdropScaffold(content = { shell() })
        }
    }
}

private fun LegacyDestination.toModule(): AppModule = when (this) {
    LegacyDestination.Settings -> AppModule.Settings
    LegacyDestination.Workshop -> AppModule.Workshop
    LegacyDestination.Downloads -> AppModule.Downloads
    LegacyDestination.ModLibrary -> AppModule.ModLibrary
}

private fun com.zomdroid.LauncherPreferences.ThemeMode.toAppThemeMode(): AppThemeMode = when (this) {
    com.zomdroid.LauncherPreferences.ThemeMode.LIGHT -> AppThemeMode.Light
    com.zomdroid.LauncherPreferences.ThemeMode.DARK -> AppThemeMode.Dark
    com.zomdroid.LauncherPreferences.ThemeMode.SYSTEM -> AppThemeMode.FollowSystem
}

fun installZomdroidApp(view: ComposeView, viewModel: AppViewModel, themeMode: AppThemeMode, callbacks: ZomdroidHostCallbacks) {
    view.setContent {
        ZomdroidApp(
            viewModel = viewModel,
            themeMode = themeMode,
            onRequestPermission = callbacks::onRequestPermission,
            onLaunchGame = callbacks::onLaunchGame,
            onOpenStorage = callbacks::onOpenStorage,
            onOpenGamepadMapper = callbacks::onOpenGamepadMapper,
            onOpenControlsEditor = callbacks::onOpenControlsEditor,
            onOpenExternalUrl = callbacks::onOpenExternalUrl,
            onRequestAllFilesAccess = callbacks::onRequestAllFilesAccess,
        )
    }
}

private fun LauncherNotice.stringRes(): Int = when (this) {
    LauncherNotice.InstallationNotFinished -> com.zomdroid.R.string.installation_not_finished
    LauncherNotice.GameFilesMissing -> com.zomdroid.R.string.game_files_missing
    LauncherNotice.GameFilesNotForLinux -> com.zomdroid.R.string.game_files_not_for_linux
    LauncherNotice.DependenciesNotInstalled -> com.zomdroid.R.string.dependencies_not_installed
    LauncherNotice.BackupRestoreFailed -> com.zomdroid.R.string.backup_restore_failed_generic
    LauncherNotice.BackupRestored -> com.zomdroid.R.string.backup_restore_done
    LauncherNotice.BackupNotFound -> com.zomdroid.R.string.backup_none_found
}
