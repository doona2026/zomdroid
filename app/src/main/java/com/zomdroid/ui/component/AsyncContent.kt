package com.zomdroid.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun <T> ZomdroidAsyncContent(
    value: T?,
    loading: Boolean,
    errorMessage: String?,
    isEmpty: Boolean,
    onRetry: (() -> Unit)? = null,
    empty: @Composable () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when {
        loading -> Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        errorMessage != null -> ZomdroidErrorState(errorMessage, onRetry)
        isEmpty || value == null -> empty()
        else -> content(value)
    }
}
