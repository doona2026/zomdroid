package com.zomdroid.workshop.library

import android.content.Context
import com.zomdroid.workshop.WorkshopPaths
import com.zomdroid.workshop.data.WorkshopItemDetail
import com.zomdroid.workshop.download.DownloadCenterFile
import com.zomdroid.workshop.download.DownloadCenterTask
import java.io.File

class ModLibraryRepository(
    private val store: ModLibraryStore,
    private val completedRoot: File,
) {
    constructor(context: Context) : this(
        store = ModLibraryStore(File(context.applicationContext.filesDir, "workshop/mod-library.json")),
        completedRoot = WorkshopPaths.completedDownloadsRoot(),
    )

    fun snapshot(): ModLibrarySnapshot = store.load()

    fun entriesFor(appId: Long, publishedFileId: Long): List<ModLibraryEntry> = snapshot().entries
        .filter { it.appId == appId && it.publishedFileId == publishedFileId }
        .sortedWith(compareByDescending<ModLibraryEntry> { it.updatedAtEpochSeconds ?: Long.MIN_VALUE }.thenBy { it.versionKey })

    fun recordDetail(detail: WorkshopItemDetail, completedPath: File, files: List<DownloadCenterFile>): ModLibraryEntry =
        recordCompleted(
            appId = detail.appId.toLong(),
            publishedFileId = detail.publishedFileId.toLong(),
            title = detail.title,
            description = detail.description,
            previewUrl = detail.previewImageUrl,
            updatedAtEpochSeconds = detail.timeUpdatedEpochSeconds,
            completedPath = completedPath,
            files = files,
            source = "steam",
        )

    fun recordCompletedTask(
        task: DownloadCenterTask,
        completedPath: File,
        files: List<DownloadCenterFile>,
        metadataJson: String? = null,
    ): ModLibraryEntry = recordCompleted(
        appId = task.appId,
        publishedFileId = task.publishedFileId,
        title = task.title.orEmpty(),
        description = task.description.orEmpty(),
        previewUrl = task.previewUrl.orEmpty(),
        updatedAtEpochSeconds = task.updatedAtEpochSeconds
            ?: metadataJson?.let { TIME_UPDATED_PATTERN.find(it)?.groupValues?.get(1)?.toLongOrNull() },
        completedPath = completedPath,
        files = files,
        source = "steam",
    )

    fun recordCompleted(
        appId: Long,
        publishedFileId: Long,
        title: String,
        description: String = "",
        previewUrl: String = "",
        updatedAtEpochSeconds: Long? = null,
        completedPath: File,
        files: List<DownloadCenterFile>,
        source: String,
    ): ModLibraryEntry {
        require(appId > 0 && publishedFileId > 0) { "Workshop identity must be positive" }
        val safePath = validateCompletedPath(completedPath)
        val libraryFiles = files.map { ModLibraryFile(it.relativePath, it.sizeBytes, it.modifiedEpochMillis) }
        val candidate = ModVersionCandidate(updatedAtEpochSeconds, files = libraryFiles)
        val entry = ModLibraryEntry(
            appId = appId,
            publishedFileId = publishedFileId,
            title = title.ifBlank { "Workshop $publishedFileId" },
            description = description,
            previewUrl = previewUrl,
            updatedAtEpochSeconds = updatedAtEpochSeconds,
            versionKey = modVersionKey(candidate),
            completedPath = safePath.absolutePath,
            files = libraryFiles,
            source = source,
        )
        store.upsert(entry)
        return entry
    }

    fun markInstalled(entry: ModLibraryEntry, instanceName: String): ModLibraryEntry {
        require(instanceName.isNotBlank()) { "Instance name must not be blank" }
        val current = snapshot().entries.firstOrNull { it == entry } ?: entry
        val next = current.copy(installedInstances = (current.installedInstances + instanceName).distinct().sorted())
        store.upsert(next)
        return next
    }

    fun markChecked(entry: ModLibraryEntry, checkedAtEpochMillis: Long = System.currentTimeMillis()) {
        val current = snapshot().entries.firstOrNull { it == entry } ?: return
        store.upsert(current.copy(lastCheckedAtEpochMillis = checkedAtEpochMillis))
    }

    fun needsUpdateCheck(
        entry: ModLibraryEntry,
        nowEpochMillis: Long = System.currentTimeMillis(),
        minimumIntervalMillis: Long = DEFAULT_CHECK_INTERVAL_MILLIS,
    ): Boolean = entry.lastCheckedAtEpochMillis == null ||
        nowEpochMillis - entry.lastCheckedAtEpochMillis >= minimumIntervalMillis

    fun remove(entry: ModLibraryEntry, deleteCompletedFile: Boolean = true) {
        store.remove(entry)
        if (deleteCompletedFile && snapshot().entries.none { it.completedPath == entry.completedPath }) {
            deleteWithinRoot(File(entry.completedPath))
        }
    }

    fun removeByCompletedPath(path: String) {
        val matches = snapshot().entries.filter { it.completedPath == path }
        matches.forEach { store.remove(it) }
    }

    fun pruneOldVersions(appId: Long, publishedFileId: Long, keepVersionKey: String): Int {
        val old = entriesFor(appId, publishedFileId).filter { it.versionKey != keepVersionKey }
        old.forEach { remove(it) }
        return old.size
    }

    private fun validateCompletedPath(path: File): File {
        val root = completedRoot.canonicalFile
        val target = path.canonicalFile
        val prefix = root.path.trimEnd(File.separatorChar) + File.separator
        require(target.isFile && (target.path == root.path || target.path.startsWith(prefix))) {
            "Completed Mod file is outside the Workshop library"
        }
        return target
    }

    private fun deleteWithinRoot(path: File) {
        val root = completedRoot.canonicalFile
        val target = runCatching { path.canonicalFile }.getOrNull() ?: return
        val prefix = root.path.trimEnd(File.separatorChar) + File.separator
        if (target.path.startsWith(prefix)) target.delete()
    }

    companion object {
        const val DEFAULT_CHECK_INTERVAL_MILLIS = 6 * 60 * 60 * 1000L
        private val TIME_UPDATED_PATTERN = Regex("\\\"time_updated\\\"\\s*:\\s*(\\d+)")

        @JvmStatic
        fun forContext(context: Context) = ModLibraryRepository(context)
    }
}
