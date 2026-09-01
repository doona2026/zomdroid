package com.zomdroid.ui.tools

import com.zomdroid.InstallerService

data class ToolTaskUiState(
    val title: String,
    val message: String? = null,
    val progress: Int = -1,
    val progressMax: Int = 0,
    val finished: Boolean = false,
    val failed: Boolean = false,
)

object InstallerTaskStateMapper {
    fun from(state: InstallerService.TaskState?): ToolTaskUiState? = state?.let {
        ToolTaskUiState(it.title ?: "", it.message, it.progress, it.progressMax, it.isFinished, it.isFinishedWithError)
    }
}
