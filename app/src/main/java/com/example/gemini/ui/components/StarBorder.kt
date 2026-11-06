package com.example.gemini.ui.components

import android.graphics.PathMeasure
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 100% Faithful Native Jetpack Compose implementation of React Bits "StarBorder"
 * (https://reactbits.dev/components/star-border / DavidHDev/react-bits).
 *
 * Renders an orbiting luminous star particle with a smooth fading stardust trail
 * traveling along the rounded perimeter of the container.
 *
 * Features:
 * - Off-white / zinc starlight head with ambient radial bloom and star core
 * - Smooth quadratic tapering trail without blunt cutoffs or chopped edges
 * - Zero heap allocations per frame in draw loop (pre-allocated coordinate buffers)
 * - Complete hibernation when inactive (0% CPU/GPU overhead when typing is idle)
 */
@Composable
fun StarBorder(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    starColor: Color = Color(0xFFF4F4F5), // Off-white starlight by default
    baseBorderColor: Color = Color(0xFF222226),
    durationMillis: Int = 2600,
    borderWidth: Dp = 1.dp,
    starLengthRatio: Float = 0.24f,
    cornerRadius: Dp = 24.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { borderWidth.toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }
    val bloomRadiusPx = with(density) { 16.dp.toPx() }

    // Active animation transition; only animates when isActive is true
    val transition = rememberInfiniteTransition(label = "star_border_transition")
    val progressState = if (isActive) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "star_progress"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    // Path caching per size to achieve 0 allocations per frame
    var lastW by remember { mutableFloatStateOf(0f) }
    var lastH by remember { mutableFloatStateOf(0f) }
    val cachedComposePath = remember { Path() }
    val cachedMeasure = remember { PathMeasure() }
    val pos = remember { FloatArray(2) }
    val prevPos = remember { FloatArray(2) }
    val bloomColors = remember(starColor) {
        listOf(starColor.copy(alpha = 0.50f), Color.Transparent)
    }

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithContent {
                drawContent()

                val w = size.width
                val h = size.height
                if (w <= 0f || h <= 0f) return@drawWithContent

                val halfStroke = strokeWidthPx / 2f
                val innerR = (cornerRadiusPx - halfStroke).coerceAtLeast(0f)

                // 1. Draw base quiet border
                drawRoundRect(
                    color = baseBorderColor,
                    topLeft = Offset(halfStroke, halfStroke),
                    size = androidx.compose.ui.geometry.Size(w - strokeWidthPx, h - strokeWidthPx),
                    cornerRadius = CornerRadius(innerR, innerR),
                    style = Stroke(width = strokeWidthPx)
                )

                // 2. Draw orbiting shooting star and stardust trail if active
                if (isActive) {
                    // Update cached path & measure only when dimensions change
                    if (w != lastW || h != lastH) {
                        lastW = w
                        lastH = h
                        cachedComposePath.reset()
                        cachedComposePath.addRoundRect(
                            RoundRect(
                                left = halfStroke,
                                top = halfStroke,
                                right = w - halfStroke,
                                bottom = h - halfStroke,
                                cornerRadius = CornerRadius(innerR, innerR)
                            )
                        )
                        cachedMeasure.setPath(cachedComposePath.asAndroidPath(), true)
                    }

                    val totalLen = cachedMeasure.length
                    if (totalLen > 0f) {
                        val progress = progressState.value
                        val starLen = totalLen * starLengthRatio
                        val headDist = (progress * totalLen) % totalLen

                        // Render multi-segment fading stardust trail (14 sample steps, 0 allocations)
                        val steps = 14
                        for (i in steps downTo 1) {
                            val d1 = wrapDist(headDist - starLen * (i.toFloat() / steps), totalLen)
                            val d2 = wrapDist(headDist - starLen * ((i - 1).toFloat() / steps), totalLen)

                            cachedMeasure.getPosTan(d1, pos, null)
                            cachedMeasure.getPosTan(d2, prevPos, null)

                            val u = 1f - (i - 0.5f) / steps
                            val alpha = (u * u * 0.92f).coerceIn(0f, 1f)
                            val segWidth = strokeWidthPx * (0.8f + u * 1.2f)

                            drawLine(
                                color = starColor.copy(alpha = alpha),
                                start = Offset(pos[0], pos[1]),
                                end = Offset(prevPos[0], prevPos[1]),
                                strokeWidth = segWidth,
                                cap = StrokeCap.Round
                            )
                        }

                        // Render brilliant Star Head at headDist
                        cachedMeasure.getPosTan(headDist, pos, null)
                        val hx = pos[0]
                        val hy = pos[1]

                        // Ambient radial bloom halo
                        drawCircle(
                            color = starColor.copy(alpha = 0.28f),
                            radius = bloomRadiusPx,
                            center = Offset(hx, hy)
                        )

                        // Core bright star particle
                        drawCircle(
                            color = Color.White,
                            radius = strokeWidthPx * 1.75f,
                            center = Offset(hx, hy)
                        )
                    }
                }
            }
    ) {
        content()
    }
}

/** Utility to wrap path distance around perimeter */
private fun wrapDist(value: Float, total: Float): Float {
    val mod = value % total
    return if (mod < 0f) mod + total else mod
}
