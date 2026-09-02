package com.zomdroid.ui.state

import com.zomdroid.ui.common.UiText

enum class TaskUiStatus {
    Idle,
    Running,
    Completed,
    Failed,
}

data class TaskUiState(
    val status: TaskUiStatus = TaskUiStatus.Idle,
    val progressPercent: Int? = null,
    val title: UiText? = null,
    val message: UiText? = null,
    val error: UiText? = null,
    val taskCount: Int = 0,
)
