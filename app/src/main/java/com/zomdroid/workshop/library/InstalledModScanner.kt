package com.zomdroid.workshop.library

import java.io.File
import java.nio.file.Files
import java.util.Locale

class InstalledModScanner {
    private data class NestedModRoot(
        val root: File,
        val infoFile: File,
        val parsed: ParsedModInfo,
    )

    private val versionDirectoryPattern = Regex(
        "(?i)^(?:b|build)?\\d{2,3}(?:[._-]\\d{1,3})?(?:[._-]\\d{1,3})?$",
    )

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
            // A root-level mod.info makes this directory the authoritative Mod root.
            // Nested version/sub-mod directories belong to it and must not be listed separately.
            return
        }

        val nestedRoots = findDirectNestedRoots(children, modsDirectory, issues)
        if (shouldGroupAsLogicalMod(children, nestedRoots)) {
            result += buildGroupedInstalledMod(instanceName, modsDirectory, canonical, nestedRoots)
            return
        }

        children.asSequence()
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
            .forEach { child ->
                collectRoots(instanceName, modsDirectory, child, visited, result, issues)
            }
    }

    private fun findDirectNestedRoots(
        children: List<File>,
        modsDirectory: File,
        issues: MutableList<String>,
    ): List<NestedModRoot> = children.asSequence()
        .filter { it.isDirectory }
        .sortedBy { it.name.lowercase(Locale.ROOT) }
        .mapNotNull { child ->
            val root = canonicalDirectoryInside(child, modsDirectory) ?: return@mapNotNull null
            val infoFile = safeList(root, issues)
                ?.firstOrNull { it.isFile && it.name.equals("mod.info", ignoreCase = true) }
                ?: return@mapNotNull null
            NestedModRoot(root, infoFile, ModInfoParser.parse(infoFile))
        }
        .toList()

    private fun shouldGroupAsLogicalMod(
        children: List<File>,
        nestedRoots: List<NestedModRoot>,
    ): Boolean {
        if (nestedRoots.size < 2) {
            return false
        }
        val versionRoots = nestedRoots.filter { versionDirectoryPattern.matches(it.root.name) }
        val commonRoots = nestedRoots.filter { it.root.name.equals("common", ignoreCase = true) }
        if (versionRoots.isEmpty() || versionRoots.size + commonRoots.size != nestedRoots.size) {
            return false
        }

        val ids = nestedRoots.mapNotNull { it.parsed.id?.trim()?.takeIf(String::isNotEmpty) }
            .map { it.lowercase(Locale.ROOT) }
            .toSet()
        if (ids.size == 1 && nestedRoots.all { !it.parsed.id.isNullOrBlank() }) return true

        val hasCommonDirectory = children.any { it.isDirectory && it.name.equals("common", ignoreCase = true) }
        if (!hasCommonDirectory) return false
        val names = nestedRoots.map { groupingName(it.parsed.name ?: it.root.name) }.toSet()
        return names.size == 1
    }

    private fun groupingName(name: String): String {
        return name.lowercase(Locale.ROOT)
            .replace(Regex("\\s*\\[?\\s*(?:legacy\\s*)?(?:build\\s*)?b?\\d{2,3}(?:[._-]\\d{1,3})?\\s*\\]?\\s*$"), "")
            .replace(Regex("[^a-z0-9]+"), "")
    }

    private fun buildGroupedInstalledMod(
        instanceName: String,
        modsDirectory: File,
        packageRoot: File,
        nestedRoots: List<NestedModRoot>,
    ): InstalledMod {
        val orderedRoots = nestedRoots.sortedWith(
            compareBy<NestedModRoot> { versionRank(it.root.name) }
                .thenBy { it.root.lastModified() }
                .thenBy { it.root.path.lowercase(Locale.ROOT) },
        )
        val primary = orderedRoots.last()
        val variants = orderedRoots.map { nested ->
            InstalledModVariant(
                rootPath = nested.root.path,
                relativePath = relativePath(modsDirectory, nested.root),
                name = nested.parsed.name ?: nested.root.name,
                modId = nested.parsed.id,
            )
        }
        return buildInstalledMod(
            instanceName = instanceName,
            modsDirectory = modsDirectory,
            packageRoot = packageRoot,
            infoRoot = primary.root,
            parsed = primary.parsed,
            variants = variants,
        )
    }

    private fun versionRank(name: String): Long {
        val match = Regex("(?i)^(?:b|build)?(\\d{2,3})(?:[._-](\\d{1,3}))?(?:[._-](\\d{1,3}))?$").matchEntire(name)
            ?: return Long.MIN_VALUE
        val major = match.groupValues[1].toLongOrNull() ?: return Long.MIN_VALUE
        val minor = match.groupValues[2].toLongOrNull() ?: 0L
        val patch = match.groupValues[3].toLongOrNull() ?: 0L
        return major * 1_000_000L + minor * 1_000L + patch
    }

    private fun buildInstalledMod(
        instanceName: String,
        modsDirectory: File,
        root: File,
        infoFile: File,
    ): InstalledMod = buildInstalledMod(
        instanceName = instanceName,
        modsDirectory = modsDirectory,
        packageRoot = root,
        infoRoot = root,
        parsed = ModInfoParser.parse(infoFile),
        variants = emptyList(),
    )

    private fun buildInstalledMod(
        instanceName: String,
        modsDirectory: File,
        packageRoot: File,
        infoRoot: File,
        parsed: ParsedModInfo,
        variants: List<InstalledModVariant>,
    ): InstalledMod {
        val relativePath = relativePath(modsDirectory, packageRoot)
        val name = parsed.name ?: infoRoot.name.ifBlank { "mod.info" }
        val id = parsed.id

        return InstalledMod(
            instanceName = instanceName,
            rootPath = packageRoot.path,
            relativePath = relativePath,
            name = name,
            modId = id,
            description = parsed.description.orEmpty(),
            thumbnailPath = resolveLocalAsset(infoRoot, parsed.poster ?: parsed.icon),
            sizeBytes = directorySize(packageRoot),
            lastModifiedEpochMillis = maxOf(
                packageRoot.lastModified(),
                variants.maxOfOrNull { File(it.rootPath).lastModified() } ?: 0L,
            ),
            metadataComplete = parsed.readable && !id.isNullOrBlank(),
            infoFields = parsed.fields,
            infoRootPath = infoRoot.path,
            variants = variants,
        )
    }

    private fun relativePath(modsDirectory: File, root: File): String = modsDirectory.toPath()
        .relativize(root.toPath())
        .toString()
        .replace(File.separatorChar, '/')

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
