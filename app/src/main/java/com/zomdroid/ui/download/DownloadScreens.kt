package com.zomdroid.ui.download

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.component.ZomdroidSectionLabel
import com.zomdroid.workshop.download.DownloadCenterTask
import com.zomdroid.workshop.download.DownloadCenterTaskState

@Composable
fun DownloadCenterScreen(viewModel: DownloadViewModel, onOpenTask: (String) -> Unit, onOpenSteamDownload: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycleCompat()
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { ZomdroidSectionLabel(stringResource(R.string.workshop_download_center_title)); OutlinedButton(onClick = onOpenSteamDownload) { IconDownload(); Text(stringResource(R.string.steam_dl_menu)) } } }
        item { Text(stringResource(R.string.workshop_download_center_note), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (state.tasks.isEmpty()) item { Text(stringResource(R.string.workshop_download_center_empty)) }
        items(state.tasks, key = { it.id }) { task -> DownloadTaskCard(task, { onOpenTask(task.id) }, viewModel) }
    }
}

@Composable private fun DownloadTaskCard(task: DownloadCenterTask, onOpen: () -> Unit, viewModel: DownloadViewModel) {
    ZomdroidGlassCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(task.title ?: stringResource(R.string.workshop_download_center_item, task.publishedFileId), style = MaterialTheme.typography.titleMedium)
            Text("${task.state.name} · ${task.phase}", color = if (task.state == DownloadCenterTaskState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            if (task.totalBytes != null && task.totalBytes > 0) {
                val progress = (task.writtenBytes * 100 / task.totalBytes).toInt().coerceIn(0, 100)
                androidx.compose.material3.LinearProgressIndicator(progress / 100f, Modifier.fillMaxWidth())
                Text(stringResource(R.string.workshop_download_progress_format, progress, formatBytes(task.writtenBytes), formatBytes(task.totalBytes)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                when (task.state) { DownloadCenterTaskState.Queued, DownloadCenterTaskState.Running -> OutlinedButton(onClick = { viewModel.pause(task) }) { Text(stringResource(R.string.workshop_download_pause)) }; DownloadCenterTaskState.Paused -> OutlinedButton(onClick = { viewModel.resume(task) }) { Text(stringResource(R.string.workshop_download_resume)) }; DownloadCenterTaskState.Failed, DownloadCenterTaskState.Cancelled -> OutlinedButton(onClick = { viewModel.retry(task) }) { Text(stringResource(R.string.workshop_download_retry)) }; else -> Unit }
                if (task.state == DownloadCenterTaskState.Queued || task.state == DownloadCenterTaskState.Running || task.state == DownloadCenterTaskState.Paused) OutlinedButton(onClick = { viewModel.cancel(task) }) { Text(stringResource(R.string.workshop_download_cancel)) }
                OutlinedButton(onClick = { viewModel.delete(task) }) { Text(stringResource(R.string.workshop_download_delete)) }
            }
        }
    }
}

@Composable fun DownloadTaskDetailScreen(viewModel: DownloadViewModel, taskId: String, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycleCompat()
    val context = androidx.compose.ui.platform.LocalContext.current
    val logsTitle = stringResource(R.string.workshop_download_logs)
    val task = state.tasks.firstOrNull { it.id == taskId }
    if (task == null) { Column(Modifier.padding(24.dp)) { Text(stringResource(R.string.workshop_download_center_empty)); OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) } }; return }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row { androidx.compose.material3.IconButton(onClick = onBack) { androidx.compose.material3.Icon(Icons.Default.ArrowBack, null) }; Text(task.title ?: stringResource(R.string.workshop_download_center_item, task.publishedFileId), style = MaterialTheme.typography.headlineSmall) } }
        item { Text("${task.state.name} · ${task.phase}") }
        if (task.errorMessage != null) item { Text(task.errorMessage, color = MaterialTheme.colorScheme.error) }
        item { ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) { ZomdroidSectionLabel(logsTitle); Text(task.logs.joinToString("\n").ifBlank { "—" }); OutlinedButton(onClick = { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, task.logs.joinToString("\n")), logsTitle)) }) { Text(stringResource(R.string.workshop_library_share)) } } } }
        if (task.state == DownloadCenterTaskState.Success && task.outputPath != null) item { DownloadInstallActions(task, viewModel, onBack) }
    }
}

@Composable private fun DownloadInstallActions(task: DownloadCenterTask, viewModel: DownloadViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showInstances by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    Button(onClick = { showInstances = true }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.workshop_download_install)) }
    if (showInstances) InstancePickerDialog(context, onDismiss = { showInstances = false }) { instanceName, keepBackup ->
        runCatching {
            val entry = com.zomdroid.workshop.library.ModLibraryRepository(context).entriesFor(task.appId, task.publishedFileId).firstOrNull { it.completedPath == task.outputPath } ?: error("Library entry is missing")
            val instance = com.zomdroid.game.GameInstanceManager.requireSingleton().getInstanceByName(instanceName) ?: error("Game instance not found")
            context.startForegroundService(com.zomdroid.workshop.install.WorkshopLibraryInstaller.buildIntent(context, entry, instance, keepBackup))
            showInstances = false
        }
    }
}

@Composable private fun InstancePickerDialog(context: Context, onDismiss: () -> Unit, onChosen: (String, Boolean) -> Unit) {
    val instances = runCatching { com.zomdroid.game.GameInstanceManager.requireSingleton().instances }.getOrDefault(emptyList())
    var selected by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.workshop_library_choose_instance)) }, text = { Column { instances.forEach { instance -> Row(Modifier.fillMaxWidth().clickable { selected = instance.name }.padding(12.dp)) { Text(instance.name) } } } }, confirmButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
    selected?.let { name -> androidx.compose.material3.AlertDialog(onDismissRequest = { selected = null }, title = { Text(stringResource(R.string.workshop_library_overwrite_title)) }, text = { Text(stringResource(R.string.workshop_library_overwrite_message)) }, dismissButton = { OutlinedButton(onClick = { onChosen(name, false) }) { Text(stringResource(R.string.workshop_library_install_without_backup)) } }, confirmButton = { Button(onClick = { onChosen(name, true) }) { Text(stringResource(R.string.workshop_library_install_with_backup)) } }) }
}

@Composable private fun IconDownload() { androidx.compose.material3.Icon(Icons.Default.Download, null) }
private fun formatBytes(bytes: Long): String = when { bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024f); bytes < 1024L * 1024L * 1024L -> "%.1f MB".format(bytes / (1024f * 1024f)); else -> "%.2f GB".format(bytes / (1024f * 1024f * 1024f)) }
@Composable private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleCompat(): androidx.compose.runtime.State<T> = this.collectAsState()
