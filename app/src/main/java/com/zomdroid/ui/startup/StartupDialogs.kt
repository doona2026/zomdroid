package com.zomdroid.ui.startup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.zomdroid.R
import com.zomdroid.ui.common.UiTokens
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog

data class StartupDialogActions(
    val onAcceptLegalNotice: () -> Unit,
    val onAcknowledgeDependencyTask: () -> Unit,
    val onRetryDependencyTask: () -> Unit,
    val onExitAfterDependencyFailure: () -> Unit,
    val onDismissReleaseNotes: () -> Unit,
    val onOpenReleaseNotesLink: () -> Unit,
)

@Composable
fun StartupDialogs(
    state: StartupUiState,
    actions: StartupDialogActions,
) {
    LegalNoticeDialog(
        show = state.legalNoticeVisible,
        onAccept = actions.onAcceptLegalNotice,
    )
    DependencyInstallDialog(
        task = state.dependencyTask,
        onAcknowledge = actions.onAcknowledgeDependencyTask,
        onRetry = actions.onRetryDependencyTask,
        onExit = actions.onExitAfterDependencyFailure,
    )
    ReleaseNotesDialog(
        show = state.releaseNotesVisible,
        version = state.releaseNotesVersion,
        onDismiss = actions.onDismissReleaseNotes,
        onOpenLink = actions.onOpenReleaseNotesLink,
    )
}

@Composable
private fun LegalNoticeDialog(show: Boolean, onAccept: () -> Unit) {
    OverlayDialog(
        show = show,
        title = stringResource(R.string.legal_notice_title),
        onDismissRequest = null,
        largeScreen = true,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = UiTokens.ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            Text(text = stringResource(R.string.legal_notice_message))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.dialog_button_accept))
            }
        }
    }
}
