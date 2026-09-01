package com.zomdroid.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.zomdroid.ui.model.AppearanceMode
import com.zomdroid.ui.theme.LocalZomdroidAppearanceMode
import com.zomdroid.ui.theme.LocalZomdroidBackdrop
import com.zomdroid.ui.model.GlassRenderingStrategy
import com.zomdroid.ui.model.glassRenderingStrategy

// Adapted from WorkshopAndroidDownloader's Backdrop surfaces under Apache-2.0.
@Composable
fun ZomdroidGlassSurface(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(24.dp), blurRadius: Dp = 18.dp, lensHeight: Dp = 10.dp, lensAmount: Dp = 12.dp, surfaceColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = .18f), borderColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = .1f), contentColor: Color = MaterialTheme.colorScheme.onSurface, content: @Composable BoxScope.() -> Unit) {
    val appearance = LocalZomdroidAppearanceMode.current
    val backdrop = LocalZomdroidBackdrop.current
    val dark = MaterialTheme.colorScheme.background.luminance() < .35f
    val strategy = glassRenderingStrategy(appearance, backdrop != null)
    val lite = strategy == GlassRenderingStrategy.LiteSurface
    val liteSurface = surfaceColor.copy(alpha = (surfaceColor.alpha + if (dark) .34f else .42f).coerceAtMost(.92f))
    val liteBorder = borderColor.copy(alpha = borderColor.alpha.coerceAtLeast(if (dark) .18f else .14f))
    if (strategy != GlassRenderingStrategy.Backdrop) {
        Surface(modifier = modifier, shape = shape, color = if (lite) liteSurface else MaterialTheme.colorScheme.surface, border = if (appearance != AppearanceMode.Classic) BorderStroke(1.dp, liteBorder) else null, contentColor = contentColor) {
            Box(content = content)
        }
        return
    }
    Box(modifier.drawBackdrop(backdrop = backdrop!!, shape = { shape }, effects = { vibrancy(); blur(blurRadius.toPx()); lens(lensHeight.toPx(), lensAmount.toPx()) }, highlight = { Highlight.Ambient.copy(alpha = if (dark) .24f else .16f) }, shadow = { Shadow(radius = 28.dp, alpha = if (dark) .72f else .22f, color = Color.Black.copy(alpha = if (dark) .28f else .12f)) }, innerShadow = { InnerShadow(radius = 14.dp, alpha = if (dark) .32f else .18f, color = Color.Black.copy(alpha = if (dark) .2f else .08f)) }, onDrawSurface = { drawRect(surfaceColor) }).border(1.dp, borderColor, shape).clip(shape)) { content() }
}

@Composable
fun ZomdroidGlassCard(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) =
    ZomdroidGlassSurface(modifier = modifier, content = content)

@Composable
fun ZomdroidBackdropScaffold(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val wallpaper = rememberLayerBackdrop()
    val contentBackdrop = rememberLayerBackdrop()
    val chrome = rememberCombinedBackdrop(wallpaper, contentBackdrop)
    Box(modifier.fillMaxSize()) {
        ZomdroidProceduralWallpaper(Modifier.fillMaxSize().layerBackdrop(wallpaper))
        Box(Modifier.fillMaxSize().layerBackdrop(contentBackdrop))
        CompositionLocalProvider(LocalZomdroidBackdrop provides chrome) { content() }
    }
}
