package com.zomdroid.workshop.favorites

import kotlinx.serialization.Serializable

@Serializable
data class WorkshopFavorite(
    val appId: Long,
    val publishedFileId: Long,
    val title: String,
    val authorName: String = "",
    val previewImageUrl: String = "",
    val description: String = "",
    val favoritedAtEpochMillis: Long,
)

@Serializable
data class WorkshopFavoritesSnapshot(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val favorites: List<WorkshopFavorite> = emptyList(),
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}
