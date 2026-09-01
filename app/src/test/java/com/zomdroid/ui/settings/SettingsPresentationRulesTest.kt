package com.zomdroid.ui.settings

import com.google.common.truth.Truth.assertThat
import com.zomdroid.LauncherPreferences
import com.zomdroid.game.SuggestedPreset
import com.zomdroid.ui.model.AppearanceMode
import org.junit.Test

class SettingsPresentationRulesTest {
    @Test fun appearanceSelectionIsImmediateAndDoesNotChangeOtherSettings() {
        val initial = SettingsUiState(jvmArgs = "-Xmx1G", memorySaver = true)
        val changed = SettingsPresentationRules.selectAppearance(initial, AppearanceMode.Classic)
        assertThat(changed.appearanceMode).isEqualTo(AppearanceMode.Classic)
        assertThat(changed.jvmArgs).isEqualTo("-Xmx1G")
        assertThat(changed.memorySaver).isTrue()
    }

    @Test fun cancelPresetRemovesPendingConfirmation() {
        val state = SettingsUiState(pendingPreset = com.zomdroid.game.SuggestedPreset.BUILD_41, pendingPresetChanges = listOf("renderer"))
        val cancelled = SettingsPresentationRules.cancelPreset(state)
        assertThat(cancelled.pendingPreset).isNull()
        assertThat(cancelled.pendingPresetChanges).isEmpty()
    }

    @Test fun finishPresetAlsoClearsConfirmationState() {
        val state = SettingsUiState(pendingPreset = com.zomdroid.game.SuggestedPreset.BUILD_42_QUALITY, pendingPresetChanges = listOf("jvm"))
        assertThat(SettingsPresentationRules.finishPreset(state).pendingPreset).isNull()
    }
    @Test fun viewModelReadsExistingStateAndUpdatesAppearanceImmediately() {
        val source = FakeSettingsDataSource(SettingsUiState(appearanceMode = AppearanceMode.Classic))
        val viewModel = SettingsViewModel(source)
        assertThat(viewModel.uiState.value.appearanceMode).isEqualTo(AppearanceMode.Classic)
        viewModel.setAppearance(AppearanceMode.LiteLiquidGlass)
        assertThat(viewModel.uiState.value.appearanceMode).isEqualTo(AppearanceMode.LiteLiquidGlass)
    }

    private class FakeSettingsDataSource(private var state: SettingsUiState) : SettingsDataSource {
        override fun read() = state
        override fun setAppearance(mode: AppearanceMode) { state = state.copy(appearanceMode = mode) }
        override fun setTheme(mode: LauncherPreferences.ThemeMode) {}
        override fun setRenderer(renderer: LauncherPreferences.Renderer) {}
        override fun setVulkanDriver(driver: LauncherPreferences.VulkanDriver) {}
        override fun setRenderScale(scale: Float) {}
        override fun setJvmArgs(args: String) {}
        override fun setEnvVars(value: String) {}
        override fun setTextureShrink(value: String?) {}
        override fun setMemorySaver(value: Boolean) {}
        override fun setQuickSaveBackup(value: Boolean) {}
        override fun setDebug(value: Boolean) {}
        override fun setTouchControls(value: Boolean) {}
        override fun setVibrateOnTouch(value: Boolean) {}
        override fun describePreset(preset: SuggestedPreset) = listOf("preview")
        override fun applyPreset(preset: SuggestedPreset) {}
    }
}
