package com.zomdroid.ui.launcher

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.zomdroid.R
import androidx.compose.ui.res.stringResource
import com.zomdroid.ui.component.ZomdroidGlassIconButton
import com.zomdroid.ui.component.ZomdroidPopupMenu
import com.zomdroid.ui.component.ZomdroidPopupMenuItem

@Composable
fun GameInstanceActions(
    instance: LauncherInstanceUiModel,
    onAction: (LauncherAction) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    androidx.compose.foundation.layout.Box {
        ZomdroidGlassIconButton(Icons.Default.MoreVert, stringResource(R.string.app_shell_more), { expanded = true })
        ZomdroidPopupMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            ZomdroidPopupMenuItem(
                text = { Text(stringResource(R.string.game_instance_manage_storage)) },
                leadingIcon = { Icon(Icons.Default.Folder, null) },
                onClick = { expanded = false; onAction(LauncherAction.OpenStorage(instance.name)) },
            )
            ZomdroidPopupMenuItem(
                text = { Text(stringResource(R.string.game_instance_restore_backup)) },
                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                onClick = { expanded = false; onAction(LauncherAction.RequestBackupRestore(instance.name)) },
            )
            ZomdroidPopupMenuItem(
                text = { Text(stringResource(R.string.app_module_settings)) },
                leadingIcon = { Icon(Icons.Default.Settings, null) },
                onClick = { expanded = false; onAction(LauncherAction.OpenInstanceSettings(instance.name)) },
            )
            ZomdroidPopupMenuItem(
                text = { Text(stringResource(R.string.game_instance_delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { expanded = false; onAction(LauncherAction.RequestDelete(instance.name)) },
            )
        }
    }
}
