package com.zomdroid.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.zomdroid.ui.component.ZomdroidLiquidButton as Button
import androidx.compose.material3.MaterialTheme
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedButton as OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zomdroid.R
import com.zomdroid.input.GamepadManager
import com.zomdroid.ui.component.ZomdroidGlassCard

class GamepadMapperViewModel(private val appContext: Context) {
    fun resetToDefault() { GamepadManager.setCustomMapping(null, appContext) }
}

@Composable
fun GamepadMapperScreen(viewModel: GamepadMapperViewModel, onStartMapping: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) }
        ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.gamepad_mapper_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.gamepad_mapper_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onStartMapping, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gamepad_mapper_start)) }
            OutlinedButton(onClick = viewModel::resetToDefault, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.gamepad_mapper_reset)) }
            Text(stringResource(R.string.gamepad_mapper_protocol_note), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}
