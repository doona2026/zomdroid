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
import androidx.compose.material3.MaterialTheme
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.zomdroid.ui.viewmodel.GameSettingsError
import com.zomdroid.ui.viewmodel.GameSettingsViewModel

@Composable
fun GameSettingsScreen(viewModel: GameSettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { viewModel.selectImport(it, displayName(it)) } }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri -> uri?.let(viewModel::export) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) }
            Text(stringResource(R.string.game_settings_menu), Modifier.weight(1f).padding(top = 12.dp))
        }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.install_mod_instance_label))
                InstancePicker(state.instances.map { it.name }, state.selectedInstance, viewModel::selectInstance)
                if (state.instances.isEmpty()) Text(stringResource(R.string.install_mod_no_instances), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.game_settings_import_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.game_settings_import_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(state.importLabel ?: stringResource(R.string.game_instance_no_file_selected))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { importPicker.launch(arrayOf("*/*")) }) { Text(stringResource(R.string.game_instance_browse_files_hint)) }
                    Button(onClick = viewModel::importSettings) { Text(stringResource(R.string.game_settings_import_title)) }
                }
            }
        }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.game_settings_export_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.game_settings_export_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { exportPicker.launch("options.ini") }) { Text(stringResource(R.string.game_settings_export_title)) }
            }
        }
        when (state.error) {
            GameSettingsError.NoInstance -> Text(stringResource(R.string.select_instance), color = MaterialTheme.colorScheme.error)
            GameSettingsError.WrongFormat -> Text(stringResource(R.string.game_settings_wrong_format), color = MaterialTheme.colorScheme.error)
            GameSettingsError.NoImportFile -> Text(stringResource(R.string.game_instance_no_file_selected), color = MaterialTheme.colorScheme.error)
            GameSettingsError.TaskFailed -> Text(stringResource(R.string.dialog_title_failed_to_create_instance), color = MaterialTheme.colorScheme.error)
            null -> Unit
        }
        if (state.taskRunning) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}

@Composable
private fun InstancePicker(options: List<String>, selected: String?, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        ZomdroidLiquidOutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
            Text(selected ?: stringResource(R.string.select_instance))
        }
        ZomdroidPopupMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                ZomdroidPopupMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelected(option) })
            }
        }
    }
}

private fun displayName(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
