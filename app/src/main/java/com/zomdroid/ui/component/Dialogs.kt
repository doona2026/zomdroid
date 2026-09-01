package com.zomdroid.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.zomdroid.R

@Composable
fun ZomdroidConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = stringResource(R.string.dialog_button_confirm),
    dismissLabel: String = stringResource(R.string.dialog_button_cancel),
    dismissOnBackPress: Boolean = true,
    dismissOnClickOutside: Boolean = true,
) {
    ZomdroidLiquidAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { ZomdroidLiquidTextButton(onClick = onConfirm) { Text(confirmLabel, color = MaterialTheme.colorScheme.primary) } },
        dismissButton = { ZomdroidLiquidTextButton(onClick = onDismiss) { Text(dismissLabel, color = MaterialTheme.colorScheme.primary) } },
        properties = DialogProperties(
            dismissOnBackPress = dismissOnBackPress,
            dismissOnClickOutside = dismissOnClickOutside,
        ),
    )
}

@Composable
fun ZomdroidInfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    ZomdroidLiquidAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { ZomdroidLiquidTextButton(onClick = onDismiss) { Text(stringResource(R.string.dialog_button_ok), color = MaterialTheme.colorScheme.primary) } },
    )
}

@Composable
fun ZomdroidLiquidAlertDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    properties: DialogProperties = DialogProperties(),
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
) {
    Dialog(onDismissRequest = onDismissRequest, properties = properties) {
        ZomdroidGlassSurface(
            modifier = modifier.widthIn(min = 280.dp, max = 420.dp),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
            blurRadius = 24.dp,
            lensHeight = 16.dp,
            lensAmount = 18.dp,
            surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .28f),
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                title?.invoke()
                text?.invoke()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}
