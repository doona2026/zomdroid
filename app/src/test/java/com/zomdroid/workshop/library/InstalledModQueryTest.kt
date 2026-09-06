package com.zomdroid.workshop.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InstalledModQueryTest {
    @Test
    fun searchesNameIdDirectoryAndRelativePathCaseInsensitively() {
        val mods = listOf(
            mod("Alpha", "alpha.id", "FolderA", "FolderA"),
            mod("Beta", "beta.id", "FolderB", "Workshop/FolderB"),
        )

        assertThat(InstalledModQuery.filterAndSort(mods, "ALPHA", InstalledModSortOrder.NAME_ASC).map { it.name })
            .containsExactly("Alpha")
        assertThat(InstalledModQuery.filterAndSort(mods, "beta.id", InstalledModSortOrder.NAME_ASC).map { it.name })
            .containsExactly("Beta")
        assertThat(InstalledModQuery.filterAndSort(mods, "folderb", InstalledModSortOrder.NAME_ASC).map { it.name })
            .containsExactly("Beta")
        assertThat(InstalledModQuery.filterAndSort(mods, "workshop/folderb", InstalledModSortOrder.NAME_ASC).map { it.name })
            .containsExactly("Beta")
    }

    @Test
    fun sortsByNameAndDirectoryModificationTimeWithStableTieBreakers() {
        val mods = listOf(
            mod("Zulu", "z", "z", "z", modified = 10L),
            mod("Alpha", "a", "a", "a", modified = 10L),
            mod("Middle", "m", "m", "m", modified = 20L),
        )

        assertThat(InstalledModQuery.filterAndSort(mods, "", InstalledModSortOrder.NAME_ASC).map { it.name })
            .containsExactly("Alpha", "Middle", "Zulu").inOrder()
        assertThat(InstalledModQuery.filterAndSort(mods, "", InstalledModSortOrder.LAST_MODIFIED_DESC).map { it.name })
            .containsExactly("Middle", "Alpha", "Zulu").inOrder()
        assertThat(InstalledModQuery.filterAndSort(mods, "", InstalledModSortOrder.LAST_MODIFIED_ASC).map { it.name })
            .containsExactly("Alpha", "Zulu", "Middle").inOrder()
    }

    @Test
    fun marksAllNonBlankDuplicateIdsWithoutMergingEntries() {
        val mods = listOf(
            mod("One", "same", "one", "one"),
            mod("Two", "same", "two", "two"),
            mod("No ID", null, "none", "none"),
        )

        val result = InstalledModQuery.filterAndSort(mods, "", InstalledModSortOrder.NAME_ASC)

        assertThat(result).hasSize(3)
        assertThat(result.filter { it.modId == "same" }.all { it.duplicateModId }).isTrue()
        assertThat(result.first { it.name == "No ID" }.duplicateModId).isFalse()
    }

    private fun mod(
        name: String,
        id: String?,
        folder: String,
        relative: String,
        modified: Long = 0L,
    ) = InstalledMod("Demo", "/mods/$relative", relative, name, id, lastModifiedEpochMillis = modified)
}
