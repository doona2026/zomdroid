package com.zomdroid.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Canvas
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
import com.kyant.shapes.Capsule
import com.zomdroid.ui.theme.LocalZomdroidBackdrop
import com.zomdroid.ui.theme.isLiquidGlassFrontendEnabled
import com.zomdroid.ui.theme.shouldReduceLiquidGlassEffects
import kotlin.math.roundToInt

// These controls mirror WorkshopAndroidDownloader's LiquidButton, LiquidToggle,
// LiquidSlider and LiquidBottomTabs. Material controls are intentionally not
// placed inside the liquid-glass surfaces.
@Composable
fun ZomdroidLiquidButton(
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    enabled: Boolean = onClick != null,
    filled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    var highlightOffset by remember { mutableStateOf(Offset.Zero) }
    val click = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onClick = onClick,
        )
    } else Modifier
    val backdrop = LocalZomdroidBackdrop.current
    val fullLiquid = isLiquidGlassFrontendEnabled() && !shouldReduceLiquidGlassEffects() && backdrop != null
    val scale = if (pressed) 1.045f else 1f
    val surfaceColor = if (filled) {
        MaterialTheme.colorScheme.primary.copy(alpha = .22f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = .14f)
    }

    if (fullLiquid) {
        Row(
            modifier = modifier
                .height(height)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { Capsule() },
                    effects = {
                        vibrancy()
                        blur(2.dp.toPx())
                        lens(12.dp.toPx(), 24.dp.toPx(), chromaticAberration = true)
                    },
                    highlight = { Highlight.Ambient.copy(alpha = if (pressed) .34f else .16f) },
                    shadow = { Shadow(radius = 10.dp, alpha = if (pressed) .24f else .12f) },
                    innerShadow = { InnerShadow(radius = 5.dp, alpha = if (pressed) .22f else .1f) },
                    onDrawSurface = { drawRect(surfaceColor) },
                )
                .drawWithContent {
                    drawContent()
                    if (pressed) {
                        drawCircle(
                            color = Color.White.copy(alpha = .12f),
                            radius = size.minDimension * .72f,
                            center = highlightOffset,
                        )
                    }
                }
                .then(click)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { highlightOffset = it },
                        onDragEnd = { highlightOffset = Offset.Zero },
                        onDragCancel = { highlightOffset = Offset.Zero },
                    ) { change, _ -> highlightOffset = change.position }
                }
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    } else {
        ZomdroidGlassSurface(
            modifier = modifier.height(height),
            shape = Capsule(),
            lensHeight = 12.dp,
            lensAmount = 24.dp,
            surfaceColor = surfaceColor,
            borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (filled) .2f else .18f),
        ) {
            Row(
                Modifier.fillMaxHeight().then(click).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}

@Composable
fun ZomdroidLiquidOutlinedButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) =
    ZomdroidLiquidButton(if (enabled) onClick else null, modifier, enabled = enabled, filled = false, content = content)

@Composable
fun ZomdroidLiquidTextButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable RowScope.() -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    Row(
        modifier
            .height(40.dp)
            .graphicsLayer { scaleX = if (pressed) .97f else 1f; scaleY = if (pressed) .97f else 1f }
            .clip(Capsule())
            .then(
                Modifier.clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ),
            )
            .background(if (pressed) MaterialTheme.colorScheme.primary.copy(alpha = .14f) else Color.Transparent)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
fun ZomdroidLiquidChip(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, label: @Composable RowScope.() -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val background = when {
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = if (pressed) .32f else .22f)
        pressed -> MaterialTheme.colorScheme.primary.copy(alpha = .14f)
        else -> MaterialTheme.colorScheme.surface.copy(alpha = .1f)
    }
    Row(
        modifier
            .height(38.dp)
            .clip(Capsule())
            .background(background)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .18f), Capsule())
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = label,
    )
}

@Composable
fun ZomdroidLiquidFloatingActionButton(onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    ZomdroidLiquidIconButton(onClick = onClick, modifier = modifier.size(56.dp), content = content)
}

