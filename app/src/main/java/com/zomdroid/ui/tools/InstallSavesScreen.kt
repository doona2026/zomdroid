package com.zomdroid.ui.tools

import androidx.compose.runtime.Composable
import com.zomdroid.InstallerService
import com.zomdroid.R

@Composable
fun InstallSavesScreen(viewModel: ToolTaskViewModel, onBack: () -> Unit) =
    ToolTaskScreen(ToolSpec(InstallerService.Task.INSTALL_SAVES_TO_INSTANCE, R.string.install_saves_menu, R.string.install_saves_menu), viewModel, onBack)
