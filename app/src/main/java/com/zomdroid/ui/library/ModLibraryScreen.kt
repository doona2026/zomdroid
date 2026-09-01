package com.zomdroid.ui.library

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import com.zomdroid.ui.component.ZomdroidLiquidAlertDialog as AlertDialog
import com.zomdroid.ui.component.ZomdroidLiquidButton as Button
import androidx.compose.material3.Icon
import com.zomdroid.ui.component.ZomdroidLiquidIconButton as IconButton
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedButton as OutlinedButton
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.component.ZomdroidSectionLabel
import com.zomdroid.ui.component.ZomdroidEmptyState
import com.zomdroid.workshop.library.ModLibraryEntry

@Composable
fun ModLibraryScreen(viewModel: ModLibraryViewModel, onOpenDetail: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycleCompat()
    var showCleanup by remember { mutableStateOf(false) }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { ZomdroidSectionLabel(stringResource(R.string.workshop_library_title)); OutlinedButton(onClick = { showCleanup = true }) { Text(stringResource(R.string.workshop_library_cleanup)) } } }
        item { Text(stringResource(R.string.workshop_library_note), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { OutlinedTextField(state.query, viewModel::setQuery, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.workshop_search_hint)) }) }
        if (viewModel.visibleEntries().isEmpty()) item { ZomdroidEmptyState(stringResource(R.string.workshop_library_empty)) }
        items(viewModel.visibleEntries(), key = { "${it.publishedFileId}:${it.versionKey}" }) { entry -> LibraryCard(entry, { onOpenDetail(entry.publishedFileId); viewModel.select(entry) }, viewModel) }
    }
    if (showCleanup) AlertDialog(onDismissRequest = { showCleanup = false }, title = { Text(stringResource(R.string.workshop_library_cleanup_title)) }, text = { Text(stringResource(R.string.workshop_library_cleanup_message)) }, dismissButton = { OutlinedButton(onClick = { showCleanup = false }) { Text(stringResource(android.R.string.cancel)) } }, confirmButton = { Button(onClick = { viewModel.cleanup(); showCleanup = false }) { Text(stringResource(android.R.string.ok)) } })
}

@Composable private fun LibraryCard(entry: ModLibraryEntry, onOpen: () -> Unit, viewModel: ModLibraryViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showDelete by remember { mutableStateOf(false) }; var showInstances by remember { mutableStateOf(false) }
    ZomdroidGlassCard(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { AsyncImage(entry.previewUrl, entry.title, Modifier.size(92.dp, 56.dp)); Column { Text(entry.title, style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.workshop_library_meta, entry.publishedFileId, entry.source, entry.installedInstances.size)) } }
            if (entry.description.isNotBlank()) Text(entry.description, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Button(onClick = { showInstances = true }) { Text(stringResource(R.string.workshop_library_install)) }; OutlinedButton(onClick = { viewModel.checkUpdate(entry) }) { Text(stringResource(R.string.workshop_library_check_update)) }; IconButton(onClick = { shareEntry(context, entry) }) { Icon(Icons.Default.Share, stringResource(R.string.workshop_library_share)) }; OutlinedButton(onClick = { showDelete = true }) { Text(stringResource(R.string.workshop_library_delete)) } }
        }
    }
    if (showInstances) LibraryInstanceDialog(context, entry, { showInstances = false })
    if (showDelete) AlertDialog(onDismissRequest = { showDelete = false }, title = { Text(stringResource(R.string.workshop_library_delete_title)) }, text = { Text(stringResource(R.string.workshop_library_delete_message)) }, dismissButton = { OutlinedButton(onClick = { showDelete = false }) { Text(stringResource(android.R.string.cancel)) } }, confirmButton = { Button(onClick = { viewModel.remove(entry); showDelete = false }) { Text(stringResource(android.R.string.ok)) } })
}

@Composable private fun LibraryInstanceDialog(context: android.content.Context, entry: ModLibraryEntry, onDismiss: () -> Unit) {
    val instances = runCatching { com.zomdroid.game.GameInstanceManager.requireSingleton().instances }.getOrDefault(emptyList())
    var selected by remember { mutableStateOf<com.zomdroid.game.GameInstance?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.workshop_library_choose_instance)) }, text = { Column { instances.forEach { instance -> Row(Modifier.fillMaxWidth().clickable { selected = instance }.padding(12.dp)) { Text(instance.name) } } } }, confirmButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) } })
    selected?.let { instance -> AlertDialog(onDismissRequest = { selected = null }, title = { Text(stringResource(R.string.workshop_library_overwrite_title)) }, text = { Text(stringResource(R.string.workshop_library_overwrite_message)) }, dismissButton = { OutlinedButton(onClick = { installFromLibrary(context, entry, instance, false); onDismiss() }) { Text(stringResource(R.string.workshop_library_install_without_backup)) } }, confirmButton = { Button(onClick = { installFromLibrary(context, entry, instance, true); onDismiss() }) { Text(stringResource(R.string.workshop_library_install_with_backup)) } }) }
}

private fun installFromLibrary(context: android.content.Context, entry: ModLibraryEntry, instance: com.zomdroid.game.GameInstance, keepBackup: Boolean) {
    runCatching { context.startForegroundService(com.zomdroid.workshop.install.WorkshopLibraryInstaller.buildIntent(context, entry, instance, keepBackup)) }
}

@Composable fun ModDetailScreen(viewModel: ModLibraryViewModel, workshopId: Long, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycleCompat(); val entry = state.entries.firstOrNull { it.publishedFileId == workshopId }
    LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { item { Row { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }; Text(entry?.title ?: stringResource(R.string.workshop_library_title), style = MaterialTheme.typography.headlineSmall) } }; if (entry != null) { item { AsyncImage(entry.previewUrl, entry.title, Modifier.fillMaxWidth()) }; item { Text(entry.description.ifBlank { "—" }) }; item { Text(stringResource(R.string.workshop_library_meta, entry.publishedFileId, entry.source, entry.installedInstances.size)) } } else item { Text(stringResource(R.string.workshop_library_empty)) } }
}

private fun shareEntry(context: android.content.Context, entry: ModLibraryEntry) {
    runCatching {
        val uri = com.zomdroid.workshop.WorkshopFileAccess.contentUriForCompletedFile(context, java.io.File(entry.completedPath))
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), context.getString(R.string.workshop_library_share)))
    }
}
@Composable private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleCompat(): androidx.compose.runtime.State<T> = this.collectAsState()
