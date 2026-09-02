package com.zomdroid.ui.startup

import com.zomdroid.ui.state.TaskUiState

data class StartupUiState(
    val legalNoticeVisible: Boolean = false,
    val dependencyTask: TaskUiState? = null,
    val releaseNotesVisible: Boolean = false,
    val releaseNotesVersion: String? = null,
    val globalTask: TaskUiState = TaskUiState(),
)
