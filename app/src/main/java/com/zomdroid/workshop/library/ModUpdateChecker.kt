package com.zomdroid.workshop.library

data class ModUpdateResult(
    val entry: ModLibraryEntry,
    val latestUpdatedAtEpochSeconds: Long?,
    val updateAvailable: Boolean,
)

/** Manual/low-frequency checker; it never starts a download or installation by itself. */
class ModUpdateChecker(
    private val repository: ModLibraryRepository,
    private val nowEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val minimumIntervalMillis: Long = ModLibraryRepository.DEFAULT_CHECK_INTERVAL_MILLIS,
) {
    suspend fun check(
        entry: ModLibraryEntry,
        force: Boolean = false,
        loadLatestUpdatedAt: suspend (ModLibraryEntry) -> Long?,
    ): ModUpdateResult? {
        if (!force && !repository.needsUpdateCheck(entry, nowEpochMillis(), minimumIntervalMillis)) return null
        val latest = loadLatestUpdatedAt(entry)
        repository.markChecked(entry, nowEpochMillis())
        return ModUpdateResult(
            entry = entry,
            latestUpdatedAtEpochSeconds = latest,
            updateAvailable = latest != null &&
                (entry.updatedAtEpochSeconds == null || latest > entry.updatedAtEpochSeconds),
        )
    }
}
