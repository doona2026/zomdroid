package com.zomdroid.workshop.favorites

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Atomic local JSON store for Workshop favorites. */
class WorkshopFavoritesStore(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    @Synchronized
    fun load(): WorkshopFavoritesSnapshot {
        if (!file.isFile) return WorkshopFavoritesSnapshot()
        return runCatching {
            json.decodeFromString<WorkshopFavoritesSnapshot>(file.readText())
        }.getOrDefault(WorkshopFavoritesSnapshot())
    }

    @Synchronized
    fun save(snapshot: WorkshopFavoritesSnapshot) {
        file.parentFile?.let {
            require(it.exists() || it.mkdirs()) { "Cannot create Workshop favorites directory" }
        }
        val temporary = File(file.parentFile ?: File("."), "${file.name}.part")
        temporary.writeText(json.encodeToString(
            WorkshopFavoritesSnapshot(
                WorkshopFavoritesSnapshot.CURRENT_SCHEMA_VERSION,
                snapshot.favorites,
            ),
        ))
        try {
            Files.move(
                temporary.toPath(),
                file.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } catch (error: Exception) {
            temporary.delete()
            throw IllegalStateException("Cannot publish Workshop favorites store", error)
        }
    }
}
