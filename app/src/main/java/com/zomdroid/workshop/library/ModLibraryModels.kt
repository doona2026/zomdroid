package com.zomdroid.workshop.library

import kotlinx.serialization.Serializable

@Serializable
data class ModLibraryFile(
    val relativePath: String,
    val sizeBytes: Long,
    val modifiedEpochMillis: Long,
)

@Serializable
data class ModLibraryEntry(
    val appId: Long,
    val publishedFileId: Long,
    val title: String,
    val description: String = "",
    val previewUrl: String = "",
    val updatedAtEpochSeconds: Long? = null,
    val versionKey: String,
    val completedPath: String,
    val files: List<ModLibraryFile> = emptyList(),
    val source: String = "steam",
    val installedInstances: List<String> = emptyList(),
    val lastCheckedAtEpochMillis: Long? = null,
)

@Serializable
data class ModLibrarySnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val entries: List<ModLibraryEntry> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 2
    }
}
