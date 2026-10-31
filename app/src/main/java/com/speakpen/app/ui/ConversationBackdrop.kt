package com.speakpen.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.isActive

/** 30 Hz draw-only ambient motion, with a continuous clock across pauses. No per-pixel noise. */
@Composable
fun ConversationBackdrop(active: Boolean, modifier: Modifier = Modifier) {
    val seconds = remember { mutableDoubleStateOf(0.0) }
    val motion = rememberAmbientMotionEnabled()
    LaunchedEffect(active, motion) {
        if (!active || !motion) return@LaunchedEffect
        var previous = 0L
        var rendered = 0L
        var elapsed = seconds.doubleValue
        while (isActive) withFrameNanos { now ->
            if (previous != 0L) elapsed += ((now - previous) / 1e9).coerceAtMost(.08)
            previous = now
            if (now - rendered >= 32_000_000L) { seconds.doubleValue = elapsed; rendered = now }
        }
    }
    Box(modifier.drawBehind {
        drawRect(Paper)
        val t = seconds.doubleValue * .12
        repeat(4) { i ->
            val seed = i * 7.31
            val nx = AmbientNoise.sample(seed + t, 1.8)
            val ny = AmbientNoise.sample(seed + 19.2, t * .83)
            val breath = AmbientNoise.sample(seed + t * .55, 38.1)
            val center = Offset(size.width * ((if (i % 2 == 0) .18f else .82f) + nx * .28f),
                size.height * ((if (i < 2) .22f else .78f) + ny * .17f))
            val tint = if (i % 2 == 0) SoftGreen else SoftBlue
            val alpha = .58f + breath * .14f
            val radius = size.maxDimension * (.48f + breath * .08f)
            drawRect(Brush.radialGradient(listOf(tint.copy(alpha = alpha), tint.copy(alpha = alpha * .55f), Color.Transparent),
                center = center, radius = radius))
        }
    })
}
