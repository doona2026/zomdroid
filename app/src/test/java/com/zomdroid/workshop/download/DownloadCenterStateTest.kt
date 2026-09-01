package com.zomdroid.workshop.download

import com.zomdroid.workshop.core.DownloadEvent
import com.zomdroid.workshop.core.DownloadedFileInfo
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadCenterStateTest {
    @Test
    fun `queued task runs to success and persists progress and archive`() = runBlocking {
        val root = Files.createTempDirectory("download-center-state").toFile()
        val runner = FakeRunner()
        val manager = manager(root, runner)
        val task = manager.enqueue(108600, 12345, "Example mod")

        manager.start()
        manager.awaitIdle()

        val saved = manager.tasks.value.single()
        assertEquals(task.id, saved.id)
        assertEquals(DownloadCenterTaskState.Success, saved.state)
        assertEquals(100L, saved.writtenBytes)
        assertTrue(saved.outputPath?.let(::File)?.isFile == true)
        assertTrue(saved.outputPath?.let(::File)?.name?.startsWith("Example mod [12345]_") == true)
        assertTrue(!root.resolve("staging").resolve(task.id).exists())
        assertEquals(listOf("media/mod.info"), saved.files.map { it.relativePath })
    }

    @Test
    fun `paused task resumes and failed task retries`() = runBlocking {
        val root = Files.createTempDirectory("download-center-transitions").toFile()
        val runner = FakeRunner(failFirstRun = true)
        val manager = manager(root, runner)
        val paused = manager.enqueue(108600, 1)
        manager.pause(paused.id)
        assertEquals(DownloadCenterTaskState.Paused, manager.tasks.value.single().state)

        manager.resume(paused.id)
        manager.start()
        manager.awaitIdle()
        assertEquals(DownloadCenterTaskState.Failed, manager.tasks.value.single().state)

        manager.retry(paused.id)
        manager.awaitIdle()
        assertEquals(DownloadCenterTaskState.Success, manager.tasks.value.single().state)
        assertEquals(2, runner.calls)
    }

    @Test
    fun `failed task keeps staging for a later retry`() = runBlocking {
        val root = Files.createTempDirectory("download-center-failed-staging").toFile()
        val manager = manager(root, FailingAfterWritingRunner())
        val task = manager.enqueue(108600, 55, "Failed mod")

        manager.start()
        manager.awaitIdle()

        assertEquals(DownloadCenterTaskState.Failed, manager.tasks.value.single().state)
        assertTrue(root.resolve("staging").resolve(task.id).resolve("partial.bin").isFile)
    }

    @Test
    fun `running tasks are requeued when a manager is recreated`() = runBlocking {
        val root = Files.createTempDirectory("download-center-restart").toFile()
        val file = root.resolve("tasks.json")
        val interrupted = DownloadCenterTask(
            id = "interrupted",
            appId = 108600,
            publishedFileId = 99,
            state = DownloadCenterTaskState.Running,
        )
        DownloadCenterStore(file).save(listOf(interrupted))

        val manager = DownloadCenterManager(
            store = DownloadCenterStore(file),
            runner = FakeRunner(),
            stagingRoot = root.resolve("staging"),
            completionRoot = root.resolve("completed"),
            maxConcurrentTasks = 1,
        )
        manager.start()
        manager.awaitIdle()

        assertEquals(DownloadCenterTaskState.Success, manager.tasks.value.single().state)
    }

    @Test
    fun `startup removes staging for previously successful tasks only`() {
        val root = Files.createTempDirectory("download-center-startup-cleanup").toFile()
        val success = DownloadCenterTask(
            id = "success",
            appId = 108600,
            publishedFileId = 100,
            state = DownloadCenterTaskState.Success,
        )
        val failed = success.copy(id = "failed", publishedFileId = 101, state = DownloadCenterTaskState.Failed)
        DownloadCenterStore(root.resolve("tasks.json")).save(listOf(success, failed))
        root.resolve("staging/success/old.bin").apply { parentFile.mkdirs(); writeText("old") }
        root.resolve("staging/failed/partial.bin").apply { parentFile.mkdirs(); writeText("partial") }

        val manager = DownloadCenterManager(
            store = DownloadCenterStore(root.resolve("tasks.json")),
            runner = FakeRunner(),
            stagingRoot = root.resolve("staging"),
            completionRoot = root.resolve("completed"),
            maxConcurrentTasks = 1,
        )
        manager.start()

        assertTrue(!root.resolve("staging/success").exists())
        assertTrue(root.resolve("staging/failed/partial.bin").isFile)
    }

    @Test
    fun `manager enforces the configured concurrency limit`() = runBlocking {
        val root = Files.createTempDirectory("download-center-concurrency").toFile()
        val runner = FakeRunner()
        val manager = DownloadCenterManager(
            store = DownloadCenterStore(root.resolve("tasks.json")),
            runner = runner,
            stagingRoot = root.resolve("staging"),
            completionRoot = root.resolve("completed"),
            maxConcurrentTasks = 1,
        )
        manager.enqueue(108600, 1)
        manager.enqueue(108600, 2)

        manager.start()
        manager.awaitIdle()

        assertEquals(1, runner.maxConcurrent)
        assertTrue(manager.tasks.value.all { it.state == DownloadCenterTaskState.Success })
    }

    @Test
    fun `cancel and delete remove a queued task`() {
        val root = Files.createTempDirectory("download-center-delete").toFile()
        val manager = manager(root, FakeRunner())
        val task = manager.enqueue(108600, 7)

        manager.cancel(task.id)
        assertEquals(DownloadCenterTaskState.Cancelled, manager.tasks.value.single().state)

        manager.delete(task.id)
        assertTrue(manager.tasks.value.isEmpty())
        assertTrue(!root.resolve("staging").resolve(task.id).exists())
    }

    private fun manager(root: File, runner: DownloadCenterRunner) = DownloadCenterManager(
        store = DownloadCenterStore(root.resolve("tasks.json")),
        runner = runner,
        stagingRoot = root.resolve("staging"),
        completionRoot = root.resolve("completed"),
        maxConcurrentTasks = 1,
    )

    private class FakeRunner(private val failFirstRun: Boolean = false) : DownloadCenterRunner {
        var calls = 0
        var concurrent = 0
        var maxConcurrent = 0

        override suspend fun run(
            task: DownloadCenterTask,
            outputDir: File,
            emit: suspend (DownloadEvent) -> Unit,
        ): List<DownloadedFileInfo> {
            calls++
            if (failFirstRun && calls == 1) error("simulated failure")
            synchronized(this) {
                concurrent++
                maxConcurrent = maxOf(maxConcurrent, concurrent)
            }
            val file = outputDir.resolve("media/mod.info").apply {
                parentFile?.mkdirs()
                writeText("name=Example")
            }
            try {
                emit(DownloadEvent.StateChanged(com.zomdroid.workshop.core.DownloadState.Downloading))
                emit(DownloadEvent.Progress(100, 100))
                delay(5)
                return listOf(
                    DownloadedFileInfo(
                        relativePath = "media/mod.info",
                        sizeBytes = file.length(),
                        modifiedEpochMillis = file.lastModified(),
                    ),
                )
            } finally {
                synchronized(this) { concurrent-- }
            }
        }
    }

    private class FailingAfterWritingRunner : DownloadCenterRunner {
        override suspend fun run(
            task: DownloadCenterTask,
            outputDir: File,
            emit: suspend (DownloadEvent) -> Unit,
        ): List<DownloadedFileInfo> {
            outputDir.mkdirs()
            outputDir.resolve("partial.bin").writeText("partial")
            error("simulated failure after staging")
        }
    }
}