@Composable
fun ZomdroidLiquidCheckbox(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .2f)
    Box(
        modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = .2f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, role = Role.Checkbox, onClick = { onCheckedChange(!checked) }),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Text("✓", color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ZomdroidLiquidIconButton(onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, content: @Composable () -> Unit) {
    val backdrop = LocalZomdroidBackdrop.current
    if (isLiquidGlassFrontendEnabled() && !shouldReduceLiquidGlassEffects() && backdrop != null) {
        ZomdroidLiquidButton(if (enabled) onClick else null, modifier.size(44.dp), 44.dp, enabled = enabled) { content() }
    } else {
        Box(
            modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { content() }
    }
}

@Composable
fun ZomdroidGlassIconButton(imageVector: ImageVector, contentDescription: String?, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val backdrop = LocalZomdroidBackdrop.current
    if (isLiquidGlassFrontendEnabled() && !shouldReduceLiquidGlassEffects() && backdrop != null) {
        ZomdroidLiquidButton(if (enabled) onClick else null, modifier.size(44.dp), 44.dp, enabled = enabled) {
            Icon(imageVector, contentDescription, tint = MaterialTheme.colorScheme.onSurface)
        }
    } else {
        Box(
            modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) { Icon(imageVector, contentDescription) }
    }
}

@Composable
fun ZomdroidNavigationItem(selected: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val item = modifier.then(if (selected) Modifier else Modifier.clickable(onClick = onClick))
    if (selected) {
        ZomdroidGlassSurface(item, surfaceColor = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                content()
                Text(label, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge)
            }
        }
    } else {
        Row(item.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
            Text(label, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun ZomdroidLiquidToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val backdrop = LocalZomdroidBackdrop.current
    val fullLiquid = isLiquidGlassFrontendEnabled() && !shouldReduceLiquidGlassEffects() && backdrop != null
    if (!fullLiquid) {
        ZomdroidGlassSurface(
            modifier = modifier.width(64.dp).height(32.dp).clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) },
            shape = Capsule(),
            lensHeight = 8.dp,
            lensAmount = 10.dp,
            surfaceColor = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = .38f) else MaterialTheme.colorScheme.surface.copy(alpha = .2f),
        ) {
            Box(Modifier.fillMaxSize().padding(4.dp), contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
                Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.onSurfaceVariant, CircleShape))
            }
        }
        return
    }

    val trackBackdrop = rememberLayerBackdrop()
    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .2f)
    val accentColor = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = .28f)
    var dragFraction by remember(checked) { mutableFloatStateOf(if (checked) 1f else 0f) }
    var dragging by remember { mutableStateOf(false) }
    val targetFraction = if (dragging) dragFraction else if (checked) 1f else 0f
    val fraction by animateFloatAsState(targetFraction, spring(stiffness = Spring.StiffnessMediumLow), label = "liquidToggleFraction")
    val thumbScale = if (dragging) 1.18f else 1f

    Box(
        modifier = modifier
            .width(64.dp)
            .height(32.dp)
            .semantics { role = Role.Switch; this.selected = checked }
            .clickable(enabled = enabled, role = Role.Switch) { onCheckedChange(!checked) }
            .pointerInput(enabled) {
                detectDragGestures(
                    onDragStart = {
                        dragging = true
                        dragFraction = if (checked) 1f else 0f
                    },
                    onDragEnd = {
                        dragging = false
                        onCheckedChange(dragFraction >= .5f)
                    },
                    onDragCancel = { dragging = false },
                ) { change, amount ->
                    dragFraction = (dragFraction + amount.x / 20.dp.toPx()).coerceIn(0f, 1f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(Capsule())
                .drawBehind { drawRect(lerp(trackColor, accentColor, fraction)) }
                .size(64.dp, 28.dp),
        )
        Box(
            Modifier
                .graphicsLayer {
                    translationX = 2.dp.toPx() + 20.dp.toPx() * fraction
                    scaleX = thumbScale
                    scaleY = thumbScale
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, trackBackdrop),
                    shape = { Capsule() },
                    effects = {
                        blur(8.dp.toPx())
                        lens(8.dp.toPx(), 14.dp.toPx(), chromaticAberration = true)
                    },
                    highlight = { Highlight.Ambient.copy(alpha = if (dragging) .36f else .2f) },
                    shadow = { Shadow(radius = 5.dp, alpha = .18f) },
                    innerShadow = { InnerShadow(radius = if (dragging) 5.dp else 2.dp, alpha = .25f) },
                    onDrawSurface = { drawRect(Color.White.copy(alpha = if (dragging) .78f else .92f)) },
                )
                .size(40.dp, 24.dp),
        )
    }
}

