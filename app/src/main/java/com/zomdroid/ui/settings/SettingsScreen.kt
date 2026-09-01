package com.zomdroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zomdroid.LauncherPreferences
import com.zomdroid.R
import com.zomdroid.game.SuggestedPreset
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.component.ZomdroidSectionLabel
import com.zomdroid.ui.model.AppearanceMode

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onAppearanceChanged: (AppearanceMode) -> Unit, onOpenTool: (SettingsTool) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(stringResource(R.string.app_module_settings), style = MaterialTheme.typography.headlineSmall)
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZomdroidSectionLabel(stringResource(R.string.settings_group_appearance))
                Text(stringResource(R.string.frontend_appearance_label))
                AppearanceModePicker(state.appearanceMode) { mode -> viewModel.setAppearance(mode); onAppearanceChanged(mode) }
                ChoiceRow(stringResource(R.string.settings_theme), state.themeMode.label()) { EnumMenu(LauncherPreferences.ThemeMode.entries.toList(), state.themeMode, viewModel::setTheme) }
            }
        }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZomdroidSectionLabel(stringResource(R.string.settings_group_rendering))
                ChoiceRow(stringResource(R.string.settings_renderer), state.renderer.name) { EnumMenu(LauncherPreferences.Renderer.entries.toList(), state.renderer, viewModel::setRenderer) }
                if (state.renderer.name.startsWith("ZINK")) ChoiceRow(stringResource(R.string.settings_vulkan_driver), state.vulkanDriver.name) { EnumMenu(LauncherPreferences.VulkanDriver.entries.toList(), state.vulkanDriver, viewModel::setVulkanDriver) }
                Text(stringResource(R.string.settings_resolution_scale))
                Slider(value = state.renderScale, onValueChange = viewModel::setRenderScale, valueRange = .25f..1f)
                Text(stringResource(R.string.percentage_format, (state.renderScale * 100).toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PresetCard(state, viewModel)
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZomdroidSectionLabel(stringResource(R.string.settings_group_advanced))
                OutlinedTextField(state.jvmArgs, viewModel::setJvmArgs, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_jvm_args)) })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton({ viewModel.setJvmArgs(LauncherPreferences.DEFAULT_JVM_ARGS) }) { Text(stringResource(R.string.settings_jvm_args_reset)) }
                    OutlinedButton({ viewModel.setJvmArgs("") }) { Text(stringResource(R.string.settings_jvm_args_clear)) }
                    OutlinedButton({ viewModel.setJvmArgs(LauncherPreferences.BUILD_42_JVM_ARGS) }) { Text(stringResource(R.string.settings_jvm_args_apply)) }
                }
                OutlinedTextField(state.envVars, viewModel::setEnvVars, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.settings_env_vars)) })
                ChoiceRow(stringResource(R.string.settings_texture_shrink_title), shrinkLabel(state.textureShrink)) { EnumMenu(listOf(null, SuggestedPreset.SHRINK_BALANCED, "1"), state.textureShrink, viewModel::setTextureShrink) }
                SettingSwitch(stringResource(R.string.settings_memory_saver), state.memorySaver, viewModel::setMemorySaver)
                SettingSwitch(stringResource(R.string.settings_debug_mode), state.debug, viewModel::setDebug)
            }
        }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZomdroidSectionLabel(stringResource(R.string.settings_group_controls))
                SettingSwitch(stringResource(R.string.touch_controls_switch_label), state.touchControls, viewModel::setTouchControls)
                SettingSwitch(stringResource(R.string.vibrate_on_touch_switch_label), state.vibrateOnTouch, viewModel::setVibrateOnTouch)
                HorizontalDivider()
                ToolLink(stringResource(R.string.nav_menu_gamepad_mapper)) { onOpenTool(SettingsTool.GamepadMapper) }
                ToolLink(stringResource(R.string.fragment_label_touch_controls)) { onOpenTool(SettingsTool.TouchControls) }
                ToolLink(stringResource(R.string.nav_menu_controls_editor)) { onOpenTool(SettingsTool.ControlsEditor) }
            }
        }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ZomdroidSectionLabel(stringResource(R.string.tools_section_title))
                listOf(
                    R.string.menu_install_controls to SettingsTool.InstallControls,
                    R.string.install_driver_menu to SettingsTool.InstallDriver,
                    R.string.native_libs_menu to SettingsTool.InstallNativeLibs,
                    R.string.install_saves_menu to SettingsTool.InstallSaves,
                    R.string.nav_menu_install_mod to SettingsTool.InstallMod,
                    R.string.nav_menu_mod_fixes to SettingsTool.ModFixes,
                    R.string.nav_menu_optimization to SettingsTool.Optimization,
                    R.string.export_log_menu to SettingsTool.ExportLog,
                    R.string.fragment_label_wiki to SettingsTool.Wiki,
                ).forEach { (label, tool) -> ToolLink(stringResource(label)) { onOpenTool(tool) } }
            }
        }
        SettingSwitch(stringResource(R.string.settings_backup_switch), state.quickSaveBackup, viewModel::setQuickSaveBackup)
    }
    state.pendingPreset?.let { preset ->
        AlertDialog(
            onDismissRequest = viewModel::cancelPreset,
            title = { Text(stringResource(preset.labelRes())) },
            text = { Text(state.pendingPresetChanges.joinToString("\n") { "• $it" }) },
            confirmButton = { TextButton(viewModel::confirmPreset) { Text(stringResource(R.string.dialog_button_confirm)) } },
            dismissButton = { TextButton(viewModel::cancelPreset) { Text(stringResource(R.string.dialog_button_cancel)) } },
        )
    }
}

