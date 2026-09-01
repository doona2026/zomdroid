/* Persistent queue coordinator for Workshop downloads. */
package com.zomdroid.workshop.download

import android.content.Context
import com.zomdroid.workshop.WorkshopFileAccess
import com.zomdroid.workshop.WorkshopPaths
import com.zomdroid.workshop.WorkshopRuntime
import com.zomdroid.workshop.core.DownloadEvent
import com.zomdroid.workshop.core.DownloadedFileInfo
import com.zomdroid.workshop.core.WorkshopDownloadEngine
import com.zomdroid.workshop.core.WorkshopDownloadException
import com.zomdroid.workshop.core.WorkshopDownloadRequest
import com.zomdroid.workshop.library.ModLibraryRepository
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.min
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

interface DownloadCenterRunner {
    suspend fun run(
        task: DownloadCenterTask,
        outputDir: File,
        emit: suspend (DownloadEvent) -> Unit,
    ): List<DownloadedFileInfo>
}

fun interface DownloadCenterTaskObserver {
    fun onTasksChanged(tasks: List<DownloadCenterTask>)
}

/** Owns queue state, persistence, worker cancellation and restart recovery. */
class DownloadCenterManager(
    private val store: DownloadCenterStore,
    private val runner: DownloadCenterRunner,
    private val stagingRoot: File,
    private val completionRoot: File,
    private val maxConcurrentTasks: Int = 2,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val libraryRepository: ModLibraryRepository? = null,
) {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val _tasks = MutableStateFlow(store.load().tasks)
    val tasks: StateFlow<List<DownloadCenterTask>> = _tasks.asStateFlow()

    init {
        require(maxConcurrentTasks > 0) { "maxConcurrentTasks must be positive" }
    }

    fun start() {
        val completedTaskIds: List<String>
        synchronized(lock) {
            if (started) return
            val recovered = _tasks.value.map { task ->
                if (task.state == DownloadCenterTaskState.Running) {
                    task.copy(
                        state = DownloadCenterTaskState.Queued,
                        phase = "Queued",
                        updatedAtMillis = clock(),
                    )
                } else {
                    task
                }
            }
            if (recovered != _tasks.value) publishLocked(recovered)
            completedTaskIds = recovered
                .filter { it.state == DownloadCenterTaskState.Success }
                .map(DownloadCenterTask::id)
            started = true
        }
        completedTaskIds.forEach { deleteWithin(stagingRoot, File(stagingRoot, it)) }
        pump()
    }

    /** Java-friendly observation bridge; callers own and cancel the returned Job. */
    fun observe(observer: DownloadCenterTaskObserver): Job =
        scope.launch { tasks.collect { observer.onTasksChanged(it) } }

    /** Stops workers without marking Running tasks as failed; the next start requeues them. */
    fun stop() {
        val jobs = synchronized(lock) {
            started = false
            activeJobs.values.toList()
        }
        jobs.forEach(Job::cancel)
    }

    fun enqueue(
        appId: Long,
        publishedFileId: Long,
        title: String? = null,
        accountId: String? = null,
    ): DownloadCenterTask = enqueueInternal(appId, publishedFileId, title, accountId, null, null)

    fun enqueueForInstance(
        appId: Long,
        publishedFileId: Long,
        title: String?,
        accountId: String?,
        targetInstanceName: String?,
        targetBuildVersion: String?,
    ): DownloadCenterTask = enqueueInternal(appId, publishedFileId, title, accountId, targetInstanceName, targetBuildVersion)

    fun enqueueForInstanceWithMetadata(
        appId: Long,
        publishedFileId: Long,
        title: String?,
        description: String?,
        previewUrl: String?,
        updatedAtEpochSeconds: Long?,
        accountId: String?,
        targetInstanceName: String?,
        targetBuildVersion: String?,
    ): DownloadCenterTask = enqueueInternal(
        appId, publishedFileId, title, accountId, targetInstanceName, targetBuildVersion,
        description, previewUrl, updatedAtEpochSeconds,
    )

    private fun enqueueInternal(
        appId: Long,
        publishedFileId: Long,
        title: String?,
        accountId: String?,
        targetInstanceName: String?,
        targetBuildVersion: String?,
        description: String? = null,
        previewUrl: String? = null,
        updatedAtEpochSeconds: Long? = null,
    ): DownloadCenterTask {
        require(appId > 0) { "appId must be positive" }
        require(publishedFileId > 0) { "publishedFileId must be positive" }
        val task = DownloadCenterTask(
            id = UUID.randomUUID().toString(),
            appId = appId,
            publishedFileId = publishedFileId,
            title = title,
            description = description,
            previewUrl = previewUrl,
            updatedAtEpochSeconds = updatedAtEpochSeconds,
            accountId = accountId,
            targetInstanceName = targetInstanceName,
            targetBuildVersion = targetBuildVersion,
        )
        synchronized(lock) {
            publishLocked(_tasks.value + task)
        }
        pump()
        return task
    }

    fun pause(taskId: String) {
        val job = synchronized(lock) {
            val task = findLocked(taskId) ?: return
            if (task.state != DownloadCenterTaskState.Queued
                && task.state != DownloadCenterTaskState.Running
            ) return
            replaceLocked(taskId) {
                it.copy(state = DownloadCenterTaskState.Paused, phase = "Paused", errorMessage = null)
            }
            activeJobs[taskId]
        }
        job?.cancel()
        pump()
    }

    fun resume(taskId: String) {
        synchronized(lock) {
            val task = findLocked(taskId) ?: return
            if (task.state != DownloadCenterTaskState.Paused) return
            replaceLocked(taskId) {
                it.copy(state = DownloadCenterTaskState.Queued, phase = "Queued", errorMessage = null)
            }
        }
        pump()
    }

    fun retry(taskId: String) {
        synchronized(lock) {
            val task = findLocked(taskId) ?: return
            if (task.state != DownloadCenterTaskState.Failed
                && task.state != DownloadCenterTaskState.Cancelled
            ) return
            replaceLocked(taskId) {
                it.copy(
                    state = DownloadCenterTaskState.Queued,
                    phase = "Queued",
                    writtenBytes = 0L,
                    totalBytes = null,
                    errorMessage = null,
                    files = emptyList(),
                    outputPath = null,
                )
            }
        }
        pump()
    }

    fun cancel(taskId: String) {
        val job = synchronized(lock) {
            val task = findLocked(taskId) ?: return
            if (task.state == DownloadCenterTaskState.Success
                || task.state == DownloadCenterTaskState.Cancelled
            ) return
            replaceLocked(taskId) {
                it.copy(state = DownloadCenterTaskState.Cancelled, phase = "Cancelled")
            }
            activeJobs[taskId]
        }
        job?.cancel()
        pump()
    }

    fun delete(taskId: String) {
        val deletedTask = synchronized(lock) {
            val task = findLocked(taskId) ?: return
            activeJobs[taskId]?.cancel()
            publishLocked(_tasks.value.filterNot { it.id == taskId })
            task
        }
        deleteWithin(stagingRoot, File(stagingRoot, deletedTask.id))
        deletedTask.outputPath?.let { deleteWithin(completionRoot, File(it)) }
        deletedTask.outputPath?.let { libraryRepository?.removeByCompletedPath(it) }
        pump()
    }

    suspend fun awaitIdle(timeoutMillis: Long = 10_000L) {
        withTimeout(timeoutMillis) {
            while (true) {
                val idle = synchronized(lock) { activeJobs.isEmpty() }
                if (idle) return@withTimeout
                delay(10L)
            }
        }
    }

    private var started = false

    private fun pump() {
        synchronized(lock) {
            if (!started) return
            while (activeJobs.size < maxConcurrentTasks) {
                val task = _tasks.value.firstOrNull {
                    it.state == DownloadCenterTaskState.Queued && !activeJobs.containsKey(it.id)
                } ?: break
                replaceLocked(task.id) {
                    it.copy(state = DownloadCenterTaskState.Running, phase = "Starting")
                }
                activeJobs[task.id] = scope.launch { runTask(task.id) }
            }
        }
    }

    private suspend fun runTask(taskId: String) {
        val task = synchronized(lock) { findLocked(taskId) } ?: return
        val outputDir = File(stagingRoot, task.id)
        try {
            val files = runner.run(task, outputDir) { event -> handleEvent(taskId, event) }
            val archive = File(
                completionRoot,
                WorkshopArchiveNaming.forWorkshop(
                    task.publishedFileId,
                    task.title,
                    task.createdAtMillis,
                ),
            )
            WorkshopFileAccess.exportCompletedZip(
                outputDir = outputDir,
                files = files,
                destination = archive,
            )
            libraryRepository?.recordCompletedTask(
                task = task,
                completedPath = archive,
                files = files.map(::toCenterFile),
                metadataJson = File(outputDir, "metadata.json").takeIf(File::isFile)?.readText(),
            )
            val completed = synchronized(lock) {
                val current = findLocked(taskId)
                if (current?.state == DownloadCenterTaskState.Running) {
                    replaceLocked(taskId) {
                        it.copy(
                            state = DownloadCenterTaskState.Success,
                            phase = "Success",
                            errorMessage = null,
                            files = files.map(::toCenterFile),
                            outputPath = archive.absolutePath,
                        )
                    }
                    true
                } else {
                    false
                }
            }
            if (completed) deleteWithin(stagingRoot, outputDir)
        } catch (_: CancellationException) {
            // Pause/cancel/stop already wrote the intended persistent state.
        } catch (error: Throwable) {
            synchronized(lock) {
                val current = findLocked(taskId)
                if (current?.state == DownloadCenterTaskState.Running) {
                    replaceLocked(taskId) {
                        it.copy(
                            state = DownloadCenterTaskState.Failed,
                            phase = "Failed",
                            errorMessage = error.message ?: error.javaClass.simpleName,
                        )
                    }
                }
            }
        } finally {
            activeJobs.remove(taskId)
            pump()
        }
    }

    private suspend fun handleEvent(taskId: String, event: DownloadEvent) {
        synchronized(lock) {
            when (event) {
                is DownloadEvent.StateChanged -> replaceLocked(taskId) {
                    it.copy(phase = event.state.name)
                }
                is DownloadEvent.Progress -> replaceLocked(taskId) {
                    it.copy(
                        writtenBytes = event.writtenBytes,
                        totalBytes = event.totalBytes?.takeIf { total -> total >= 0L },
                    )
                }
                is DownloadEvent.LogAppended -> replaceLocked(taskId) {
                    it.copy(logs = (it.logs + event.line).takeLast(MAX_LOG_LINES))
                }
                is DownloadEvent.Failed -> replaceLocked(taskId) {
                    it.copy(errorMessage = event.message)
                }
                is DownloadEvent.FileCompleted,
                is DownloadEvent.Completed,
                -> Unit
            }
        }
    }

    private fun findLocked(taskId: String): DownloadCenterTask? =
        _tasks.value.firstOrNull { it.id == taskId }

    private fun replaceLocked(taskId: String, transform: (DownloadCenterTask) -> DownloadCenterTask) {
        val updated = _tasks.value.map { task ->
            if (task.id == taskId) transform(task).copy(updatedAtMillis = clock()) else task
        }
        publishLocked(updated)
    }

    private fun publishLocked(tasks: List<DownloadCenterTask>) {
        store.save(tasks)
        _tasks.value = tasks
    }

    private fun toCenterFile(file: DownloadedFileInfo) = DownloadCenterFile(
        relativePath = file.relativePath,
        sizeBytes = file.sizeBytes,
        modifiedEpochMillis = file.modifiedEpochMillis,
    )

    private fun deleteWithin(root: File, target: File) {
        val rootPath = root.canonicalFile
        val targetPath = target.canonicalFile
        val prefix = rootPath.path.trimEnd(File.separatorChar) + File.separator
        if (targetPath.path.startsWith(prefix)) targetPath.deleteRecursively()
    }

    companion object {
        private const val MAX_LOG_LINES = 200

        fun forContext(context: Context): DownloadCenterManager {
            val appContext = context.applicationContext
            return DownloadCenterManager(
                store = DownloadCenterStore(
                    File(appContext.filesDir, "workshop/download-center.json"),
                ),
                runner = CoreWorkshopDownloadRunner(appContext),
                stagingRoot = File(appContext.filesDir, "workshop/download-center-staging"),
                completionRoot = WorkshopPaths.completedDownloadsRoot(),
                libraryRepository = ModLibraryRepository(appContext),
            )
        }
    }
}

private class CoreWorkshopDownloadRunner(
    private val context: Context,
) : DownloadCenterRunner {
    override suspend fun run(
        task: DownloadCenterTask,
        outputDir: File,
        emit: suspend (DownloadEvent) -> Unit,
    ): List<DownloadedFileInfo> {
        WorkshopRuntime.initialize(context)
        var files: List<DownloadedFileInfo>? = null
        WorkshopRuntime.requireEngine(context, task.accountId)
            .download(
                WorkshopDownloadRequest(
                    appId = task.appId.toUInt(),
                    publishedFileId = task.publishedFileId.toULong(),
                    outputDir = outputDir,
                ),
            ).collect { event ->
                emit(event)
                if (event is DownloadEvent.Completed) files = event.files
                if (event is DownloadEvent.Failed) {
                    throw WorkshopDownloadException(event.message)
                }
            }
        return files ?: throw WorkshopDownloadException("Workshop download did not complete")
    }
}

object DownloadCenterManagerProvider {
    @Volatile
    private var instance: DownloadCenterManager? = null

    @JvmStatic
    @Synchronized
    fun get(context: Context): DownloadCenterManager {
        return instance ?: DownloadCenterManager.forContext(context).also { instance = it }
    }
}
