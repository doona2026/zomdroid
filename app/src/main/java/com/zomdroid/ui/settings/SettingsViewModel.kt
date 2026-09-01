package com.zomdroid.ui.settings

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.zomdroid.LauncherPreferences
import com.zomdroid.game.SuggestedPreset
import com.zomdroid.input.GamepadManager
import com.zomdroid.ui.model.AppearanceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val appearanceMode: AppearanceMode = AppearanceMode.default,
    val themeMode: LauncherPreferences.ThemeMode = LauncherPreferences.ThemeMode.SYSTEM,
    val renderer: LauncherPreferences.Renderer = LauncherPreferences.Renderer.NG_GL4ES,
    val vulkanDriver: LauncherPreferences.VulkanDriver = LauncherPreferences.VulkanDriver.SYSTEM_DEFAULT,
    val renderScale: Float = .60f,
    val jvmArgs: String = LauncherPreferences.DEFAULT_JVM_ARGS,
    val envVars: String = "",
    val textureShrink: String? = null,
    val memorySaver: Boolean = false,
    val quickSaveBackup: Boolean = false,
    val debug: Boolean = false,
    val touchControls: Boolean = false,
    val vibrateOnTouch: Boolean = false,
    val pendingPreset: SuggestedPreset? = null,
    val pendingPresetChanges: List<String> = emptyList(),
)

object SettingsPresentationRules {
    fun selectAppearance(state: SettingsUiState, mode: AppearanceMode) = state.copy(appearanceMode = mode)
    fun cancelPreset(state: SettingsUiState) = state.copy(pendingPreset = null, pendingPresetChanges = emptyList())
    fun finishPreset(state: SettingsUiState) = state.copy(pendingPreset = null, pendingPresetChanges = emptyList())
}

interface SettingsDataSource {
    fun read(): SettingsUiState
    fun setAppearance(mode: AppearanceMode)
    fun setTheme(mode: LauncherPreferences.ThemeMode)
    fun setRenderer(renderer: LauncherPreferences.Renderer)
    fun setVulkanDriver(driver: LauncherPreferences.VulkanDriver)
    fun setRenderScale(scale: Float)
    fun setJvmArgs(args: String)
    fun setEnvVars(value: String)
    fun setTextureShrink(value: String?)
    fun setMemorySaver(value: Boolean)
    fun setQuickSaveBackup(value: Boolean)
    fun setDebug(value: Boolean)
    fun setTouchControls(value: Boolean)
    fun setVibrateOnTouch(value: Boolean)
    fun describePreset(preset: SuggestedPreset): List<String>
    fun applyPreset(preset: SuggestedPreset)
}

