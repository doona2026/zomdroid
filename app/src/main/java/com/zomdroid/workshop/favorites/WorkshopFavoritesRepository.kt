package com.zomdroid.workshop.favorites

import android.content.Context
import com.zomdroid.workshop.data.WorkshopBrowseItem
import java.io.File

class WorkshopFavoritesRepository(
    private val store: WorkshopFavoritesStore,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
) {
    constructor(context: Context) : this(
        WorkshopFavoritesStore(
            File(context.applicationContext.filesDir, "workshop/favorites.json"),
        ),
    )

    private var loadedSnapshot: WorkshopFavoritesSnapshot? = null

    @Synchronized
    fun snapshot(): WorkshopFavoritesSnapshot = currentSnapshot()

    @Synchronized
    fun list(): List<WorkshopFavorite> = currentSnapshot().favorites
        .sortedWith(compareByDescending<WorkshopFavorite> { it.favoritedAtEpochMillis }.thenBy { it.title })

    @Synchronized
    fun contains(appId: Long, publishedFileId: Long): Boolean = currentSnapshot().favorites.any {
        it.appId == appId && it.publishedFileId == publishedFileId
    }

    /** Returns true when the item is now favorited, false when it was removed. */
    @Synchronized
    fun toggle(item: WorkshopBrowseItem): Boolean = toggle(
        appId = item.appId.toLong(),
        publishedFileId = item.publishedFileId.toLong(),
        title = item.title,
        authorName = item.authorName,
        previewImageUrl = item.previewImageUrl,
        description = item.descriptionSnippet,
    )

    /** Java-friendly overload used by the Workshop list adapter. */
    @Synchronized
    fun toggle(
        appId: Long,
        publishedFileId: Long,
        title: String,
        authorName: String,
        previewImageUrl: String,
        description: String,
    ): Boolean {
        require(appId > 0 && publishedFileId > 0) { "Workshop identity must be positive" }
        val current = currentSnapshot().favorites
        val existing = current.any { it.appId == appId && it.publishedFileId == publishedFileId }
        val next = if (existing) {
            current.filterNot { it.appId == appId && it.publishedFileId == publishedFileId }
        } else {
            current + WorkshopFavorite(
                appId = appId,
                publishedFileId = publishedFileId,
                title = title,
                authorName = authorName,
                previewImageUrl = previewImageUrl,
                description = description,
                favoritedAtEpochMillis = nowEpochMillis(),
            )
        }
        publish(next)
        return !existing
    }

    @Synchronized
    fun remove(appId: Long, publishedFileId: Long): Boolean {
        val current = currentSnapshot().favorites
        val next = current.filterNot { it.appId == appId && it.publishedFileId == publishedFileId }
        if (next.size == current.size) return false
        publish(next)
        return true
    }

    private fun currentSnapshot(): WorkshopFavoritesSnapshot {
        loadedSnapshot?.let { return it }
        return store.load().also { loadedSnapshot = it }
    }

    private fun publish(favorites: List<WorkshopFavorite>) {
        val next = WorkshopFavoritesSnapshot(
            WorkshopFavoritesSnapshot.CURRENT_SCHEMA_VERSION,
            favorites,
        )
        store.save(next)
        loadedSnapshot = next
    }
}
