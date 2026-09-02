package com.zomdroid.ui.startup

import com.google.common.truth.Truth.assertThat
import com.zomdroid.InstallerService
import com.zomdroid.ui.state.TaskUiStatus
import com.zomdroid.workshop.download.DownloadCenterTask
import com.zomdroid.workshop.download.DownloadCenterTaskState
import org.junit.Test

class StartupUiAdapterTest {
    @Test
    fun installerProgressMapsToRunningPercentAndText() {
        val mapped = StartupUiAdapter.mapInstallerTaskState(
            InstallerService.TaskState("Installing", "Extracting", 25, 100, false, false),
        )

        assertThat(mapped.status).isEqualTo(TaskUiStatus.Running)
        assertThat(mapped.progressPercent).isEqualTo(25)
        assertThat(mapped.title).isNotNull()
        assertThat(mapped.message).isNotNull()
    }

    @Test
    fun installerFailureMapsToRetryableFailure() {
        val mapped = StartupUiAdapter.mapInstallerTaskState(
            InstallerService.TaskState("Install failed", "Disk full", -1, 0, false, true),
        )

        assertThat(mapped.status).isEqualTo(TaskUiStatus.Failed)
        assertThat(mapped.error).isNotNull()
        assertThat(mapped.progressPercent).isNull()
    }

    @Test
    fun workshopTasksAggregateToVisibleGlobalEntry() {
        val mapped = StartupUiAdapter.mapWorkshopTasks(
            listOf(
                DownloadCenterTask(
                    id = "active",
                    appId = 1,
                    publishedFileId = 2,
                    title = "Mod",
                    state = DownloadCenterTaskState.Running,
                    writtenBytes = 50,
                    totalBytes = 100,
                ),
                DownloadCenterTask(
                    id = "failed",
                    appId = 1,
                    publishedFileId = 3,
                    state = DownloadCenterTaskState.Failed,
                    errorMessage = "Network error",
                ),
            ),
        )

        assertThat(mapped.status).isEqualTo(TaskUiStatus.Failed)
        assertThat(mapped.progressPercent).isEqualTo(50)
        assertThat(mapped.taskCount).isEqualTo(2)
    }

    @Test
    fun emptyWorkshopQueueDoesNotExposeGlobalEntryState() {
        assertThat(StartupUiAdapter.mapWorkshopTasks(emptyList()).status)
            .isEqualTo(TaskUiStatus.Idle)
        assertThat(StartupUiAdapter.mapWorkshopTasks(emptyList()).taskCount).isEqualTo(0)
    }
}
