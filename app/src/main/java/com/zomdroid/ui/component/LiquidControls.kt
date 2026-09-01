package com.zomdroid.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.shapes.Capsule
import com.zomdroid.ui.theme.LocalZomdroidBackdrop
import com.zomdroid.ui.theme.isLiquidGlassFrontendEnabled
import com.zomdroid.ui.theme.shouldReduceLiquidGlassEffects

// Adapted from WorkshopAndroidDownloader's liquid controls under Apache-2.0.
@Composable
fun ZomdroidLiquidButton(onClick: (() -> Unit)?, modifier: Modifier = Modifier, height: Dp = 48.dp, content: @Composable RowScope.() -> Unit) {
    val backdrop = LocalZomdroidBackdrop.current
    val useBackdrop = isLiquidGlassFrontendEnabled() && !shouldReduceLiquidGlassEffects() && backdrop != null
    val base = modifier.height(height)
    val click = if (onClick == null) Modifier else Modifier.clickable(role = Role.Button, onClick = onClick)
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .18f)
    val buttonModifier = if (useBackdrop) base.drawBackdrop(backdrop = backdrop!!, shape = { Capsule() }, effects = { blur(2.dp.toPx()); lens(12.dp.toPx(), 24.dp.toPx()) }, onDrawSurface = { drawRect(surfaceColor) }).then(click).padding(horizontal = 16.dp) else base.then(click)
    Row(modifier = buttonModifier, verticalAlignment = Alignment.CenterVertically, content = content)
}

@Composable
fun ZomdroidGlassIconButton(imageVector: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val backdrop = LocalZomdroidBackdrop.current
    if (isLiquidGlassFrontendEnabled() && !shouldReduceLiquidGlassEffects() && backdrop != null) {
        ZomdroidLiquidButton(if (enabled) onClick else null, modifier.size(44.dp), 44.dp) {
            Icon(imageVector, contentDescription, tint = MaterialTheme.colorScheme.onSurface)
        }
    } else {
        IconButton(onClick = onClick, modifier = modifier, enabled = enabled) { Icon(imageVector, contentDescription) }
    }
}

@Composable
fun ZomdroidNavigationItem(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val item = modifier.then(if (selected) Modifier else Modifier.clickable(onClick = onClick))
    if (selected) {
        ZomdroidGlassSurface(item, surfaceColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { content(); Text(label, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge) }
        }
    } else {
        Row(item.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { content(); Text(label, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
fun ZomdroidChoiceRow(label: String, value: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ZomdroidLiquidToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) = Switch(checked, onCheckedChange, modifier, enabled = enabled)

@Composable
fun ZomdroidLiquidSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, modifier: Modifier = Modifier, enabled: Boolean = true) = Slider(value, onValueChange, valueRange = valueRange, modifier = modifier.fillMaxWidth(), enabled = enabled)

@Composable
fun ZomdroidSectionLabel(text: String, modifier: Modifier = Modifier) = Text(text, modifier, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