@Composable
fun ZomdroidLiquidSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val backdrop = LocalZomdroidBackdrop.current
    val fullLiquid = isLiquidGlassFrontendEnabled() && !shouldReduceLiquidGlassEffects() && backdrop != null
    var dragging by remember { mutableStateOf(false) }
    BoxWithConstraints(
        modifier = modifier.fillMaxWidth().height(42.dp).pointerInput(enabled, valueRange) {
            detectTapGestures { position ->
                val fraction = (position.x / size.width).coerceIn(0f, 1f)
                onValueChange(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
            }
        }.pointerInput(enabled, valueRange) {
            detectDragGestures(
                onDragStart = { dragging = true },
                onDragEnd = { dragging = false },
                onDragCancel = { dragging = false },
            ) { change, _ ->
                val fraction = (change.position.x / size.width).coerceIn(0f, 1f)
                onValueChange(valueRange.start + fraction * (valueRange.endInclusive - valueRange.start))
            }
        },
        contentAlignment = Alignment.CenterStart,
    ) {
        val progress = ((value - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
        val trackBackdrop = rememberLayerBackdrop()
        val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (fullLiquid) .18f else .2f)
        val accentColor = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) .86f else .35f)
        Box(Modifier.layerBackdrop(trackBackdrop).fillMaxWidth().height(6.dp).clip(Capsule()).background(trackColor))
        Box(Modifier.fillMaxWidth(progress).height(6.dp).clip(Capsule()).background(accentColor))
        Box(
            Modifier
                .graphicsLayer {
                    translationX = (constraints.maxWidth * progress - 20.dp.toPx()).coerceIn(0f, constraints.maxWidth.toFloat() - 40.dp.toPx())
                    scaleX = if (enabled) 1f else .92f
                    scaleY = if (enabled) 1f else .92f
                    if (dragging) {
                        scaleX *= 1.16f
                        scaleY *= 1.16f
                    }
                }
                .then(
                    if (fullLiquid) {
                        Modifier.drawBackdrop(
                            backdrop = rememberCombinedBackdrop(backdrop, trackBackdrop),
                            shape = { Capsule() },
                            effects = { blur(8.dp.toPx()); lens(10.dp.toPx(), 14.dp.toPx(), chromaticAberration = true) },
                            highlight = { Highlight.Ambient.copy(alpha = .24f) },
                            shadow = { Shadow(radius = 5.dp, alpha = .18f) },
                            innerShadow = { InnerShadow(radius = 3.dp, alpha = .2f) },
                            onDrawSurface = { drawRect(Color.White.copy(alpha = .86f)) },
                        )
                    } else Modifier.background(MaterialTheme.colorScheme.surface.copy(alpha = .86f), Capsule())
                )
                .size(40.dp, 24.dp),
        )
    }
}

