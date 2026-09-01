package com.zomdroid.workshop.library

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ModVersioningTest {
    private val old = ModLibraryEntry(
        appId = 108600,
        publishedFileId = 123,
        title = "Mod",
        updatedAtEpochSeconds = 100,
        versionKey = "old-key",
        completedPath = "/tmp/mod.zip",
    )

    @Test
    fun newerWorkshopTimestampWins() {
        assertThat(isNewerModVersion(old, ModVersionCandidate(updatedAtEpochSeconds = 101))).isTrue()
        assertThat(isNewerModVersion(old, ModVersionCandidate(updatedAtEpochSeconds = 99))).isFalse()
    }

    @Test
    fun missingMetadataUsesStableFileFingerprint() {
        val candidate = ModVersionCandidate(
            updatedAtEpochSeconds = null,
            files = listOf(ModLibraryFile("mod.info", 12, 50)),
        )
        assertThat(modVersionKey(candidate)).isEqualTo(modVersionKey(candidate))
        assertThat(modVersionKey(candidate)).isNotEqualTo(modVersionKey(candidate.copy(files = emptyList())))
    }

    @Test
    fun sameVersionIsNotNewerEvenWhenMetadataIsSame() {
        val candidate = ModVersionCandidate(updatedAtEpochSeconds = 100)
        val same = old.copy(versionKey = modVersionKey(candidate))
        assertThat(isNewerModVersion(same, candidate)).isFalse()
    }
}
