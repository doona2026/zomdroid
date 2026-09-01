package com.zomdroid.ui

import androidx.compose.runtime.Composable
import com.zomdroid.ui.model.AppAction
import com.zomdroid.ui.model.AppConfirmationDialog
import com.zomdroid.ui.component.ZomdroidConfirmDialog

@Composable
fun GlobalDialogs(dialog: AppConfirmationDialog?, onAction: (AppAction) -> Unit) {
    if (dialog == null) return
    ZomdroidConfirmDialog(
        title = dialog.title,
        message = dialog.message,
        onConfirm = { onAction(AppAction.DismissDialog) },
        onDismiss = { onAction(AppAction.DismissDialog) },
    )
}