@Composable
fun ZomdroidLiquidBottomNavigation(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (index: Int, selected: Boolean, onClick: () -> Unit) -> Unit,
) {
    val backdrop = LocalZomdroidBackdrop.current
    val fullLiquid = isLiquidGlassFrontendEnabled() && !shouldReduceLiquidGlassEffects() && backdrop != null
    BoxWithConstraints(modifier.height(64.dp), contentAlignment = Alignment.CenterStart) {
        val tabs = rememberLayerBackdrop()
        val density = LocalDensity.current
        val selectedProgress by animateFloatAsState(selectedIndex.toFloat(), spring(stiffness = Spring.StiffnessMediumLow), label = "liquidBottomTabIndicator")
        val innerPadding = 4.dp
        val indicatorWidth = ((constraints.maxWidth.toFloat() - with(density) { innerPadding.toPx() } * 2f) / 5f).coerceAtLeast(0f)
        val containerColor = MaterialTheme.colorScheme.surface.copy(alpha = if (fullLiquid) .2f else .72f)
        val indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = if (fullLiquid) .16f else .18f)

        if (fullLiquid) {
            Row(
                Modifier
                    .fillMaxSize()
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { Capsule() },
                        effects = { vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx()) },
                        highlight = { Highlight.Ambient.copy(alpha = .16f) },
                        onDrawSurface = { drawRect(containerColor) },
                    )
                    .padding(innerPadding)
                    .layerBackdrop(tabs),
            ) { repeat(5) { Box(Modifier.weight(1f).fillMaxHeight()) } }
            Box(
                Modifier
                    .padding(innerPadding)
                    .graphicsLayer { translationX = selectedProgress * indicatorWidth }
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabs),
                        shape = { Capsule() },
                        effects = { lens(10.dp.toPx(), 14.dp.toPx(), chromaticAberration = true) },
                        highlight = { Highlight.Default.copy(alpha = .35f) },
                        shadow = { Shadow(radius = 6.dp, alpha = .2f) },
                        innerShadow = { InnerShadow(radius = 6.dp, alpha = .2f) },
                        onDrawSurface = { drawRect(indicatorColor) },
                    )
                    .width(with(density) { indicatorWidth.toDp() })
                    .height(56.dp),
            )
        } else {
            Surface(Modifier.fillMaxSize(), shape = Capsule(), color = containerColor) {
                Box(Modifier.fillMaxSize().padding(innerPadding)) {
                    Box(Modifier.graphicsLayer { translationX = selectedProgress * indicatorWidth }.width(with(density) { indicatorWidth.toDp() }).fillMaxHeight().background(indicatorColor, Capsule()))
                }
            }
        }
        Row(Modifier.fillMaxSize().padding(innerPadding), verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { index ->
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    itemContent(index, index == selectedIndex) { onSelected(index) }
                }
            }
        }
    }
}

@Composable
fun ZomdroidLiquidTextField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, singleLine: Boolean = false) {
    ZomdroidGlassSurface(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), blurRadius = 12.dp, lensHeight = 8.dp, lensAmount = 10.dp, surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = .12f)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Text(label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            BasicTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth().padding(top = 5.dp), textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), singleLine = singleLine)
        }
    }
}

@Composable
fun ZomdroidLiquidOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    supportingText: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
) {
    ZomdroidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        blurRadius = 12.dp,
        lensHeight = 8.dp,
        lensAmount = 10.dp,
        surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isError) .2f else .12f),
        borderColor = (if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface).copy(alpha = if (isError) .55f else .14f),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
            singleLine = singleLine,
            maxLines = maxLines,
            decorationBox = { innerTextField ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            label?.invoke()
                            Box {
                                if (placeholder != null && value.isEmpty()) placeholder()
                                innerTextField()
                            }
                        }
                        trailingIcon?.invoke()
                    }
                    supportingText?.invoke()
                }
            },
        )
    }
}

@Composable
fun ZomdroidLinearProgressIndicator(modifier: Modifier = Modifier) {
    ZomdroidLinearProgressIndicator(1f, modifier)
}

@Composable
fun ZomdroidLinearProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    Box(modifier.height(6.dp).clip(Capsule()).background(MaterialTheme.colorScheme.onSurface.copy(alpha = .14f))) {
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary.copy(alpha = .84f), Capsule()))
    }
}

@Composable
fun ZomdroidLinearProgressIndicator(progress: () -> Float, modifier: Modifier = Modifier) {
    ZomdroidLinearProgressIndicator(progress(), modifier)
}

@Composable
fun ZomdroidCircularProgressIndicator(modifier: Modifier = Modifier, strokeWidth: Dp = 4.dp) {
    val progressColor = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(36.dp)) {
        drawArc(
            color = progressColor,
            startAngle = -90f,
            sweepAngle = 270f,
            useCenter = false,
            style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth.toPx()),
        )
    }
}

@Composable
fun ZomdroidSectionLabel(text: String, modifier: Modifier = Modifier) = Text(text, modifier, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
