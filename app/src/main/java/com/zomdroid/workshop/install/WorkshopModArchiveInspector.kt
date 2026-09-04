package com.zomdroid.workshop.install

import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

/**
 * Finds the directory names that InstallerService will use for a Workshop archive.
 *
 * This only reads the ZIP central directory. It does not extract or decompress the archive, so it
 * is safe to run as an install preflight even for multi-gigabyte downloads.
 */
object WorkshopModArchiveInspector {
    @JvmStatic
    fun findModRootNames(archive: File): List<String> {
        require(archive.isFile) { "Workshop archive is unavailable: ${archive.absolutePath}" }

        val candidates = linkedSetOf<String>()
        ZipFile(archive).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val parts = normalize(entry.name)?.split('/') ?: return@forEach
                if (parts.isEmpty()) return@forEach

                // InstallerService treats any directory containing mod.info as a mod root.
                if (parts.last().equals("mod.info", ignoreCase = true)) {
                    candidates += parts.dropLast(1).joinToString("/")
                }

                // It also recognizes media/common and version directories directly below a mod
                // root. Looking at every path segment handles archives without explicit directory
                // entries (many Workshop ZIPs omit them).
                for (index in parts.indices) {
                    if (isModRootMarker(parts[index])) {
                        candidates += parts.subList(0, index).joinToString("/")
                    }
                }
            }
        }

        // If a wrapper contains another marked directory, InstallerService stops at the first
        // marked ancestor. Keep only candidates that have no marked ancestor.
        val roots = candidates.filterTo(linkedSetOf()) { candidate ->
            candidates.none { other ->
                other != candidate && isAncestor(other, candidate)
            }
        }
        val archiveName = archive.name.substringBeforeLast('.', archive.name)
        return roots.map { root ->
            if (root.isEmpty()) archiveName else root.substringAfterLast('/')
        }.distinct()
    }

    @JvmStatic
    fun findExistingModNames(archive: File, modsDirectory: File): List<String> {
        if (!modsDirectory.isDirectory) return emptyList()
        return findModRootNames(archive).filter { modName ->
            File(modsDirectory, modName).exists()
        }
    }

    private fun normalize(rawName: String): String? {
        val normalized = rawName.replace('\\', '/').trim('/').removeSuffix("/")
        if (normalized.isEmpty()) return null
        if (normalized.split('/').any { it == ".." || it.isEmpty() }) return null
        return normalized
    }

    private fun isModRootMarker(name: String): Boolean {
        val lower = name.lowercase(Locale.US)
        return lower == "media" || lower == "common" || lower == "41" || lower == "42" ||
            lower.startsWith("41.") || lower.startsWith("42.")
    }

    private fun isAncestor(ancestor: String, path: String): Boolean =
        ancestor.isEmpty() || path.startsWith("$ancestor/")
}
