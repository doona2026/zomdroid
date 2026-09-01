package com.zomdroid.workshop.library

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

/** Atomic JSON store for completed Workshop files and their version history. */
class ModLibraryStore(
    private val file: File,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {
    @Synchronized
    fun load(): ModLibrarySnapshot {
        if (!file.isFile) return ModLibrarySnapshot()
        val raw = file.readText()
        return runCatching { json.decodeFromString<ModLibrarySnapshot>(raw) }.getOrElse {
            // v1 stored the entry array directly. Promote it to the current envelope on read.
            runCatching { json.decodeFromString(ListSerializer(ModLibraryEntry.serializer()), raw) }
                .map { entries -> ModLibrarySnapshot(CURRENT_SCHEMA_VERSION, entries) }
                .getOrElse { ModLibrarySnapshot() }
        }
    }

    @Synchronized
    fun save(snapshot: ModLibrarySnapshot) {
        file.parentFile?.let { require(it.exists() || it.mkdirs()) { "Cannot create Mod library directory" } }
        val temporary = File(file.parentFile ?: File("."), "${file.name}.part")
        temporary.writeText(json.encodeToString(ModLibrarySnapshot(CURRENT_SCHEMA_VERSION, snapshot.entries)))
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
            throw IllegalStateException("Cannot publish Mod library store", error)
        }
    }

    @Synchronized
    fun upsert(entry: ModLibraryEntry): ModLibrarySnapshot {
        val current = load()
        val nextEntries = current.entries.filterNot {
            it.appId == entry.appId &&
                it.publishedFileId == entry.publishedFileId &&
                it.versionKey == entry.versionKey
        } + entry
        return ModLibrarySnapshot(ModLibrarySnapshot.CURRENT_SCHEMA_VERSION, nextEntries).also(::save)
    }

    @Synchronized
    fun remove(entry: ModLibraryEntry): ModLibrarySnapshot {
        val current = load()
        return ModLibrarySnapshot(
            ModLibrarySnapshot.CURRENT_SCHEMA_VERSION,
            current.entries.filterNot { it == entry },
        ).also(::save)
    }

    private companion object {
        const val CURRENT_SCHEMA_VERSION = ModLibrarySnapshot.CURRENT_SCHEMA_VERSION
    }
}
