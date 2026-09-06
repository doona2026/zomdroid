package com.zomdroid.workshop.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class InstalledModModelsTest {
    @Test
    fun missingModIdMeansMetadataIsIncompleteByDefault() {
        val mod = InstalledMod("Demo", "/instances/Demo/Zomboid/mods/A", "A", "A")

        assertThat(mod.metadataComplete).isFalse()
        assertThat(mod.duplicateModId).isFalse()
        assertThat(mod.matchedWorkshopId).isNull()
    }

    @Test
    fun scanResultDefaultsToAnExistingModsDirectoryWithNoIssues() {
        val result = InstalledModScanResult()

        assertThat(result.mods).isEmpty()
        assertThat(result.ignoredDirectoryCount).isEqualTo(0)
        assertThat(result.issues).isEmpty()
        assertThat(result.modsDirectoryExists).isTrue()
    }
}
