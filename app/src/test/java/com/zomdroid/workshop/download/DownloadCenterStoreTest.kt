package com.zomdroid.workshop.download

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCenterStoreTest {
    @Test
    fun `tasks survive a store round trip`() {
        val file = Files.createTempDirectory("download-center-store").resolve("tasks.json").toFile()
        val store = DownloadCenterStore(file)
        val task = DownloadCenterTask(
            id = "task-1",
            appId = 108600,
            publishedFileId = 12345,
            title = "Example mod",
            state = DownloadCenterTaskState.Paused,
            phase = "Downloading",
            writtenBytes = 42,
            totalBytes = 100,
            logs = listOf("started", "paused"),
        )

        store.save(listOf(task))

        assertEquals(listOf(task), DownloadCenterStore(file).load().tasks)
        assertTrue(file.isFile)
        assertTrue(!file.resolveSibling("tasks.json.tmp").exists())
    }

    @Test
    fun `corrupt json recovers as an empty queue and records the error`() {
        val file = Files.createTempDirectory("download-center-corrupt").resolve("tasks.json").toFile()
        file.writeText("{not valid json")

        val store = DownloadCenterStore(file)

        assertTrue(store.load().tasks.isEmpty())
        assertNotNull(store.lastLoadError)
    }
}
