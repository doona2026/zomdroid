/* Persistent download-center models for Zomdroid. */
package com.zomdroid.workshop.download

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadCenterTaskState {
    Queued,
    Running,
    Paused,
    Success,
    Failed,
    Cancelled,
}

@Serializable
data class DownloadCenterFile(
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedEpochMillis: Long,
)

@Serializable
data class DownloadCenterTask(
    val id: String,
    val appId: Long,
    val publishedFileId: Long,
    val title: String? = null,
    val description: String? = null,
    val previewUrl: String? = null,
    val updatedAtEpochSeconds: Long? = null,
    val accountId: String? = null,
    val targetInstanceName: String? = null,
    val targetBuildVersion: String? = null,
    val state: DownloadCenterTaskState = DownloadCenterTaskState.Queued,
    val phase: String = "Queued",
    val writtenBytes: Long = 0L,
    val totalBytes: Long? = null,
    val errorMessage: String? = null,
    val logs: List<String> = emptyList(),
    val files: List<DownloadCenterFile> = emptyList(),
    val outputPath: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis,
)

@Serializable
data class DownloadCenterSnapshot(
    val schemaVersion: Int = 1,
    val tasks: List<DownloadCenterTask> = emptyList(),
)
