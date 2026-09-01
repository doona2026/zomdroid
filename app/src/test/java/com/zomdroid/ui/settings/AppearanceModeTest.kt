package com.zomdroid.ui.settings

import com.google.common.truth.Truth.assertThat
import com.zomdroid.ui.model.AppearanceMode
import org.junit.Test

class AppearanceModeTest {
    @Test fun knownValuesRoundTripWithoutOrdinalCoupling() {
        AppearanceMode.entries.forEach { mode ->
            assertThat(AppearanceMode.fromStorageValue(mode.storageValue)).isEqualTo(mode)
        }
    }

    @Test fun missingOrUnknownValueUsesLiquidGlassAndNormalizesStorage() {
        val store = FakeStore(null)
        val repository = UiSettingsRepository(store)
        assertThat(repository.getAppearanceMode()).isEqualTo(AppearanceMode.LiquidGlass)
        assertThat(store.value).isEqualTo("liquid_glass")
        store.value = "future_mode"
        assertThat(repository.getAppearanceMode()).isEqualTo(AppearanceMode.LiquidGlass)
        assertThat(store.value).isEqualTo("liquid_glass")
    }

    @Test fun changingModePersistsStableString() {
        val store = FakeStore("liquid_glass")
        val repository = UiSettingsRepository(store)
        repository.setAppearanceMode(AppearanceMode.Classic)
        assertThat(store.value).isEqualTo("classic")
        assertThat(repository.getAppearanceMode()).isEqualTo(AppearanceMode.Classic)
    }

    private class FakeStore(var value: String?) : AppearanceModeStore {
        override fun read(): String? = value
        override fun write(value: String) { this.value = value }
    }
}

