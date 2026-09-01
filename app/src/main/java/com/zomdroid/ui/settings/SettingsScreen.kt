package com.zomdroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.zomdroid.ui.component.ZomdroidLiquidAlertDialog as AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.zomdroid.ui.component.ZomdroidLiquidTextButton as TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zomdroid.LauncherPreferences
import com.zomdroid.R
import com.zomdroid.game.SuggestedPreset
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.component.ZomdroidLiquidButton
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedButton
import com.zomdroid.ui.component.ZomdroidLiquidSlider
import com.zomdroid.ui.component.ZomdroidLiquidTextField
import com.zomdroid.ui.component.ZomdroidLiquidToggle
import com.zomdroid.ui.component.ZomdroidPopupMenu
import com.zomdroid.ui.component.ZomdroidPopupMenuItem
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
                ZomdroidLiquidSlider(value = state.renderScale, onValueChange = viewModel::setRenderScale, valueRange = .25f..1f)
                Text(stringResource(R.string.percentage_format, (state.renderScale * 100).toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        PresetCard(state, viewModel)
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ZomdroidSectionLabel(stringResource(R.string.settings_group_advanced))
                ZomdroidLiquidTextField(state.jvmArgs, viewModel::setJvmArgs, stringResource(R.string.settings_jvm_args), Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ZomdroidLiquidOutlinedButton({ viewModel.setJvmArgs(LauncherPreferences.DEFAULT_JVM_ARGS) }) { Text(stringResource(R.string.settings_jvm_args_reset)) }
                    ZomdroidLiquidOutlinedButton({ viewModel.setJvmArgs("") }) { Text(stringResource(R.string.settings_jvm_args_clear)) }
                    ZomdroidLiquidOutlinedButton({ viewModel.setJvmArgs(LauncherPreferences.BUILD_42_JVM_ARGS) }) { Text(stringResource(R.string.settings_jvm_args_apply)) }
                }
                ZomdroidLiquidTextField(state.envVars, viewModel::setEnvVars, stringResource(R.string.settings_env_vars), Modifier.fillMaxWidth())
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
                ZomdroidLiquidButton(onClick = { onSelected(mode) }, modifier = Modifier.weight(1f), filled = true) { Text(mode.label()) }
            } else {
                ZomdroidLiquidOutlinedButton(onClick = { onSelected(mode) }, modifier = Modifier.weight(1f)) { Text(mode.label()) }
            }
        }
    }
}

@Composable private fun PresetCard(state: SettingsUiState, viewModel: SettingsViewModel) {
    ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ZomdroidSectionLabel(stringResource(R.string.preset_card_title))
        Text(stringResource(R.string.preset_card_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SuggestedPreset.entries.forEach { preset -> ZomdroidLiquidOutlinedButton({ viewModel.requestPreset(preset) }, Modifier.fillMaxWidth()) { Text(stringResource(preset.labelRes())) } }
        Text(stringResource(R.string.preset_current_none))
    } }
}

@Composable private fun ChoiceRow(label: String, value: String, menu: @Composable () -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); menu() }; Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
@Composable private fun <T> EnumMenu(options: List<T>, selected: T, onSelected: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        ZomdroidLiquidOutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.widthIn(min = 128.dp, max = 260.dp),
        ) {
            Text(
                selected?.toString() ?: stringResource(R.string.settings_value_off),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ZomdroidPopupMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.widthIn(min = 220.dp, max = 320.dp),
        ) {
            options.forEach { option ->
                ZomdroidPopupMenuItem(
                    text = { Text(option?.toString() ?: stringResource(R.string.settings_value_off)) },
                    onClick = { expanded = false; onSelected(option) },
                )
            }
        }
    }
}
@Composable private fun SettingSwitch(label: String, checked: Boolean, onChanged: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label); ZomdroidLiquidToggle(checked, onChanged) } }
@Composable private fun ToolLink(label: String, onClick: () -> Unit) { ZomdroidLiquidOutlinedButton(onClick, Modifier.fillMaxWidth()) { Text(label) } }
@Composable private fun AppearanceMode.label() = stringResource(when (this) { AppearanceMode.LiquidGlass -> R.string.frontend_mode_liquid; AppearanceMode.LiteLiquidGlass -> R.string.frontend_mode_lite; AppearanceMode.Classic -> R.string.frontend_mode_classic })
@Composable private fun LauncherPreferences.ThemeMode.label() = stringResource(when (this) { LauncherPreferences.ThemeMode.SYSTEM -> R.string.settings_theme_system; LauncherPreferences.ThemeMode.LIGHT -> R.string.settings_theme_light; LauncherPreferences.ThemeMode.DARK -> R.string.settings_theme_dark })
@Composable private fun String?.label() = this ?: stringResource(R.string.settings_value_off)
@Composable private fun shrinkLabel(value: String?) = value.label()
private fun SuggestedPreset.labelRes() = when (this) { SuggestedPreset.BUILD_42_QUALITY -> R.string.preset_name_b42; SuggestedPreset.BUILD_42_COMPATIBILITY -> R.string.preset_name_b42_compat; SuggestedPreset.BUILD_41 -> R.string.preset_name_b41 }
