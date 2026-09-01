package com.zomdroid.ui.launcher

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LauncherRepositoryTest {
    @Test
    fun mapsEveryPersistedInstanceFieldToImmutableUiState() {
        val record = LauncherInstanceRecord(
            name = "B42",
            buildVersion = "42",
            presetName = "Build 42.12+",
            homePath = "/instances/B42",
            installationFinished = true,
            hasGameFiles = true,
            hasFilesForLinux = true,
            backup = LauncherBackupUiModel("Apocalypse/World", 123L, 4_000_000L, false),
        )

        val mapped = LauncherRepository.mapRecord(record)

        assertThat(mapped.name).isEqualTo("B42")
        assertThat(mapped.buildVersion).isEqualTo("42")
        assertThat(mapped.presetName).isEqualTo("Build 42.12+")
        assertThat(mapped.homePath).isEqualTo("/instances/B42")
        assertThat(mapped.isReady).isTrue()
        assertThat(mapped.backup?.worldRel).isEqualTo("Apocalypse/World")
    }

    @Test
    fun readinessReflectsInstallationAndFileChecks() {
        val base = LauncherInstanceRecord("B41", "41", "Build 41", "/instances/B41", true, true, true)
        assertThat(LauncherRepository.mapRecord(base).isReady).isTrue()
        assertThat(LauncherRepository.mapRecord(base.copy(installationFinished = false)).isReady).isFalse()
        assertThat(LauncherRepository.mapRecord(base.copy(hasGameFiles = false)).isReady).isFalse()
        assertThat(LauncherRepository.mapRecord(base.copy(hasFilesForLinux = false)).isReady).isFalse()
    }
}
