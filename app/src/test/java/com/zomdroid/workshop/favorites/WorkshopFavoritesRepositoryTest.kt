package com.zomdroid.workshop.favorites

import com.google.common.truth.Truth.assertThat
import com.zomdroid.workshop.data.WorkshopBrowseItem
import java.nio.file.Files
import org.junit.Test

class WorkshopFavoritesRepositoryTest {
    @Test
    fun togglePersistsSnapshotAndUpdatesMetadataOnReAdd() {
        val dir = Files.createTempDirectory("workshop-favorites").toFile()
        val file = dir.resolve("favorites.json")
        val item = WorkshopBrowseItem(108600u, 42uL, "Test mod", "Author", "https://example/one.png", "Description")
        val repository = WorkshopFavoritesRepository(WorkshopFavoritesStore(file)) { 100L }

        assertThat(repository.toggle(item)).isTrue()
        assertThat(repository.contains(108600, 42)).isTrue()
        assertThat(WorkshopFavoritesRepository(WorkshopFavoritesStore(file)).list().single().title)
            .isEqualTo("Test mod")

        assertThat(repository.toggle(item.copy(title = "Renamed mod", previewImageUrl = "https://example/two.png")))
            .isFalse()
        assertThat(repository.toggle(item.copy(title = "Renamed mod", previewImageUrl = "https://example/two.png")))
            .isTrue()
        val favorite = repository.list().single()
        assertThat(favorite.title).isEqualTo("Renamed mod")
        assertThat(favorite.previewImageUrl).isEqualTo("https://example/two.png")
        assertThat(favorite.favoritedAtEpochMillis).isEqualTo(100L)
    }

    @Test
    fun removeIsIdempotentAndCorruptStoreBehavesAsEmpty() {
        val dir = Files.createTempDirectory("workshop-favorites-corrupt").toFile()
        val file = dir.resolve("favorites.json").apply { writeText("not json") }
        val repository = WorkshopFavoritesRepository(WorkshopFavoritesStore(file)) { 200L }

        assertThat(repository.list()).isEmpty()
        assertThat(repository.remove(108600, 42)).isFalse()
        assertThat(repository.toggle(108600, 42, "Test", "Author", "", "")).isTrue()
        assertThat(repository.remove(108600, 42)).isTrue()
        assertThat(repository.remove(108600, 42)).isFalse()
    }
}
