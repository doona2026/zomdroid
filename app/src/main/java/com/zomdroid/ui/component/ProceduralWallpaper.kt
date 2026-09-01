package com.zomdroid.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*

// Adapted from WorkshopAndroidDownloader's procedural liquid-glass wallpaper under Apache-2.0.
@Composable
fun ZomdroidProceduralWallpaper(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .35f
    val stops = if (dark) listOf(Color(0xFF06131E), Color(0xFF0D2234), Color(0xFF081A28)) else listOf(Color(0xFFF3F8FF), Color(0xFFE6F1FF), Color(0xFFFFF1E9))
    val blue = if (dark) Color(0xFF0F8BFF).copy(alpha = .48f) else Color(0xFF2B8BFF).copy(alpha = .35f)
    val peach = if (dark) Color(0xFFFF9B6A).copy(alpha = .34f) else Color(0xFFFFB38C).copy(alpha = .28f)
    val mint = if (dark) Color(0xFF2DD5B6).copy(alpha = .22f) else Color(0xFF6EE0C9).copy(alpha = .22f)
    Canvas(modifier.background(stops.first())) {
        drawRect(Brush.linearGradient(stops, Offset.Zero, Offset(size.width, size.height)))
        drawCircle(Brush.radialGradient(listOf(blue, Color.Transparent)), size.minDimension * .52f, Offset(size.width * .15f, size.height * .18f))
        drawCircle(Brush.radialGradient(listOf(peach, Color.Transparent)), size.minDimension * .4f, Offset(size.width * .88f, size.height * .2f))
        drawCircle(Brush.radialGradient(listOf(mint, Color.Transparent)), size.minDimension * .44f, Offset(size.width * .72f, size.height * .82f))
        drawOval(Brush.radialGradient(listOf(blue.copy(alpha = blue.alpha * .42f), peach.copy(alpha = peach.alpha * .16f), Color.Transparent), Offset(size.width * .28f, size.height * .76f), size.minDimension * .58f), Offset(size.width * -.08f, size.height * .56f), Size(size.width * .92f, size.height * .24f), if (dark) .24f else .16f)
    }
}

