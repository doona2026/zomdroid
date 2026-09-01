package com.zomdroid.ui.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zomdroid.InstallerService
import com.zomdroid.R

@Composable
fun ModFixesScreen(viewModel: ToolTaskViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) }
        ToolTaskCard(
            ToolSpec(InstallerService.Task.INSTALL_MOD_WITH_FIX, R.string.nav_menu_mod_fixes, R.string.mod_fix_double_path_hint),
            viewModel,
        )
    }
}
