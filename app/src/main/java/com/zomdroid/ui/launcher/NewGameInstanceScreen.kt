package com.zomdroid.ui.launcher

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.zomdroid.ui.component.ZomdroidLiquidButton as Button
import com.zomdroid.ui.component.ZomdroidLinearProgressIndicator as LinearProgressIndicator
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedButton as OutlinedButton
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedButton
import com.zomdroid.ui.component.ZomdroidPopupMenu
import com.zomdroid.ui.component.ZomdroidPopupMenuItem
import com.zomdroid.ui.viewmodel.NewGameInstanceError
import com.zomdroid.ui.viewmodel.NewGameInstanceNameError
import com.zomdroid.ui.viewmodel.NewGameInstanceViewModel

@Composable
fun NewGameInstanceScreen(
    viewModel: NewGameInstanceViewModel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onCreated: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.created) { if (state.created) onCreated() }
    val gamePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.selectGameFiles(it, displayName(it)) } }
    val nativePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.selectNativeLibs(it, displayName(it)) } }
    val savesPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.selectSaves(it, displayName(it)) } }
    val modsPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.selectMods(it, displayName(it)) } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) }
            Text(stringResource(R.string.fragment_label_new_game_instance), Modifier.weight(1f).padding(top = 12.dp))
        }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.new_game_instance_group_name))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::setName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.game_instance_name)) },
                    isError = state.nameError != null,
                    supportingText = {
                        when (state.nameError) {
                            NewGameInstanceNameError.Invalid -> Text(stringResource(R.string.game_instance_name_invalid))
                            NewGameInstanceNameError.AlreadyExists -> Text(stringResource(R.string.game_instance_name_already_exists))
                            null -> Unit
                        }
                    },
                )
                Text(stringResource(R.string.new_game_instance_group_preset))
                PresetPicker(state.presets, state.selectedPreset, viewModel::selectPreset)
            }
        }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.new_game_instance_group_files))
                FilePickerRow(stringResource(R.string.game_instance_files), state.gameFilesLabel, { gamePicker.launch("application/zip") })
                FilePickerRow(stringResource(R.string.game_instance_native_libs_label), state.nativeLibsLabel, { nativePicker.launch("application/zip") })
                FilePickerRow(stringResource(R.string.game_instance_saves_label), state.savesLabel, { savesPicker.launch("application/zip") })
                FilePickerRow(stringResource(R.string.game_instance_mods_label), state.modsLabel, { modsPicker.launch("application/zip") })
                Text(stringResource(R.string.new_game_instance_b42_jvm_hint), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.new_game_instance_open_settings)) }
            }
        }
        when (state.error) {
            NewGameInstanceError.MissingGameFiles -> Text(stringResource(R.string.game_instance_no_file_selected), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            NewGameInstanceError.MissingPreset -> Text(stringResource(R.string.new_game_instance_no_preset_selected), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            NewGameInstanceError.CreationFailed -> Text(stringResource(R.string.dialog_title_failed_to_create_instance), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            null -> Unit
        }
        Button(onClick = viewModel::create, enabled = !state.isCreating, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.new_game_instance_create))
        }
        if (state.isCreating) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun PresetPicker(options: List<String>, selected: String?, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        ZomdroidLiquidOutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), enabled = options.isNotEmpty()) {
            Text(selected ?: stringResource(R.string.new_game_instance_select_preset))
        }
        ZomdroidPopupMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                ZomdroidPopupMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelected(option) })
            }
        }
    }
}

@Composable
private fun FilePickerRow(label: String, selected: String?, onPick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label)
            Text(selected ?: stringResource(R.string.game_instance_no_file_selected), color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onPick) { Text(stringResource(R.string.game_instance_browse_files_hint)) }
    }
}

private fun displayName(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
