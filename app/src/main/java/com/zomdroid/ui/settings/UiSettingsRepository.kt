package com.zomdroid.ui.settings

import com.zomdroid.LauncherPreferences
import com.zomdroid.ui.model.AppearanceMode

/** Small adapter that keeps Compose settings independent from the legacy preferences object. */
interface AppearanceModeStore {
    fun read(): String?
    fun write(value: String)
}

class UiSettingsRepository(private val store: AppearanceModeStore) {
    fun getAppearanceMode(): AppearanceMode {
        val raw = store.read()
        val mode = AppearanceMode.fromStorageValue(raw)
        if (raw != mode.storageValue) store.write(mode.storageValue)
        return mode
    }

    fun setAppearanceMode(mode: AppearanceMode) = store.write(mode.storageValue)
}

class LauncherAppearanceModeStore(private val preferences: LauncherPreferences) : AppearanceModeStore {
    override fun read(): String? = preferences.frontendMode
    override fun write(value: String) { preferences.setFrontendMode(value) }
}

