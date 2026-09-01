package com.zomdroid.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassSurface
import com.zomdroid.ui.component.ZomdroidLiquidBottomNavigation
import com.zomdroid.ui.model.AppModule
import com.zomdroid.ui.theme.isLiquidGlassFrontendEnabled

private data class NavigationEntry(val module: AppModule, val icon: ImageVector, val labelRes: Int)

private val entries = listOf(
    NavigationEntry(AppModule.Launcher, Icons.Default.Home, R.string.app_module_launcher),
    NavigationEntry(AppModule.Workshop, Icons.Default.Explore, R.string.app_module_workshop),
    NavigationEntry(AppModule.Downloads, Icons.Default.Download, R.string.app_module_downloads),
    NavigationEntry(AppModule.ModLibrary, Icons.Default.Extension, R.string.app_module_mod_library),
    NavigationEntry(AppModule.Settings, Icons.Default.Settings, R.string.app_module_settings),
)

@Composable
fun AdaptiveNavigation(selected: AppModule, onSelected: (AppModule) -> Unit, wide: Boolean, modifier: Modifier = Modifier) {
    Box(modifier) {
        if (!wide) {
            if (isLiquidGlassFrontendEnabled()) {
                ZomdroidLiquidBottomNavigation(
                    selectedIndex = entries.indexOfFirst { it.module == selected }.coerceAtLeast(0),
                    onSelected = { onSelected(entries[it].module) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("primary_navigation_bar"),
                ) { index, isSelected, onClick ->
                    val entry = entries[index]
                    val label = stringResource(entry.labelRes)
                    Column(
                        Modifier
                            .fillMaxHeight()
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(role = Role.Tab, onClick = onClick)
                            .semantics { role = Role.Tab; this.selected = isSelected },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        Icon(entry.icon, contentDescription = label)
                        Text(label)
                    }
                }
            } else {
                ZomdroidGlassSurface(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).testTag("primary_navigation_bar"),
                    shape = RoundedCornerShape(32.dp),
                    surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .2f),
                ) {
                    NavigationBar(containerColor = Color.Transparent) {
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
                }
            }
        } else {
            if (isLiquidGlassFrontendEnabled()) {
                ZomdroidGlassSurface(
                    Modifier.fillMaxHeight().width(96.dp).testTag("primary_navigation_rail"),
                    shape = RectangleShape,
                    enableLens = false,
                    surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .16f),
                ) {
                    Column(
                        Modifier.fillMaxHeight().padding(horizontal = 6.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        entries.forEach { entry ->
                            val isSelected = selected == entry.module
                            val label = stringResource(entry.labelRes)
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(24.dp))
                                    .clickable(onClick = { onSelected(entry.module) })
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = .18f) else Color.Transparent)
                                    .padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Icon(entry.icon, contentDescription = label)
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            } else {
                ZomdroidGlassSurface(
                    Modifier.fillMaxHeight().testTag("primary_navigation_rail"),
                    shape = RectangleShape,
                    enableLens = false,
                    surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .16f),
                ) {
                    NavigationRail(containerColor = Color.Transparent) {
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
    }
}
