package com.zomdroid.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedButton as OutlinedButton
import com.zomdroid.ui.component.ZomdroidLiquidToggle as Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassCard

@Composable fun TouchControlsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.fragment_label_touch_controls), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.touch_controls_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.touch_controls_switch_label)); Switch(state.touchControls, viewModel::setTouchControls) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(stringResource(R.string.vibrate_on_touch_switch_label)); Switch(state.vibrateOnTouch, viewModel::setVibrateOnTouch) }
        } }
    }
}
