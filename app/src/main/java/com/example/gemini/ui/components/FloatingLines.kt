package com.example.gemini.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.*

enum class FloatingWaveType {
    TOP, MIDDLE, BOTTOM
}

data class FloatingWavePosition(
    val shift: Float = 0f,
    val lift: Float = 0f,
    val rotate: Float = 0f
)

private data class FloatingClickSwirl(
    val x: Float,
    val y: Float,
    var age: Float,
    val spin: Float
)

/**
 * Native Jetpack Compose implementation of React Bits "Floating Lines" (reactbits.dev/backgrounds/floating-lines).
 *
 * Renders ribbons of fine glowing lines that drift in harmonic waves, part smoothly
 * around the finger on drag, and curl into dynamic vortex eddies when tapped.
 */
@Composable
fun FloatingLines(
    modifier: Modifier = Modifier,
    linesGradient: List<Color> = listOf(
        Color(0xFFFFFFFF), // Brilliant core white
        Color(0xFF38BDF8), // Electric cyan
        Color(0xFF60A5FA), // Soft neon sky blue
        Color(0xFF3B82F6), // Royal cobalt
        Color(0xFF818CF8)  // Violet undertone
    ),
    enabledWaves: List<FloatingWaveType> = listOf(
        FloatingWaveType.TOP,
        FloatingWaveType.MIDDLE,
        FloatingWaveType.BOTTOM
    ),
    lineCount: Int = 8,
    lineDistance: Float = 5.5f,
    topWavePosition: FloatingWavePosition = FloatingWavePosition(shift = 8f, lift = 0.45f, rotate = -0.38f),
    middleWavePosition: FloatingWavePosition = FloatingWavePosition(shift = 4f, lift = 0.05f, rotate = 0.22f),
    bottomWavePosition: FloatingWavePosition = FloatingWavePosition(shift = 2f, lift = -0.55f, rotate = -0.85f),
    animationSpeed: Float = 1.0f,
    amplitude: Float = 1.0f,
    lineWidth: Dp = 1.2.dp,
    glow: Float = 1.0f,
    interactive: Boolean = true,
    bendRadius: Float = 0.55f,
    bendStrength: Float = -0.65f,
    mouseDamping: Float = 0.10f,
    clickSwirl: Boolean = true
) {
    val density = LocalDensity.current
    val coreStrokeWidthPx = with(density) { lineWidth.toPx() }
    val glowStrokeWidthPx = with(density) { (lineWidth * 4.5f).toPx() }

    // Pre-allocate paths to avoid allocations on render loop
    val maxTotalLines = enabledWaves.size * lineCount.coerceIn(1, 16)
    val pathPool = remember(maxTotalLines) { List(maxTotalLines) { Path() } }

    var pointerX by remember { mutableFloatStateOf(0f) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var targetPointerX by remember { mutableFloatStateOf(0f) }
    var targetPointerY by remember { mutableFloatStateOf(0f) }
    var pointerInfluence by remember { mutableFloatStateOf(0f) }
    var targetInfluence by remember { mutableFloatStateOf(0f) }
    var isPointerInside by remember { mutableStateOf(false) }

    val clickSwirls = remember { mutableStateListOf<FloatingClickSwirl>() }

    // Frame timing ticker for smooth continuous 60/120 fps wave motion
    var timeSeconds by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(animationSpeed) {
        var lastNanos = 0L
        while (true) {
            withInfiniteAnimationFrameMillis { frameMillis ->
                val currentNanos = frameMillis * 1_000_000L
                if (lastNanos != 0L) {
                    val dt = ((currentNanos - lastNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
                    timeSeconds += dt * animationSpeed

                    // Smooth pointer following
                    val follow = 1f - (1f - mouseDamping).pow(dt * 60f)
                    pointerX += (targetPointerX - pointerX) * follow
                    pointerY += (targetPointerY - pointerY) * follow
                    pointerInfluence += (targetInfluence - pointerInfluence) * follow

                    // Age out tap swirls
                    val iterator = clickSwirls.iterator()
                    while (iterator.hasNext()) {
                        val swirl = iterator.next()
                        swirl.age += dt
                        if (swirl.age > 2.5f) {
                            iterator.remove()
                        }
                    }
                }
                lastNanos = currentNanos
            }
        }
    }

    Box(
        modifier = modifier
            .pointerInput(interactive) {
                if (!interactive) return@pointerInput
                detectTapGestures(
                    onTap = { offset ->
                        if (clickSwirl) {
                            val normX = (offset.x / size.width) * 2f - 1f
                            val normY = (offset.y / size.height) * 2f - 1f
                            val spin = if (normX < 0) -1f else 1f
                            if (clickSwirls.size >= 4) {
                                clickSwirls.removeAt(0)
                            }
                            clickSwirls.add(FloatingClickSwirl(x = normX, y = normY, age = 0f, spin = spin))
                        }
                    }
                )
            }
            .pointerInput(interactive) {
                if (!interactive) return@pointerInput
                detectDragGestures(
                    onDragStart = { offset ->
                        isPointerInside = true
                        targetInfluence = 1f
                        targetPointerX = (offset.x / size.width) * 2f - 1f
                        targetPointerY = (offset.y / size.height) * 2f - 1f
                        pointerX = targetPointerX
                        pointerY = targetPointerY
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        targetInfluence = 1f
                        targetPointerX = (change.position.x / size.width) * 2f - 1f
                        targetPointerY = (change.position.y / size.height) * 2f - 1f
                    },
                    onDragEnd = {
                        isPointerInside = false
                        targetInfluence = 0f
                    },
                    onDragCancel = {
                        isPointerInside = false
                        targetInfluence = 0f
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            if (width <= 0f || height <= 0f) return@Canvas

            val aspect = width / height
            val numSteps = 72 // High resolution curve sampling
            val stepSize = (aspect * 2.4f) / (numSteps - 1)
            val startU = -aspect * 1.2f

            var pathIndex = 0

            for (waveType in enabledWaves) {
                val (wavePos, waveShape) = when (waveType) {
                    FloatingWaveType.TOP -> Pair(
                        topWavePosition,
                        WaveShapeConfig(offset = 1.0f, step = 0.20f, flip = 1.0f, drift = 0.6f)
                    )
                    FloatingWaveType.MIDDLE -> Pair(
                        middleWavePosition,
                        WaveShapeConfig(offset = 2.0f, step = 0.15f, flip = 0.0f, drift = 1.0f)
                    )
                    FloatingWaveType.BOTTOM -> Pair(
                        bottomWavePosition,
                        WaveShapeConfig(offset = 1.5f, step = 0.20f, flip = 0.0f, drift = 1.4f)
                    )
                }

                val effectiveLines = lineCount.coerceIn(1, 16)

                for (line in 0 until effectiveLines) {
                    if (pathIndex >= pathPool.size) break
                    val path = pathPool[pathIndex]
                    path.reset()

                    val tGradient = if (effectiveLines > 1) line.toFloat() / (effectiveLines - 1).toFloat() else 0f
                    val lineColor = sampleGradient(linesGradient, tGradient)

                    val lineOffset = waveShape.offset + waveShape.step * line
                    val along = (lineDistance * 0.01f) * line + wavePos.shift
                    val phase = along + lineOffset + timeSeconds * 0.16f
                    val amp = sin(lineOffset + timeSeconds * 0.24f) * 0.32f * amplitude

                    var isFirstPoint = true

                    for (s in 0 until numSteps) {
                        var u = startU + s * stepSize

                        // Harmonic undulation across screen
                        val sx = sin(u * 1.8f)
                        val cx = cos(u * 1.8f)
                        var y = (sx * cos(phase) + cx * sin(phase)) * amp + wavePos.lift

                        // Interactive touch repulsion (parting around finger)
                        if (pointerInfluence > 0.005f) {
                            val dx = u - pointerX
                            val dy = y - pointerY
                            val distSq = dx * dx + dy * dy
                            val invSigma = 1.0f / max(bendRadius * bendRadius, 1e-4f)
                            val near = exp(-distSq * invSigma) * pointerInfluence
                            val push = bendStrength * near
                            y += dy * push
                        }

                        // Tap swirl eddies
                        for (swirl in clickSwirls) {
                            val age = swirl.age
                            if (age in 0f..2.5f) {
                                val turn = 1.8f * smoothstep(0f, 0.25f, age) * exp(-max(age - 0.25f, 0f) * 1.3f) * swirl.spin
                                val spread = 0.38f * (1.0f + age * 0.55f)
                                val dx = u - swirl.x
                                val dy = y - swirl.y
                                val distSq = dx * dx + dy * dy
                                val angle = turn * exp(-distSq / (spread * spread))
                                val cosA = cos(angle)
                                val sinA = sin(angle)
                                u = swirl.x + (dx * cosA - dy * sinA)
                                y = swirl.y + (dx * sinA + dy * cosA)
                            }
                        }

                        // Spatial logarithmic warp (React Bits signature 3D twisting)
                        val (warpedX, warpedY) = warpCoords(u, y, wavePos.rotate, waveShape.flip)

                        // Convert to Canvas coordinates
                        val canvasX = (warpedX / (aspect * 2.2f) + 0.5f) * width
                        val canvasY = (warpedY * 0.5f + 0.5f) * height

                        if (isFirstPoint) {
                            path.moveTo(canvasX, canvasY)
                            isFirstPoint = false
                        } else {
                            path.lineTo(canvasX, canvasY)
                        }
                    }

                    // Layer 1: Ambient Bloom / Halo Glow
                    if (glow > 0.01f) {
                        drawPath(
                            path = path,
                            color = lineColor.copy(alpha = 0.14f * glow),
                            style = Stroke(width = glowStrokeWidthPx)
                        )
                    }

                    // Layer 2: Core Brilliant Line
                    drawPath(
                        path = path,
                        color = lineColor.copy(alpha = 0.88f),
                        style = Stroke(width = coreStrokeWidthPx)
                    )

                    pathIndex++
                }
            }
        }
    }
}

private data class WaveShapeConfig(
    val offset: Float,
    val step: Float,
    val flip: Float,
    val drift: Float
)

private fun warpCoords(x: Float, y: Float, rotate: Float, flip: Float): Pair<Float, Float> {
    val r = sqrt(x * x + y * y)
    val angle = rotate * ln(r + 1f)
    val cosA = cos(angle)
    val sinA = sin(angle)
    var rx = x * cosA + y * sinA
    val ry = y * cosA - x * sinA
    if (flip > 0.5f) {
        rx = -rx
    }
    return Pair(rx, ry)
}

private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
    val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun sampleGradient(gradient: List<Color>, fraction: Float): Color {
    if (gradient.isEmpty()) return Color.White
    if (gradient.size == 1) return gradient[0]

    val clamped = fraction.coerceIn(0f, 1f)
    val scaled = clamped * (gradient.size - 1)
    val index = floor(scaled).toInt().coerceIn(0, gradient.size - 2)
    val subFraction = scaled - index

    val c1 = gradient[index]
    val c2 = gradient[index + 1]

    return Color(
        red = c1.red + (c2.red - c1.red) * subFraction,
        green = c1.green + (c2.green - c1.green) * subFraction,
        blue = c1.blue + (c2.blue - c1.blue) * subFraction,
        alpha = c1.alpha + (c2.alpha - c1.alpha) * subFraction
    )
}
