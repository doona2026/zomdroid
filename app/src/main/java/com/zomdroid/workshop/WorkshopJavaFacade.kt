/* Adapted from WorkshopAndroidDownloader (Apache-2.0); Java compatibility facade for Zomdroid. */
package com.zomdroid.workshop

import com.zomdroid.workshop.core.DownloadEvent
import com.zomdroid.workshop.core.DownloadedFileInfo
import com.zomdroid.workshop.core.WorkshopDownloadEngine
import com.zomdroid.workshop.core.WorkshopDownloadRequest
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Small Java-safe surface; queue persistence and foreground execution arrive in later stages. */
class WorkshopJavaFacade internal constructor(
    private val engine: WorkshopDownloadEngine,
    private val stagingRoot: File,
) {
    interface Listener {
        fun onItemStarted(publishedFileId: Long) {}
        fun onStateChanged(state: String)
        fun onProgress(writtenBytes: Long, totalBytes: Long)
        fun onCompleted(files: List<DownloadedFileInfo>)
        fun onFailed(message: String)
        fun onFinished()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap.newKeySet<Job>()

    /** Starts an anonymous official Steam download into app-private staging storage. */
    @JvmOverloads
    fun downloadAnonymous(
        publishedFileId: Long,
        listener: Listener? = null,
    ): Job = downloadAnonymous(listOf(publishedFileId), listener)

    /** Starts several anonymous official Steam downloads sequentially. */
    fun downloadAnonymous(
        publishedFileIds: List<Long>,
        listener: Listener? = null,
    ): Job {
        require(publishedFileIds.isNotEmpty()) { "publishedFileIds must not be empty" }
        require(publishedFileIds.all { it > 0 }) { "publishedFileIds must be positive" }
        val job = scope.launch {
            try {
                for (publishedFileId in publishedFileIds) {
                    listener?.onItemStarted(publishedFileId)
                    engine.download(
                        WorkshopDownloadRequest(
                            appId = WorkshopAppContract.PROJECT_ZOMBOID_STEAM_APP_ID.toUInt(),
                            publishedFileId = publishedFileId.toULong(),
                            outputDir = File(stagingRoot, publishedFileId.toString()),
                        ),
                    ).collect { event ->
                        when (event) {
                            is DownloadEvent.StateChanged -> listener?.onStateChanged(event.state.name)
                            is DownloadEvent.Progress -> listener?.onProgress(
                                event.writtenBytes,
                                event.totalBytes ?: -1L,
                            )
                            is DownloadEvent.Completed -> listener?.onCompleted(event.files)
                            is DownloadEvent.Failed -> listener?.onFailed(event.message)
                            is DownloadEvent.FileCompleted,
                            is DownloadEvent.LogAppended,
                            -> Unit
                        }
                    }
                }
            } finally {
                listener?.onFinished()
            }
        }
        activeJobs += job
        job.invokeOnCompletion { activeJobs -= job }
        return job
    }

    fun cancel(job: Job) {
        job.cancel()
    }

    fun cancelAll() {
        activeJobs.toList().forEach(Job::cancel)
    }

    fun close() {
        cancelAll()
        scope.coroutineContext[Job]?.cancel()
    }
}
