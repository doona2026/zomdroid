package com.zomdroid.ui.model

/** The three app-wide rendering strategies. Persist only [storageValue], never enum ordinals. */
enum class AppearanceMode(val storageValue: String) {
    LiquidGlass("liquid_glass"),
    LiteLiquidGlass("lite_liquid_glass"),
    Classic("classic");

    companion object {
        val default: AppearanceMode = LiquidGlass

        fun fromStorageValue(value: String?): AppearanceMode =
            entries.firstOrNull { it.storageValue == value } ?: default
    }
}

enum class GlassRenderingStrategy { Backdrop, LiteSurface, ClassicSurface }

fun glassRenderingStrategy(mode: AppearanceMode, backdropAvailable: Boolean): GlassRenderingStrategy = when {
    mode == AppearanceMode.Classic -> GlassRenderingStrategy.ClassicSurface
    mode == AppearanceMode.LiteLiquidGlass -> GlassRenderingStrategy.LiteSurface
    backdropAvailable -> GlassRenderingStrategy.Backdrop
    else -> GlassRenderingStrategy.LiteSurface
}
