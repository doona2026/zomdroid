package com.zomdroid.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.zomdroid.R
import com.zomdroid.ui.common.UiTokens
import com.zomdroid.ui.common.resolve
import com.zomdroid.ui.state.TaskUiState
import com.zomdroid.ui.state.TaskUiStatus
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun DependencyInstallDialog(
    task: TaskUiState?,
    onAcknowledge: () -> Unit,
    onRetry: () -> Unit,
    onExit: () -> Unit,
) {
    if (task == null) return
    val context = LocalContext.current
    val title = task.title?.resolve(context)
        ?: stringResource(R.string.dialog_title_installing_dependencies)
    OverlayDialog(
        show = true,
        title = title,
        onDismissRequest = null,
        largeScreen = true,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = UiTokens.ContentMaxWidth)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            when (task.status) {
                TaskUiStatus.Running -> {
                    task.message?.let { Text(text = it.resolve(context)) }
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        progress = task.progressPercent?.div(100f),
                    )
                }
                TaskUiStatus.Completed -> {
                    task.message?.let { Text(text = it.resolve(context)) }
                    Button(onClick = onAcknowledge, modifier = Modifier.fillMaxWidth()) {
                        Text(text = stringResource(R.string.dialog_button_ok))
                    }
                }
                TaskUiStatus.Failed -> {
                    Text(
                        text = task.error?.resolve(context)
                            ?: stringResource(R.string.dialog_title_failed_to_install_dependencies),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(UiTokens.CompactSpacing),
                    ) {
                        Button(onClick = onRetry, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.dependency_install_retry))
                        }
                        Button(onClick = onExit, modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.dependency_install_exit))
                        }
                    }
                }
                TaskUiStatus.Idle -> Unit
            }
        }
    }
}
