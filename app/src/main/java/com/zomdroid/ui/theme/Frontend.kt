package com.zomdroid.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.zomdroid.ui.model.AppearanceMode

data class ZomdroidChromePadding(val top: Dp = 0.dp, val bottom: Dp = 0.dp)
val LocalZomdroidAppearanceMode = staticCompositionLocalOf { AppearanceMode.default }
val LocalZomdroidBackdrop = staticCompositionLocalOf<Backdrop?> { null }
val LocalZomdroidChromePadding = staticCompositionLocalOf { ZomdroidChromePadding() }

@Composable fun isLiquidGlassFrontendEnabled() = LocalZomdroidAppearanceMode.current != AppearanceMode.Classic
@Composable fun isLiteLiquidGlassFrontendEnabled() = LocalZomdroidAppearanceMode.current == AppearanceMode.LiteLiquidGlass
@Composable fun shouldReduceLiquidGlassEffects() = isLiteLiquidGlassFrontendEnabled()

@Composable
fun zomdroidListContentPadding(topExtra: Dp = 0.dp, bottomExtra: Dp = 0.dp): PaddingValues {
    val chrome = LocalZomdroidChromePadding.current
    val overlap = if (isLiquidGlassFrontendEnabled()) 24.dp else 0.dp
    return PaddingValues((chrome.top + topExtra - overlap).coerceAtLeast(0.dp), chrome.bottom + bottomExtra)
}

@Composable
fun Modifier.zomdroidChromePadding(topExtra: Dp = 0.dp, bottomExtra: Dp = 0.dp): Modifier {
    val chrome = LocalZomdroidChromePadding.current
    return padding(top = chrome.top + topExtra, bottom = chrome.bottom + bottomExtra)
}
