package com.zomdroid.workshop.library

import com.google.common.truth.Truth.assertThat
import java.nio.file.Files
import org.junit.Test

class ModLibraryStoreTest {
    @Test
    fun storeRoundTripsAndMigratesLegacyArrayFormat() {
        val dir = Files.createTempDirectory("mod-library").toFile()
        val file = dir.resolve("library.json")
        val entry = ModLibraryEntry(108600, 123, "Test", versionKey = "v1", completedPath = "/tmp/test.zip")
        val store = ModLibraryStore(file)
        store.save(ModLibrarySnapshot(entries = listOf(entry)))
        assertThat(store.load().entries).containsExactly(entry)

        file.writeText("[{\"appId\":108600,\"publishedFileId\":123,\"title\":\"Test\",\"versionKey\":\"v1\",\"completedPath\":\"/tmp/test.zip\"}]")
        assertThat(store.load().schemaVersion).isEqualTo(ModLibrarySnapshot.CURRENT_SCHEMA_VERSION)
        assertThat(store.load().entries).containsExactly(entry)
    }

    @Test
    fun upsertKeepsVersionsButRemoveCleansDuplicateFileOnlyOnce() {
        val dir = Files.createTempDirectory("mod-library").toFile()
        val completedRoot = dir.resolve("completed").apply { mkdirs() }
        val archive = completedRoot.resolve("mod.zip").apply { writeText("zip") }
        val store = ModLibraryStore(dir.resolve("library.json"))
        val repository = ModLibraryRepository(store, completedRoot)
        val first = repository.recordCompleted(108600, 123, "Test", completedPath = archive, files = emptyList(), source = "steam")
        val second = first.copy(versionKey = "v2")
        store.upsert(second)

        repository.remove(first)
        assertThat(archive.exists()).isTrue()
        repository.remove(second)
        assertThat(archive.exists()).isFalse()
    }
}