class SettingsRepository(
    private val appContext: Context,
    private val preferences: LauncherPreferences = LauncherPreferences.requireSingleton(),
) : SettingsDataSource {
    private val appearanceRepository = UiSettingsRepository(LauncherAppearanceModeStore(preferences))

    override fun read(): SettingsUiState = SettingsUiState(
        appearanceMode = appearanceRepository.getAppearanceMode(),
        themeMode = preferences.themeMode,
        renderer = preferences.renderer,
        vulkanDriver = preferences.vulkanDriver,
        renderScale = preferences.renderScale,
        jvmArgs = preferences.jvmArgs,
        envVars = preferences.envVars,
        textureShrink = SuggestedPreset.readShrink(preferences.envVars),
        memorySaver = preferences.isMemorySaver,
        quickSaveBackup = preferences.isQuickSaveBackup,
        debug = preferences.isDebug,
        touchControls = preferences.isTouchControlsEnabled,
        vibrateOnTouch = preferences.isVibrateOnTouch,
    )

    override fun setAppearance(mode: AppearanceMode) { appearanceRepository.setAppearanceMode(mode) }
    override fun setTheme(mode: LauncherPreferences.ThemeMode) {
        preferences.setThemeMode(mode)
        AppCompatDelegate.setDefaultNightMode(mode.nightMode)
    }
    override fun setRenderer(renderer: LauncherPreferences.Renderer) { preferences.setRenderer(renderer) }
    override fun setVulkanDriver(driver: LauncherPreferences.VulkanDriver) { preferences.setVulkanDriver(driver) }
    override fun setRenderScale(scale: Float) { preferences.setRenderScale(scale.coerceIn(.25f, 1f)) }
    override fun setJvmArgs(args: String) { preferences.setJvmArgs(args.trim()) }
    override fun setEnvVars(value: String) { preferences.setEnvVars(value.trim()) }
    override fun setTextureShrink(value: String?) { preferences.setEnvVars(SuggestedPreset.withShrink(preferences.envVars, value)) }
    override fun setMemorySaver(value: Boolean) { preferences.setMemorySaver(value) }
    override fun setQuickSaveBackup(value: Boolean) { preferences.setQuickSaveBackup(value) }
    override fun setDebug(value: Boolean) { preferences.setDebug(value) }
    override fun setTouchControls(value: Boolean) {
        preferences.setTouchControlsEnabled(value)
        GamepadManager.setTouchOverride(value)
    }
    override fun setVibrateOnTouch(value: Boolean) { preferences.setVibrateOnTouch(value) }
    override fun describePreset(preset: SuggestedPreset): List<String> = preset.describeChanges(appContext)
    override fun applyPreset(preset: SuggestedPreset) { preset.apply(appContext) }
}

class SettingsViewModel(private val repository: SettingsDataSource) : ViewModel() {
    private val _uiState = MutableStateFlow(repository.read())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun refresh() { _uiState.value = repository.read() }
    fun setAppearance(mode: AppearanceMode) { repository.setAppearance(mode); refresh() }
    fun setTheme(mode: LauncherPreferences.ThemeMode) { repository.setTheme(mode); refresh() }
    fun setRenderer(renderer: LauncherPreferences.Renderer) { repository.setRenderer(renderer); refresh() }
    fun setVulkanDriver(driver: LauncherPreferences.VulkanDriver) { repository.setVulkanDriver(driver); refresh() }
    fun setRenderScale(scale: Float) { repository.setRenderScale(scale); _uiState.value = _uiState.value.copy(renderScale = scale.coerceIn(.25f, 1f)) }
    fun setJvmArgs(args: String) { repository.setJvmArgs(args); _uiState.value = _uiState.value.copy(jvmArgs = args) }
    fun setEnvVars(value: String) { repository.setEnvVars(value); refresh() }
    fun setTextureShrink(value: String?) { repository.setTextureShrink(value); refresh() }
    fun setMemorySaver(value: Boolean) { repository.setMemorySaver(value); _uiState.value = _uiState.value.copy(memorySaver = value) }
    fun setQuickSaveBackup(value: Boolean) { repository.setQuickSaveBackup(value); _uiState.value = _uiState.value.copy(quickSaveBackup = value) }
    fun setDebug(value: Boolean) { repository.setDebug(value); _uiState.value = _uiState.value.copy(debug = value) }
    fun setTouchControls(value: Boolean) { repository.setTouchControls(value); _uiState.value = _uiState.value.copy(touchControls = value) }
    fun setVibrateOnTouch(value: Boolean) { repository.setVibrateOnTouch(value); _uiState.value = _uiState.value.copy(vibrateOnTouch = value) }

    fun requestPreset(preset: SuggestedPreset) {
        _uiState.value = _uiState.value.copy(pendingPreset = preset, pendingPresetChanges = repository.describePreset(preset))
    }
    fun cancelPreset() { _uiState.value = SettingsPresentationRules.cancelPreset(_uiState.value) }
    fun confirmPreset() {
        val preset = _uiState.value.pendingPreset ?: return
        viewModelScope.launch {
            repository.applyPreset(preset)
            _uiState.value = SettingsPresentationRules.finishPreset(repository.read())
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SettingsViewModel(SettingsRepository(context.applicationContext)) as T
        }
    }
}
