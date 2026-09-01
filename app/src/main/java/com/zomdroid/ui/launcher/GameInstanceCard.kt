package com.zomdroid.ui.launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import com.zomdroid.ui.component.ZomdroidLiquidIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassSurface

@Composable
fun GameInstanceCard(
    instance: LauncherInstanceUiModel,
    onAction: (LauncherAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    ZomdroidGlassSurface(modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(14.dp)) {
            Image(
                painter = painterResource(instanceBanner(instance.presetName)),
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(18.dp)),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(instance.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        stringResource(R.string.launcher_build_label, instance.presetName),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = { onAction(LauncherAction.OpenInstanceSettings(instance.name)) }) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.app_module_settings))
                }
                GameInstanceActions(instance, onAction)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = {},
                    label = { Text(instanceStatus(instance)) },
                    leadingIcon = { Icon(Icons.Default.Settings, null, Modifier.size(16.dp)) },
                )
                Spacer(Modifier.weight(1f))
                com.zomdroid.ui.component.ZomdroidLiquidButton(onClick = { onAction(LauncherAction.Launch(instance.name)) }) {
                    Icon(Icons.Default.PlayArrow, null)
                    Text(stringResource(R.string.launcher_launch), Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

private fun instanceBanner(presetName: String): Int = when (presetName) {
    "Build 42.12+" -> R.drawable.banner_build42_12
    "Build 42" -> R.drawable.banner_build42
    else -> R.drawable.banner_build41
}

@Composable
private fun instanceStatus(instance: LauncherInstanceUiModel): String = when {
    !instance.installationFinished -> stringResource(R.string.launcher_status_installing)
    !instance.hasGameFiles -> stringResource(R.string.launcher_status_missing_files)
    !instance.hasFilesForLinux -> stringResource(R.string.launcher_status_wrong_files)
    else -> stringResource(R.string.launcher_status_ready)
}
