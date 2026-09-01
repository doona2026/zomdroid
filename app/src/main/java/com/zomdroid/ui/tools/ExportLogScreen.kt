package com.zomdroid.ui.tools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.zomdroid.R

@Composable fun ExportLogScreen(viewModel: ToolTaskViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri -> uri?.let(viewModel::exportLog) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) }
        Text(stringResource(R.string.export_log_menu), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        InstancePicker(state.instances.map { it.name }, state.selectedInstance, viewModel::selectInstance)
        Text(stringResource(R.string.export_log_hint))
        Button(onClick = { picker.launch("zomdroid-log.zip") }, enabled = state.selectedInstance != null && !state.running, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_log_menu)) }
        if (state.running) OutlinedButton(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.dialog_button_cancel)) }
        state.error?.let { Text(stringResource(R.string.dialog_title_failed_to_create_instance), color = MaterialTheme.colorScheme.error) }
        state.task?.let { task ->
            task.message?.takeIf { it.isNotBlank() }?.let { Text(it, color = if (task.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!task.finished) {
                if (task.progressMax > 0) LinearProgressIndicator({ task.progress.toFloat() / task.progressMax }, Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
