package com.example.gemini.ui.components

import android.graphics.PathMeasure
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Native Jetpack Compose implementation of React Bits "StarBorder"
 * (https://reactbits.dev/components/star-border).
 *
 * Renders an animated glowing star particle that orbits smoothly along the
 * curved perimeter of the container border.
 */
@Composable
fun StarBorder(
    modifier: Modifier = Modifier,
    isActive: Boolean = true,
    starColor: Color = Color(0xFF38BDF8),
    baseBorderColor: Color = Color(0xFF27272A),
    durationMillis: Int = 3800,
    borderWidth: Dp = 1.dp,
    starLengthRatio: Float = 0.22f,
    cornerRadius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    val density = LocalDensity.current
    val strokeWidthPx = with(density) { borderWidth.toPx() }
    val glowWidthPx = with(density) { (borderWidth * 3.5f).toPx() }
    val cornerRadiusPx = with(density) { cornerRadius.toPx() }

    val transition = rememberInfiniteTransition(label = "star_border_transition")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "star_progress"
    )

    val shape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }

    Box(
        modifier = modifier
            .clip(shape)
            .drawWithContent {
                drawContent()

                val w = size.width
                val h = size.height
                if (w > 0f && h > 0f) {
                    val composePath = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = strokeWidthPx / 2f,
                                top = strokeWidthPx / 2f,
                                right = w - strokeWidthPx / 2f,
                                bottom = h - strokeWidthPx / 2f,
                                cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                            )
                        )
                    }

                    // Draw base inactive border
                    drawPath(
                        path = composePath,
                        color = baseBorderColor,
                        style = Stroke(width = strokeWidthPx)
                    )

                    // Draw orbiting star segment if active
                    if (isActive) {
                        val androidPath = composePath.asAndroidPath()
                        val pathMeasure = PathMeasure(androidPath, true)
                        val totalLen = pathMeasure.length

                        if (totalLen > 0f) {
                            val starLen = totalLen * starLengthRatio
                            val startDist = progress * totalLen
                            val endDist = startDist + starLen

                            val segmentAndroidPath = android.graphics.Path()

                            if (endDist <= totalLen) {
                                pathMeasure.getSegment(startDist, endDist, segmentAndroidPath, true)
                            } else {
                                // Wraps around the corner
                                pathMeasure.getSegment(startDist, totalLen, segmentAndroidPath, true)
                                pathMeasure.getSegment(0f, endDist - totalLen, segmentAndroidPath, true)
                            }

                            val segmentComposePath = segmentAndroidPath.asComposePath()

                            // Layer 1: Ambient Halo Glow
                            drawPath(
                                path = segmentComposePath,
                                color = starColor.copy(alpha = 0.28f),
                                style = Stroke(width = glowWidthPx)
                            )

                            // Layer 2: Core Brilliant Star Particle
                            drawPath(
                                path = segmentComposePath,
                                color = starColor,
                                style = Stroke(width = strokeWidthPx * 1.5f)
                            )
                        }
                    }
                }
            }
    ) {
        content()
    }
}
