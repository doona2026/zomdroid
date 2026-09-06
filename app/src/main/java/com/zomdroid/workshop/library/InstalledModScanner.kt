package com.zomdroid.workshop.library

import java.io.File
import java.nio.file.Files

class InstalledModScanner {
    fun scan(instanceName: String, instanceHomePath: String): InstalledModScanResult =
        scan(instanceName, File(instanceHomePath))

    fun scan(instanceName: String, instanceHome: File): InstalledModScanResult {
        val modsDirectory = runCatching { File(instanceHome, "Zomboid/mods").canonicalFile }.getOrElse {
            return InstalledModScanResult(
                issues = listOf("Cannot resolve the instance Mods directory"),
                modsDirectoryExists = false,
            )
        }
        if (!modsDirectory.isDirectory) {
            return InstalledModScanResult(modsDirectoryExists = false)
        }

        val mods = mutableListOf<InstalledMod>()
        val issues = mutableListOf<String>()
        val visited = mutableSetOf<String>()
        val topLevelDirectories = safeListDirectories(modsDirectory, issues)
        var ignoredDirectoryCount = 0

        topLevelDirectories.forEach { directory ->
            val countBefore = mods.size
            collectRoots(
                instanceName = instanceName,
                modsDirectory = modsDirectory,
                directory = directory,
                visited = visited,
                result = mods,
                issues = issues,
            )
            if (mods.size == countBefore) ignoredDirectoryCount++
        }

        return InstalledModScanResult(
            mods = mods,
            ignoredDirectoryCount = ignoredDirectoryCount,
            issues = issues,
            modsDirectoryExists = true,
        )
    }

    private fun collectRoots(
        instanceName: String,
        modsDirectory: File,
        directory: File,
        visited: MutableSet<String>,
        result: MutableList<InstalledMod>,
        issues: MutableList<String>,
    ) {
        val canonical = canonicalDirectoryInside(directory, modsDirectory) ?: return
        if (!visited.add(canonical.path)) return

        val children = safeList(canonical, issues)
        if (children == null) return

        val infoFile = children.firstOrNull { it.isFile && it.name.equals("mod.info", ignoreCase = true) }
        if (infoFile != null) {
            result += buildInstalledMod(instanceName, modsDirectory, canonical, infoFile)
        }

        children.asSequence()
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
            .forEach { child ->
                collectRoots(instanceName, modsDirectory, child, visited, result, issues)
            }
    }

    private fun buildInstalledMod(
        instanceName: String,
        modsDirectory: File,
        root: File,
        infoFile: File,
    ): InstalledMod {
        val parsed = ModInfoParser.parse(infoFile)
        val relativePath = modsDirectory.toPath()
            .relativize(root.toPath())
            .toString()
            .replace(File.separatorChar, '/')
        val name = parsed.name ?: root.name.ifBlank { "mod.info" }
        val id = parsed.id

        return InstalledMod(
            instanceName = instanceName,
            rootPath = root.path,
            relativePath = relativePath,
            name = name,
            modId = id,
            description = parsed.description.orEmpty(),
            thumbnailPath = resolveLocalAsset(root, parsed.poster ?: parsed.icon),
            sizeBytes = directorySize(root),
            lastModifiedEpochMillis = root.lastModified(),
            metadataComplete = parsed.readable && !id.isNullOrBlank(),
            infoFields = parsed.fields,
        )
    }

    private fun resolveLocalAsset(root: File, rawPath: String?): String? {
        if (rawPath.isNullOrBlank()) return null
        val normalized = rawPath.replace('\\', '/')
        if (normalized.startsWith('/') || normalized.split('/').any { it == ".." || it.isEmpty() }) return null
        val target = runCatching { File(root, normalized).canonicalFile }.getOrNull() ?: return null
        if (!isWithin(root, target) || !target.isFile) return null
        return target.path
    }

    private fun directorySize(root: File): Long {
        val visited = mutableSetOf<String>()
        fun sizeOf(file: File): Long {
            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return 0L
            if (!isWithin(root, canonical)) return 0L
            if (canonical.isDirectory) {
                if (!visited.add(canonical.path)) return 0L
                return safeList(canonical, mutableListOf())?.sumOf(::sizeOf) ?: 0L
            }
            return canonical.length()
        }
        return sizeOf(root)
    }

    private fun safeListDirectories(directory: File, issues: MutableList<String>): List<File> =
        safeList(directory, issues).orEmpty()
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }

    private fun safeList(directory: File, issues: MutableList<String>): List<File>? {
        return try {
            directory.listFiles()?.toList() ?: run {
                issues += "Cannot read directory: ${directory.path}"
                null
            }
        } catch (error: SecurityException) {
            issues += "Cannot read directory: ${directory.path}"
            null
        }
    }

    private fun canonicalDirectoryInside(directory: File, boundary: File): File? {
        val canonical = runCatching { directory.canonicalFile }.getOrNull() ?: return null
        if (!canonical.isDirectory || !isWithin(boundary, canonical)) return null
        if (Files.isSymbolicLink(directory.toPath()) && !isWithin(boundary, canonical)) return null
        return canonical
    }

    private fun isWithin(boundary: File, candidate: File): Boolean {
        val boundaryPath = boundary.path.trimEnd(File.separatorChar)
        return candidate.path == boundaryPath || candidate.path.startsWith(boundaryPath + File.separator)
    }
}
