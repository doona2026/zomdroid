package com.zomdroid.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.zomdroid.ui.theme.LocalZomdroidBackdrop
import com.zomdroid.ui.theme.isLiquidGlassFrontendEnabled
import com.zomdroid.ui.theme.shouldReduceLiquidGlassEffects
import kotlin.math.roundToInt

private val PopupShape = RoundedCornerShape(18.dp)

data class ZomdroidPopupAction(val label: String, val onClick: () -> Unit)

internal data class PopupEntry(
    val ownerId: Any,
    val anchorBounds: Rect,
    val modifier: Modifier,
    val onDismissRequest: () -> Unit,
    val content: @Composable ColumnScope.() -> Unit,
)

class ZomdroidPopupHostState {
    internal var currentEntry by mutableStateOf<PopupEntry?>(null)
        private set

    internal fun show(entry: PopupEntry) {
        currentEntry = entry
    }

    fun dismiss(ownerId: Any? = null) {
        val current = currentEntry ?: return
        if (ownerId == null || current.ownerId === ownerId) currentEntry = null
    }
}

val LocalZomdroidPopupHostState = staticCompositionLocalOf<ZomdroidPopupHostState?> { null }

@Composable
fun rememberZomdroidPopupHostState(): ZomdroidPopupHostState = remember { ZomdroidPopupHostState() }

/**
 * Draws every popup in the app's existing content layer. This is deliberate:
 * a system Popup creates another window and cannot reliably sample the page
 * Backdrop on all Android versions.
 */
@Composable
fun ZomdroidPopupHost(state: ZomdroidPopupHostState, modifier: Modifier = Modifier) {
    var hostBounds by remember { mutableStateOf<Rect?>(null) }
    var menuSize by remember { mutableStateOf(IntSize.Zero) }
    val entry = state.currentEntry
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(1000f)
            .onGloballyPositioned { hostBounds = it.boundsInWindow() },
    ) {
        val host = hostBounds
        val current = entry
        if (host == null || current == null) return@Box

        val anchorLeft = current.anchorBounds.left - host.left
        val anchorRight = current.anchorBounds.right - host.left
        val anchorTop = current.anchorBounds.top - host.top
        val anchorBottom = current.anchorBounds.bottom - host.top
        val horizontalPadding = with(density) { 8.dp.roundToPx() }
        val verticalGap = with(density) { 8.dp.roundToPx() }
        val popupWidth = menuSize.width
        val popupHeight = menuSize.height
        val hostWidth = host.width.roundToInt()
        val hostHeight = host.height.roundToInt()
        val desiredX = anchorRight - popupWidth
        val horizontalPaddingFloat = horizontalPadding.toFloat()
        val maxX = (hostWidth - popupWidth - horizontalPadding).coerceAtLeast(horizontalPadding).toFloat()
        val x = desiredX.coerceIn(horizontalPaddingFloat, maxX)
        val below = anchorBottom + verticalGap
        val above = anchorTop - popupHeight - verticalGap
        val maxY = (hostHeight - popupHeight - horizontalPadding).coerceAtLeast(horizontalPadding).toFloat()
        val y = when {
            below <= maxY -> below
            above >= horizontalPadding -> above
            else -> below.coerceIn(horizontalPaddingFloat, maxY)
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .clickable(
                    interactionSource = remember(current.ownerId) { MutableInteractionSource() },
                    indication = null,
                    onClick = current.onDismissRequest,
                ),
        )
        Box(
            modifier = Modifier
                .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
                .onSizeChanged { menuSize = it },
        ) {
            ZomdroidPopupSurface(modifier = current.modifier, content = current.content)
        }
    }
}

@Composable
fun BoxScope.ZomdroidPopupMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val host = LocalZomdroidPopupHostState.current
    val ownerId = remember { Any() }
    var anchorBounds by remember { mutableStateOf<Rect?>(null) }

    Box(
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned { anchorBounds = it.boundsInWindow() },
    )

    if (host == null) return
    SideEffect {
        val bounds = anchorBounds
        if (expanded && bounds != null) {
            host.show(PopupEntry(ownerId, bounds, modifier, onDismissRequest, content))
        } else {
            host.dismiss(ownerId)
        }
    }
    DisposableEffect(host, ownerId) {
        onDispose { host.dismiss(ownerId) }
    }
}

@Composable
private fun ZomdroidPopupSurface(
    modifier: Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val backdrop = LocalZomdroidBackdrop.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val liquid = isLiquidGlassFrontendEnabled() && !shouldReduceLiquidGlassEffects()
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.22f else 0.16f)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = if (isDark) 0.16f else 0.12f)
    val contentColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface

    if (liquid && backdrop != null) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Column(
                modifier = modifier
                    .widthIn(min = 220.dp, max = 320.dp)
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { PopupShape },
                        effects = {
                            vibrancy()
                            blur(2.dp.toPx())
                            lens(12.dp.toPx(), 18.dp.toPx())
                        },
                        onDrawSurface = { drawRect(surfaceColor) },
                    )
                    .clip(PopupShape)
                    .border(1.dp, borderColor, PopupShape)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
    } else {
        Surface(
            modifier = modifier.widthIn(min = 220.dp, max = 320.dp),
            shape = PopupShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, borderColor),
            shadowElevation = 10.dp,
            contentColor = contentColor,
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                content = content,
            )
        }
    }
}

@Composable
fun ZomdroidPopupMenuItem(
    text: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.52f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingIcon != null) {
            Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { leadingIcon() }
        }
        Box(Modifier.weight(1f)) {
            ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() }
        }
    }
}
