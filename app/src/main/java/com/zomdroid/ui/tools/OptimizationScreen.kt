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
fun OptimizationScreen(viewModel: ToolTaskViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) }
        ToolTaskCard(ToolSpec(InstallerService.Task.INSTALL_BETTERFPS, R.string.optimization_group_betterfps, R.string.optimization_betterfps_hint), viewModel)
        ToolTaskCard(ToolSpec(InstallerService.Task.INSTALL_RENDER_LESS_ZOMBIE, R.string.optimization_group_rlz, R.string.optimization_rlz_hint), viewModel)
        ToolTaskCard(ToolSpec(InstallerService.Task.INSTALL_ETO, R.string.optimization_group_eto, R.string.optimization_eto_hint), viewModel)
        ToolTaskCard(ToolSpec(InstallerService.Task.INSTALL_ZOMBIEBUDDY, R.string.optimization_group_zombiebuddy, R.string.optimization_zombiebuddy_hint), viewModel)
        ToolTaskCard(ToolSpec(InstallerService.Task.INSTALL_ZBBETTERFPS, R.string.optimization_group_zbbetterfps, R.string.optimization_zbbetterfps_hint), viewModel)
    }
}
