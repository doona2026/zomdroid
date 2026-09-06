package com.zomdroid.workshop.library

import java.util.Comparator
import java.util.Locale

object InstalledModQuery {
    @JvmStatic
    fun filterAndSort(
        mods: List<InstalledMod>,
        query: String?,
        sortOrder: InstalledModSortOrder,
    ): List<InstalledMod> {
        val duplicateIds = mods.asSequence()
            .mapNotNull { it.modId?.trim()?.takeIf(String::isNotEmpty) }
            .groupingBy { it.lowercase(Locale.ROOT) }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        val prepared = mods.map { mod ->
            val normalizedId = mod.modId?.trim()?.takeIf(String::isNotEmpty)?.lowercase(Locale.ROOT)
            if (normalizedId != null && normalizedId in duplicateIds) {
                mod.copy(duplicateModId = true)
            } else {
                mod.copy(duplicateModId = false)
            }
        }
        val needle = query.orEmpty().trim().lowercase(Locale.ROOT)
        return prepared.asSequence()
            .filter { needle.isEmpty() || it.searchableValues().any { value -> value.contains(needle) } }
            .sortedWith(comparator(sortOrder))
            .toList()
    }

    private fun InstalledMod.searchableValues(): List<String> = listOf(
        name,
        modId.orEmpty(),
        rootPath.substringAfterLast('/').substringAfterLast('\\'),
        relativePath,
    ).map { it.lowercase(Locale.ROOT) }

    private fun comparator(sortOrder: InstalledModSortOrder): Comparator<InstalledMod> {
        val nameComparator = compareBy<InstalledMod> { it.name.lowercase(Locale.ROOT) }
            .thenBy { it.modId.orEmpty().lowercase(Locale.ROOT) }
            .thenBy { it.relativePath.lowercase(Locale.ROOT) }
        return when (sortOrder) {
            InstalledModSortOrder.NAME_ASC -> nameComparator
            InstalledModSortOrder.LAST_MODIFIED_DESC ->
                compareByDescending<InstalledMod> { it.lastModifiedEpochMillis }
                    .then(nameComparator)
            InstalledModSortOrder.LAST_MODIFIED_ASC ->
                compareBy<InstalledMod> { it.lastModifiedEpochMillis }
                    .then(nameComparator)
        }
    }
}
