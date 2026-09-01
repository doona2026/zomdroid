package com.zomdroid.workshop.thirdparty

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.zomdroid.workshop.WorkshopPaths
import com.zomdroid.workshop.download.DownloadCenterTask
import com.zomdroid.workshop.library.ModLibraryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

object GgntwFallbackRuntime {
    interface Callback {
        fun onSuccess()
        fun onError(message: String)
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    @JvmStatic
    fun download(context: Context, task: DownloadCenterTask, callback: Callback) {
        scope.launch {
            runCatching {
                GgntwFallbackClient().downloadToLibrary(
                    workshopId = task.publishedFileId,
                    title = task.title.orEmpty(),
                    description = task.description.orEmpty(),
                    previewUrl = task.previewUrl.orEmpty(),
                    updatedAtEpochSeconds = task.updatedAtEpochSeconds,
                    stagingRoot = File(
                        WorkshopPaths.privateStagingRoot(context.applicationContext),
                        "ggntw",
                    ),
                    library = ModLibraryRepository(context.applicationContext),
                    destinationRoot = WorkshopPaths.completedDownloadsRoot(),
                )
            }.onSuccess { main.post { callback.onSuccess() } }
                .onFailure { error -> main.post { callback.onError(error.message ?: "Third-party download failed") } }
        }
    }
}
