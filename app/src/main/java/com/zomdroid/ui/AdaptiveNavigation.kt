package com.zomdroid.ui

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zomdroid.R
import com.zomdroid.ui.model.AppModule

private data class NavigationEntry(val module: AppModule, val icon: ImageVector, val labelRes: Int)

private val entries = listOf(
    NavigationEntry(AppModule.Launcher, Icons.Default.Home, R.string.app_module_launcher),
    NavigationEntry(AppModule.Workshop, Icons.Default.Explore, R.string.app_module_workshop),
    NavigationEntry(AppModule.Downloads, Icons.Default.Download, R.string.app_module_downloads),
    NavigationEntry(AppModule.ModLibrary, Icons.Default.Extension, R.string.app_module_mod_library),
    NavigationEntry(AppModule.Settings, Icons.Default.Settings, R.string.app_module_settings),
)

@Composable
fun AdaptiveNavigation(selected: AppModule, onSelected: (AppModule) -> Unit, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        if (maxWidth < 600.dp) {
            NavigationBar(Modifier.fillMaxWidth()) {
                entries.forEach { entry ->
                    val label = stringResource(entry.labelRes)
                    NavigationBarItem(
                        selected = selected == entry.module,
                        onClick = { onSelected(entry.module) },
                        icon = { Icon(entry.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        } else {
            NavigationRail {
                entries.forEach { entry ->
                    val label = stringResource(entry.labelRes)
                    NavigationRailItem(
                        selected = selected == entry.module,
                        onClick = { onSelected(entry.module) },
                        icon = { Icon(entry.icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        }
    }
}
