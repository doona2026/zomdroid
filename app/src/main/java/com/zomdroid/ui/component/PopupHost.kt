package com.zomdroid.ui.component

import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

// Adapted from WorkshopAndroidDownloader's popup host concept under Apache-2.0.
data class ZomdroidPopupAction(val label: String, val onClick: () -> Unit)
class ZomdroidPopupHostState {
    var actions by mutableStateOf<List<ZomdroidPopupAction>>(emptyList())
        private set
    fun show(actions: List<ZomdroidPopupAction>) { this.actions = actions }
    fun dismiss() { actions = emptyList() }
}

@Composable fun rememberZomdroidPopupHostState() = remember { ZomdroidPopupHostState() }

@Composable
fun ZomdroidPopupHost(state: ZomdroidPopupHostState, expanded: Boolean, onDismissRequest: () -> Unit, modifier: Modifier = Modifier) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismissRequest, modifier = modifier) {
        state.actions.forEach { action -> DropdownMenuItem(text = { androidx.compose.material3.Text(action.label) }, onClick = { action.onClick(); state.dismiss() }) }
    }
}