enum class SettingsTool { GamepadMapper, TouchControls, ControlsEditor, InstallControls, InstallDriver, InstallNativeLibs, InstallSaves, InstallMod, ModFixes, Optimization, ExportLog, Wiki }

@Composable
fun AppearanceModePicker(selected: AppearanceMode, onSelected: (AppearanceMode) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AppearanceMode.entries.forEach { mode ->
            if (selected == mode) {
                Button(onClick = { onSelected(mode) }, modifier = Modifier.weight(1f)) { Text(mode.label()) }
            } else {
                OutlinedButton(onClick = { onSelected(mode) }, modifier = Modifier.weight(1f)) { Text(mode.label()) }
            }
        }
    }
}

@Composable private fun PresetCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ZomdroidSectionLabel(stringResource(R.string.preset_card_title))
        Text(stringResource(R.string.preset_card_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SuggestedPreset.entries.forEach { preset -> OutlinedButton({ viewModel.requestPreset(preset) }, Modifier.fillMaxWidth()) { Text(stringResource(preset.labelRes())) } }
        Text(stringResource(R.string.preset_current_none))
    } }
}

@Composable private fun ChoiceRow(label: String, value: String, menu: @Composable () -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); menu() }; Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
@Composable private fun <T> EnumMenu(options: List<T>, selected: T, onSelected: (T) -> Unit) { var expanded by remember { mutableStateOf(false) }; OutlinedButton({ expanded = true }) { Text(selected?.toString() ?: stringResource(R.string.settings_value_off)) }; DropdownMenu(expanded, { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(option?.toString() ?: stringResource(R.string.settings_value_off)) }, onClick = { expanded = false; onSelected(option) }) } } }
@Composable private fun SettingSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); Switch(checked, onChanged) } }
@Composable private fun ToolLink(label: String, onClick: () -> Unit) { OutlinedButton(onClick, Modifier.fillMaxWidth()) { Text(label) } }
@Composable private fun AppearanceMode.label() = stringResource(when (this) { AppearanceMode.LiquidGlass -> R.string.frontend_mode_liquid; AppearanceMode.LiteLiquidGlass -> R.string.frontend_mode_lite; AppearanceMode.Classic -> R.string.frontend_mode_classic })
@Composable private fun LauncherPreferences.ThemeMode.label() = stringResource(when (this) { LauncherPreferences.ThemeMode.SYSTEM -> R.string.settings_theme_system; LauncherPreferences.ThemeMode.LIGHT -> R.string.settings_theme_light; LauncherPreferences.ThemeMode.DARK -> R.string.settings_theme_dark })
@Composable private fun String?.label() = this ?: stringResource(R.string.settings_value_off)
@Composable private fun shrinkLabel(value: String?) = value.label()
private fun SuggestedPreset.labelRes() = when (this) { SuggestedPreset.BUILD_42_QUALITY -> R.string.preset_name_b42; SuggestedPreset.BUILD_42_COMPATIBILITY -> R.string.preset_name_b42_compat; SuggestedPreset.BUILD_41 -> R.string.preset_name_b41 }
