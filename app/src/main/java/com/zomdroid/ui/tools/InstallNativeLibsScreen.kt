package com.zomdroid.ui.tools

import androidx.compose.runtime.Composable
import com.zomdroid.InstallerService
import com.zomdroid.R

@Composable fun InstallNativeLibsScreen(viewModel: ToolTaskViewModel, onBack: () -> Unit) = ToolTaskScreen(ToolSpec(InstallerService.Task.INSTALL_NATIVE_LIBS, R.string.native_libs_menu, R.string.native_libs_hint), viewModel, onBack)
