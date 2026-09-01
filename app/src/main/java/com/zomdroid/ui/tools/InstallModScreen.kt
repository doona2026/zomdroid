package com.zomdroid.ui.tools

import androidx.compose.runtime.Composable
import com.zomdroid.InstallerService
import com.zomdroid.R

@Composable
fun InstallModScreen(viewModel: ToolTaskViewModel, onBack: () -> Unit) =
    ToolTaskScreen(ToolSpec(InstallerService.Task.INSTALL_MOD_TO_INSTANCE, R.string.nav_menu_install_mod, R.string.nav_menu_install_mod), viewModel, onBack)
