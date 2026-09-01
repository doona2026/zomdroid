package com.zomdroid.ui.tools

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.zomdroid.ui.component.ZomdroidErrorState

@Composable
fun ToolTaskScreen(spec: ToolSpec, viewModel: ToolTaskViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.selectFile(it, displayName(it)) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) }
            Text(stringResource(spec.titleRes), Modifier.weight(1f).padding(top = 12.dp), style = MaterialTheme.typography.titleLarge)
        }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                spec.descriptionRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (spec.needsInstance) InstancePicker(state.instances.map { it.name }, state.selectedInstance, viewModel::selectInstance)
                Text(state.fileLabel ?: stringResource(R.string.game_instance_no_file_selected), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picker.launch(spec.archiveMime) }) { Text(stringResource(R.string.game_instance_browse_files_hint)) }
                    Button(onClick = { viewModel.start(spec) }, enabled = !state.running) { Text(stringResource(R.string.optimization_install)) }
                    if (state.running) OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.dialog_button_cancel)) }
                }
                when (state.error) {
                    ToolError.MissingInstance -> ZomdroidErrorState(stringResource(R.string.select_instance))
                    ToolError.MissingFile -> ZomdroidErrorState(stringResource(R.string.game_instance_no_file_selected))
                    ToolError.FailedToStart -> ZomdroidErrorState(stringResource(R.string.dialog_title_failed_to_create_instance))
                    null -> Unit
                }
                state.task?.let { task ->
                    task.message?.takeIf { it.isNotBlank() }?.let { Text(it, color = if (task.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (!task.finished) {
                        if (task.progressMax > 0) LinearProgressIndicator({ task.progress.toFloat() / task.progressMax }, Modifier.fillMaxWidth())
                        else LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
            }
        }
    }
}

@Composable
fun InstancePicker(options: List<String>, selected: String?, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        OutlinedButton(onClick = { expanded = true }, enabled = options.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text(selected ?: stringResource(R.string.select_instance)) }
        DropdownMenu(expanded, { expanded = false }) { options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelected(option) }) } }
    }
}

@Composable
fun ToolTaskCard(spec: ToolSpec, viewModel: ToolTaskViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { viewModel.selectFile(it, displayName(it)) } }
    ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(spec.titleRes), style = MaterialTheme.typography.titleMedium)
        spec.descriptionRes?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (spec.needsInstance) InstancePicker(state.instances.map { it.name }, state.selectedInstance, viewModel::selectInstance)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picker.launch(spec.archiveMime) }) { Text(stringResource(R.string.game_instance_browse_files_hint)) }
            Button(onClick = { viewModel.start(spec) }, enabled = !state.running) { Text(stringResource(R.string.optimization_install)) }
            if (state.running) OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.dialog_button_cancel)) }
        }
        state.task?.let { task ->
            task.message?.takeIf { it.isNotBlank() }?.let { Text(it, color = if (task.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!task.finished) {
                if (task.progressMax > 0) LinearProgressIndicator({ task.progress.toFloat() / task.progressMax }, Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    } }
}

private fun displayName(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()
