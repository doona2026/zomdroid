package com.zomdroid.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.tools.InstancePicker
import com.zomdroid.ui.tools.ToolTaskViewModel

@Composable fun ControlsEditorLaunchScreen(viewModel: ToolTaskViewModel, onOpenEditor: (String, String?) -> Unit, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let(viewModel::saveEditorBackground) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.nav_menu_controls_editor), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.controls_editor_open_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            InstancePicker(state.instances.map { it.name }, state.selectedInstance, viewModel::selectInstance)
            Text(stringResource(R.string.controls_editor_bg_group), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.controls_editor_bg_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { picker.launch("image/*") }, enabled = state.selectedInstance != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.game_instance_browse_files_hint)) }
            if (state.editorBackgroundPath != null) OutlinedButton(onClick = viewModel::clearEditorBackground, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.controls_editor_bg_clear)) }
            Button(onClick = { state.selectedInstance?.let { onOpenEditor(it, state.editorBackgroundPath) } }, enabled = state.selectedInstance != null, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.controls_editor_open_btn)) }
        } }
    }
}
