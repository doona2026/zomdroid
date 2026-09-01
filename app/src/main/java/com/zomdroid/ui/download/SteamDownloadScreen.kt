package com.zomdroid.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.zomdroid.ui.component.ZomdroidLiquidAlertDialog as AlertDialog
import com.zomdroid.ui.component.ZomdroidLiquidButton as Button
import com.zomdroid.ui.component.ZomdroidLinearProgressIndicator as LinearProgressIndicator
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedButton as OutlinedButton
import com.zomdroid.ui.component.ZomdroidLiquidOutlinedTextField as OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zomdroid.R
import com.zomdroid.ui.component.ZomdroidGlassCard
import com.zomdroid.ui.component.ZomdroidSectionLabel

@Composable
fun SteamDownloadScreen(viewModel: SteamDownloadViewModel, onBack: () -> Unit, onRequestStorageAccess: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycleCompat()
    var gameMode by remember { mutableStateOf(true) }; var username by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }; var manifest by remember { mutableStateOf("") }; var mods by remember { mutableStateOf("") }; var build42 by remember { mutableStateOf(false) }; var guardCode by remember { mutableStateOf("") }
    LaunchedEffect(state.storageAccessRequired) { if (state.storageAccessRequired) { onRequestStorageAccess(); viewModel.storageRequestHandled() } }
    LazyColumnCompat(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.app_shell_back)) }; ZomdroidSectionLabel(stringResource(R.string.steam_dl_menu)) } }
        item { Text(stringResource(R.string.steam_dl_version_note), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { gameMode = true }) { Text(stringResource(R.string.steam_dl_tab_game)) }; OutlinedButton(onClick = { gameMode = false }) { Text(stringResource(R.string.steam_dl_tab_mods)) } } }
        if (gameMode) {
            item { ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { ZomdroidSectionLabel(stringResource(R.string.steam_dl_login_group)); OutlinedTextField(value = username, onValueChange = { username = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.steam_dl_username)) }); OutlinedTextField(value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.steam_dl_password)) }); OutlinedTextField(value = manifest, onValueChange = { manifest = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.steam_dl_manifest_hint)) }); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { OutlinedButton(onClick = { build42 = false }) { Text(stringResource(R.string.steam_dl_build_41)) }; OutlinedButton(onClick = { build42 = true }) { Text(stringResource(R.string.steam_dl_build_42)) } }; Button(enabled = !state.downloading, onClick = { viewModel.startGame(username, password, manifest, build42) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.steam_dl_download_game)) } } } }
        } else item { ZomdroidGlassCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.steam_dl_mods_official_note), color = MaterialTheme.colorScheme.onSurfaceVariant); OutlinedTextField(value = mods, onValueChange = { mods = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.steam_dl_mods_hint)) }); Button(enabled = !state.downloading, onClick = { viewModel.startMods(mods) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.steam_dl_download_mods)) } } } }
        if (state.downloading) item { Button(onClick = viewModel::cancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.steam_dl_cancel)) } }
        item { if (state.indeterminate && state.downloading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) else if (state.percent >= 0) LinearProgressIndicator(progress = state.percent / 100f, modifier = Modifier.fillMaxWidth()) }
        item { ZomdroidGlassCard(Modifier.fillMaxWidth()) { Text(state.log.ifBlank { "—" }, Modifier.padding(12.dp).height(220.dp).verticalScroll(rememberScrollState())) } }
    }
    state.guardRequest?.let { request -> AlertDialog(onDismissRequest = {}, title = { Text(if (request.previousWrong) stringResource(R.string.steam_dl_guard_retry) else stringResource(R.string.steam_dl_guard_title)) }, text = { OutlinedTextField(value = guardCode, onValueChange = { guardCode = it }, label = { Text(request.email?.let { email -> stringResource(R.string.steam_dl_guard_email, email) } ?: stringResource(R.string.steam_dl_guard_hint)) }) }, confirmButton = { Button(onClick = { viewModel.submitGuardCode(guardCode); guardCode = "" }) { Text(stringResource(android.R.string.ok)) } }) }
    state.finishedMessage?.let { message -> AlertDialog(onDismissRequest = viewModel::clearFinishedMessage, text = { Text(message) }, confirmButton = { Button(onClick = viewModel::clearFinishedMessage) { Text(stringResource(android.R.string.ok)) } }) }
}

@Composable private fun LazyColumnCompat(modifier: Modifier, verticalArrangement: Arrangement.Vertical, content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) { androidx.compose.foundation.lazy.LazyColumn(modifier, verticalArrangement = verticalArrangement, content = content) }
@Composable private fun <T> kotlinx.coroutines.flow.StateFlow<T>.collectAsStateWithLifecycleCompat(): androidx.compose.runtime.State<T> = this.collectAsState()
