package com.zomdroid.ui.state

import com.zomdroid.ui.common.UiText

data class AppUiState(
    val selectedInstanceId: String? = null,
    val task: TaskUiState = TaskUiState(),
    val error: UiText? = null,
    val snackbar: UiText? = null,
)
