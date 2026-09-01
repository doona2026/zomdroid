package com.zomdroid.ui

import com.google.common.truth.Truth.assertThat
import com.zomdroid.ui.model.AppearanceMode
import com.zomdroid.ui.model.GlassRenderingStrategy
import com.zomdroid.ui.model.glassRenderingStrategy
import org.junit.Test

class GlassRenderingStrategyTest {
    @Test fun fullLiquidGlassUsesBackdropWhenAvailable() {
        assertThat(glassRenderingStrategy(AppearanceMode.LiquidGlass, true)).isEqualTo(GlassRenderingStrategy.Backdrop)
    }
    @Test fun liteModeNeverEntersExpensiveBackdropPath() {
        assertThat(glassRenderingStrategy(AppearanceMode.LiteLiquidGlass, true)).isEqualTo(GlassRenderingStrategy.LiteSurface)
    }
    @Test fun classicModeUsesMaterialSurfaceEvenWhenBackdropExists() {
        assertThat(glassRenderingStrategy(AppearanceMode.Classic, true)).isEqualTo(GlassRenderingStrategy.ClassicSurface)
    }

    @Test fun liquidGlassFallsBackToLiteSurfaceWhenBackdropIsUnavailable() {
        assertThat(glassRenderingStrategy(AppearanceMode.LiquidGlass, false)).isEqualTo(GlassRenderingStrategy.LiteSurface)
    }

    @Test fun onlyFullLiquidGlassUsesBackdropAndOnlyWhenAvailable() {
        AppearanceMode.entries.forEach { mode ->
            assertThat(glassRenderingStrategy(mode, false)).isNotEqualTo(GlassRenderingStrategy.Backdrop)
            if (mode == AppearanceMode.LiquidGlass) {
                assertThat(glassRenderingStrategy(mode, true)).isEqualTo(GlassRenderingStrategy.Backdrop)
            } else {
                assertThat(glassRenderingStrategy(mode, true)).isNotEqualTo(GlassRenderingStrategy.Backdrop)
            }
        }
    }
}
