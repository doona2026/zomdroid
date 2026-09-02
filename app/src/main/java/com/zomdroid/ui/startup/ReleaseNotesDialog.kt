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
import androidx.compose.ui.unit.dp
import com.zomdroid.R
import com.zomdroid.ui.common.UiTokens
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayDialog

@Composable
fun ReleaseNotesDialog(
    show: Boolean,
    version: String?,
    onDismiss: () -> Unit,
    onOpenLink: () -> Unit,
) {
    if (!show) return
    val safeVersion = version ?: return
    OverlayDialog(
        show = true,
        title = stringResource(R.string.release_notes_title, safeVersion),
        onDismissRequest = onDismiss,
        largeScreen = true,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = UiTokens.ContentMaxWidth)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(UiTokens.ContentSpacing),
        ) {
            Text(text = stringResource(R.string.release_notes_body))
            Button(onClick = onOpenLink, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.release_notes_open_changelog))
            }
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.dialog_button_ok))
            }
        }
    }
}
