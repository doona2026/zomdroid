package com.zomdroid.ui.launcher

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassCard

@Composable
fun LauncherScreen(
    state: LauncherUiState,
    onAction: (LauncherAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            androidx.compose.foundation.layout.Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.launcher_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.launcher_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { onAction(LauncherAction.Refresh) }) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.launcher_refresh))
                }
            }
            if (state.isRefreshing && state.instances.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (state.instances.isEmpty()) {
                EmptyLauncherState(onAdd = { onAction(LauncherAction.OpenNewGameInstance) }, modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(state.instances, key = { it.name }) { instance ->
                        GameInstanceCard(instance, onAction)
                    }
                    if (state.task != null) {
                        item { TaskBanner(state.task) }
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = { onAction(LauncherAction.OpenNewGameInstance) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
        ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.launcher_add_instance)) }

        state.crashRecovery?.let { instance ->
            AlertDialog(
                onDismissRequest = { onAction(LauncherAction.ContinueAfterCrash) },
                title = { Text(stringResource(R.string.backup_restore_title)) },
                text = { Text(stringResource(R.string.backup_restore_message, instance.backup?.worldRel ?: instance.name, "", (instance.backup?.sizeBytes ?: 0L) shr 20)) },
                confirmButton = { TextButton(onClick = { onAction(LauncherAction.RestoreCrashedBackup) }) { Text(stringResource(R.string.backup_restore_do)) } },
                dismissButton = { TextButton(onClick = { onAction(LauncherAction.ContinueAfterCrash) }) { Text(stringResource(R.string.backup_restore_continue)) } },
            )
        }
        state.deleteConfirmation?.let {
            AlertDialog(
                onDismissRequest = { onAction(LauncherAction.DismissDelete) },
                title = { Text(stringResource(R.string.dialog_title_delete_game_instance)) },
                text = { Text(stringResource(R.string.delete_game_instance)) },
                confirmButton = { TextButton(onClick = { onAction(LauncherAction.ConfirmDelete) }) { Text(stringResource(R.string.dialog_button_confirm)) } },
                dismissButton = { TextButton(onClick = { onAction(LauncherAction.DismissDelete) }) { Text(stringResource(R.string.dialog_button_cancel)) } },
            )
        }
        state.backupRestore?.let { instance ->
            val backup = instance.backup
            if (backup != null) {
                AlertDialog(
                    onDismissRequest = { onAction(LauncherAction.DismissDelete) },
                    title = { Text(stringResource(R.string.game_instance_restore_backup)) },
                    text = { Text(stringResource(R.string.backup_manual_restore_message, backup.worldRel, "", backup.sizeBytes shr 20)) },
                    confirmButton = { TextButton(onClick = { onAction(LauncherAction.RestoreBackup) }) { Text(stringResource(R.string.backup_restore_do)) } },
                    dismissButton = { TextButton(onClick = { onAction(LauncherAction.DismissDelete) }) { Text(stringResource(R.string.dialog_button_cancel)) } },
                )
            }
        }
    }
}

@Composable
private fun EmptyLauncherState(onAdd: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.launcher_empty_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.launcher_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.Button(onClick = onAdd) { Text(stringResource(R.string.launcher_add_instance)) }
            }
        }
    }
}

@Composable
private fun TaskBanner(task: LauncherTaskUiState) {
    ZomdroidGlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(task.title, style = MaterialTheme.typography.titleMedium)
            task.message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (task.progress < 0) CircularProgressIndicator(Modifier.padding(top = 10.dp))
            else androidx.compose.material3.LinearProgressIndicator(
                progress = { if (task.progressMax > 0) task.progress.toFloat() / task.progressMax else 0f },
                Modifier.fillMaxWidth().padding(top = 10.dp),
            )
        }
    }
}
