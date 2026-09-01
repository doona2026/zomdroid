package com.zomdroid.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.model.AppModule
import com.zomdroid.ui.model.AppUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    state: AppUiState,
    onModuleSelected: (AppModule) -> Unit,
    onBack: () -> Unit,
    onMenuClick: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (AppUiState) -> Unit = { currentState ->
        CompatModuleContent(currentState.selectedModule, Modifier.fillMaxSize())
    },
) {
    val hasDetails = state.backStack.size > 1
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !hasDetails,
        drawerContent = {
            ModalDrawerSheet {
                Column(Modifier.padding(vertical = 18.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(20.dp))
                    AppModule.values().forEach { module ->
                        NavigationDrawerItem(
                            label = { Text(moduleTitle(module)) },
                            selected = module == state.selectedModule,
                            onClick = { onModuleSelected(module); scope.launch { drawerState.close() } },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val isWide = maxWidth >= 600.dp
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(moduleTitle(state.selectedModule)) },
                        navigationIcon = {
                            IconButton(onClick = if (hasDetails) onBack else {
                                { scope.launch { drawerState.open() }; onMenuClick() }
                            }) {
                                Icon(
                                    if (hasDetails) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Menu,
                                    contentDescription = stringResource(if (hasDetails) R.string.app_shell_back else R.string.app_shell_menu),
                                )
                            }
                        },
                    )
                },
                bottomBar = {
                    if (!hasDetails && !isWide) AdaptiveNavigation(selected = state.selectedModule, onSelected = onModuleSelected)
                },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                Row(Modifier.fillMaxSize().padding(innerPadding)) {
                    if (!hasDetails && isWide) AdaptiveNavigation(selected = state.selectedModule, onSelected = onModuleSelected)
                    Box(Modifier.fillMaxSize().weight(1f)) { content(state) }
                }
            }
        }
    }
}

@Composable
internal fun CompatModuleContent(module: AppModule, modifier: Modifier) {
    Column(modifier.padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        ZomdroidGlassCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.app_shell_compat_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.app_shell_compat_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(moduleTitle(module), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun moduleTitle(module: AppModule): String = stringResource(
    when (module) {
        AppModule.Launcher -> R.string.app_module_launcher
        AppModule.Workshop -> R.string.app_module_workshop
        AppModule.Downloads -> R.string.app_module_downloads
        AppModule.ModLibrary -> R.string.app_module_mod_library
        AppModule.Settings -> R.string.app_module_settings
    },
)
