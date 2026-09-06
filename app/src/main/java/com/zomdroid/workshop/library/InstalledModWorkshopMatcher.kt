package com.zomdroid.workshop.library

import com.zomdroid.workshop.install.WorkshopModArchiveInspector
import java.io.File
import java.util.Locale

object InstalledModWorkshopMatcher {
    @JvmStatic
    fun match(mod: InstalledMod, entries: List<ModLibraryEntry>): ModLibraryEntry? {
        val currentInstanceEntries = entries.filter { it.installedInstances.contains(mod.instanceName) }
        val rootName = File(mod.rootPath).name
        val rootMatches = currentInstanceEntries.filter { entry ->
            runCatching {
                WorkshopModArchiveInspector.findModRootNames(File(entry.completedPath))
                    .any { it.equals(rootName, ignoreCase = true) }
            }.getOrDefault(false)
        }
        if (rootMatches.size == 1) return rootMatches.single()
        if (rootMatches.size > 1) return null

        val modId = mod.modId?.trim()?.takeIf(String::isNotEmpty)?.lowercase(Locale.ROOT) ?: return null
        val idMatches = entries.filter { entry ->
            runCatching {
                WorkshopModArchiveInspector.findModInfoIds(File(entry.completedPath))
                    .any { it.trim().lowercase(Locale.ROOT) == modId }
            }.getOrDefault(false)
        }
        return idMatches.singleOrNull()
    }

    @JvmStatic
    fun annotate(mods: List<InstalledMod>, entries: List<ModLibraryEntry>): List<InstalledMod> =
        mods.map { mod ->
            match(mod, entries)?.let { entry ->
                mod.copy(
                    matchedWorkshopId = entry.publishedFileId,
                    matchedWorkshopTitle = entry.title,
                )
            } ?: mod.copy(matchedWorkshopId = null, matchedWorkshopTitle = null)
        }
}
