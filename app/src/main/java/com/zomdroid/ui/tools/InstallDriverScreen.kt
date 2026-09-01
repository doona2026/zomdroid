package com.zomdroid.ui.tools

import androidx.compose.runtime.Composable
import com.zomdroid.InstallerService
import com.zomdroid.R

@Composable fun InstallDriverScreen(viewModel: ToolTaskViewModel, onBack: () -> Unit) = ToolTaskScreen(ToolSpec(InstallerService.Task.IMPORT_CUSTOM_DRIVER, R.string.install_driver_menu, R.string.install_driver_menu, false, "*/*"), viewModel, onBack)
