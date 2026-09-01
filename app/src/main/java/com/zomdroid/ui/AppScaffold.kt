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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import com.zomdroid.ui.component.ZomdroidLiquidIconButton as IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.component.ZomdroidGlassSurface
import com.zomdroid.ui.component.LocalZomdroidPopupHostState
import com.zomdroid.ui.component.ZomdroidPopupHost
import com.zomdroid.ui.component.rememberZomdroidPopupHostState
import com.zomdroid.ui.model.AppModule
import com.zomdroid.ui.model.AppUiState
import com.zomdroid.ui.theme.isLiquidGlassFrontendEnabled

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    state: AppUiState,
    onModuleSelected: (AppModule) -> Unit,
    onBack: () -> Unit,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (AppUiState) -> Unit = { currentState ->
        CompatModuleContent(currentState.selectedModule, Modifier.fillMaxSize())
    },
) {
    val hasDetails = state.backStack.size > 1
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 600.dp && maxWidth > maxHeight
        val popupHostState = rememberZomdroidPopupHostState()
        CompositionLocalProvider(LocalZomdroidPopupHostState provides popupHostState) {
            Box(Modifier.fillMaxSize()) {
                Scaffold(
                    containerColor = if (isLiquidGlassFrontendEnabled()) Color.Transparent else MaterialTheme.colorScheme.background,
                    topBar = {
                        ZomdroidGlassSurface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RectangleShape,
                            blurRadius = 24.dp,
                            enableLens = false,
                            surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .16f),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (hasDetails) {
                                    IconButton(onClick = onBack) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.app_shell_back))
                                    }
                                }
                                Text(moduleTitle(state.selectedModule), Modifier.padding(horizontal = 8.dp), style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    },
                    bottomBar = {
                        if (!isWide && !hasDetails) {
                            AdaptiveNavigation(
                                selected = state.selectedModule,
                                onSelected = onModuleSelected,
                                wide = false,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                ) { innerPadding ->
                    Row(Modifier.fillMaxSize().padding(innerPadding)) {
                        if (!hasDetails && isWide) AdaptiveNavigation(selected = state.selectedModule, onSelected = onModuleSelected, wide = true)
                        Box(Modifier.fillMaxSize().weight(1f)) { content(state) }
                    }
                }
                ZomdroidPopupHost(popupHostState)
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
