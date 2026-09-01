/* Persistent JSON store for the Workshop download center. */
package com.zomdroid.workshop.download

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

class DownloadCenterStore(
    private val file: File,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    },
) {
    @Volatile
    var lastLoadError: Throwable? = null
        private set

    @Synchronized
    fun load(): DownloadCenterSnapshot {
        if (!file.isFile) {
            lastLoadError = null
            return DownloadCenterSnapshot()
        }

        return try {
            val snapshot = json.decodeFromString<DownloadCenterSnapshot>(file.readText())
            require(snapshot.schemaVersion == 1) {
                "Unsupported download-center schema: ${snapshot.schemaVersion}"
            }
            lastLoadError = null
            snapshot
        } catch (error: SerializationException) {
            lastLoadError = error
            DownloadCenterSnapshot()
        } catch (error: IllegalArgumentException) {
            lastLoadError = error
            DownloadCenterSnapshot()
        }
    }

    @Synchronized
    fun save(tasks: List<DownloadCenterTask>) {
        file.parentFile?.let { parent ->
            require(parent.exists() || parent.mkdirs()) { "Cannot create download-center directory" }
        }
        val temporary = file.resolveSibling("${file.name}.tmp")
        val contents = json.encodeToString(DownloadCenterSnapshot(tasks = tasks))
        try {
            FileOutputStream(temporary).use { output ->
                output.write(contents.toByteArray(Charsets.UTF_8))
                output.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(
                    temporary.toPath(),
                    file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }
}
