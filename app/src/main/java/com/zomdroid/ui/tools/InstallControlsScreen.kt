package com.zomdroid.ui.tools

import androidx.compose.runtime.Composable
import com.zomdroid.InstallerService
import com.zomdroid.R

@Composable fun InstallControlsScreen(viewModel: ToolTaskViewModel, onBack: () -> Unit) = ToolTaskScreen(ToolSpec(InstallerService.Task.INSTALL_CONTROLS_TO_INSTANCE, R.string.menu_install_controls, R.string.controls_editor_open_hint), viewModel, onBack)
