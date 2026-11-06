package com.example.gemini.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.agent.agy.SavedAgyAccount
import com.example.gemini.domain.model.AgentKind

/**
 * React Bits inspired SpotlightCard component for Jetpack Compose.
 * Features an interactive radial spotlight that follows finger touches/drags,
 * an illuminated border glow ("edge catch"), and a subtle ambient breathing effect when idle.
 */
@Composable
fun SpotlightCard(
    modifier: Modifier = Modifier,
    spotlightColor: Color = Color(0xFF22C55E),
    backgroundColor: Color = Color(0xFF131317),
    borderColor: Color = Color(0xFF26262E),
    cornerRadius: Dp = 14.dp,
    intensity: Float = 0.16f,
    borderGlow: Float = 0.70f,
    content: @Composable ColumnScope.() -> Unit
) {
    var isTouching by remember { mutableStateOf(false) }
    var spotlightCenter by remember { mutableStateOf(Offset.Zero) }

    val presenceAnim by animateFloatAsState(
        targetValue = if (isTouching) 1f else 0.35f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "spotlight_presence"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isTouching = true
                    spotlightCenter = down.position

                    while (true) {
                        val event = awaitPointerEvent()
                        val move = event.changes.firstOrNull() ?: break
                        if (move.pressed) {
                            spotlightCenter = move.position
                        } else {
                            isTouching = false
                            break
                        }
                    }
                }
            }
            .drawWithContent {
                // 1. Draw base obsidian surface background
                drawRect(backgroundColor)

                // 2. Draw radial spotlight beam/light inside the card
                val center = if (isTouching) spotlightCenter else Offset(size.width / 2f, size.height / 2f)
                val dynamicRadius = (size.width.coerceAtLeast(size.height) * 0.95f).coerceAtLeast(100f)
                val glowAlpha = (presenceAnim * intensity).coerceIn(0f, 1f)
                if (glowAlpha > 0.005f) {
                    val surfaceBrush = Brush.radialGradient(
                        colors = listOf(
                            spotlightColor.copy(alpha = glowAlpha),
                            spotlightColor.copy(alpha = glowAlpha * 0.35f),
                            Color.Transparent
                        ),
                        center = center,
                        radius = dynamicRadius
                    )
                    drawRect(surfaceBrush)
                }

                // 3. Draw content (children composables)
                drawContent()

                // 4. Draw illuminated border edge catch
                val edgeAlpha = (presenceAnim * borderGlow).coerceIn(0f, 1f)
                val borderBrush = if (edgeAlpha > 0.01f) {
                    Brush.radialGradient(
                        colors = listOf(
                            spotlightColor.copy(alpha = edgeAlpha.coerceAtMost(0.95f)),
                            spotlightColor.copy(alpha = edgeAlpha * 0.35f),
                            borderColor.copy(alpha = 0.55f)
                        ),
                        center = center,
                        radius = dynamicRadius * 0.8f
                    )
                } else {
                    SolidColor(borderColor)
                }

                drawRoundRect(
                    brush = borderBrush,
                    cornerRadius = CornerRadius(cornerRadius.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            content()
        }
    }
}

/**
 * Rich account card built with SpotlightCard and React Bits 3-stat pill layout.
 * Displays real-time status (Active now or last used), remaining credits, and tier quota.
 */
@Composable
fun SpotlightAccountCard(
    account: SavedAgyAccount,
    isActive: Boolean,
    onSwitch: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentColor = if (isActive) Color(0xFF22C55E) else Color(0xFF94A3B8)
    val cardBg = if (isActive) Color(0xFF141716) else Color(0xFF111115)
    val cardBorder = if (isActive) Color(0xFF22C55E).copy(alpha = 0.35f) else Color(0xFF26262F)

    SpotlightCard(
        modifier = modifier.fillMaxWidth(),
        spotlightColor = accentColor,
        backgroundColor = cardBg,
        borderColor = cardBorder,
        cornerRadius = 14.dp,
        intensity = if (isActive) 0.22f else 0.14f,
        borderGlow = if (isActive) 0.85f else 0.55f
    ) {
        // Top Row: Avatar, Name/Email, Active badge or Switch/Remove
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (isActive) Color(0xFF22C55E) else AgentKind.AGY.accent()),
                contentAlignment = Alignment.Center
            ) {
                val initialChar = account.displayName.firstOrNull() ?: account.email.firstOrNull() ?: '•'
                Text(
                    initialChar.uppercaseChar().toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        account.displayName.ifBlank { account.email },
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (account.userTier.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Text(
                                account.userTier,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(1.dp))
                Text(
                    account.email,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.width(8.dp))

            if (isActive) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF22C55E).copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, Color(0xFF22C55E).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Active",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF22C55E)
                        )
                    }
                }
            } else {
                Surface(
                    onClick = onSwitch,
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFFE8E5DF),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        "Switch",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Outlined.Close,
                        "Remove account",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // Middle Row: 3-Stat Pill Box (React Bits stats layout)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color(0xFF09090C).copy(alpha = 0.7f),
            border = BorderStroke(1.dp, Color(0xFF202026)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stat 1: STATUS / LAST USED
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        "STATUS",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isActive) {
                            Box(Modifier.size(5.dp).clip(CircleShape).background(Color(0xFF22C55E)))
                            Spacer(Modifier.width(4.dp))
                            Text("Active now", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E))
                        } else {
                            Text(formatLastUsedTime(account.lastUsedTimestamp), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.85f))
                        }
                    }
                }

                // Divider
                Box(Modifier.width(1.dp).height(22.dp).background(Color(0xFF24242C)))

                // Stat 2: PROMPT CREDITS
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "PROMPT CREDITS",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        account.availablePromptCredits?.toString() ?: "Available",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }

                // Divider
                Box(Modifier.width(1.dp).height(22.dp).background(Color(0xFF24242C)))

                // Stat 3: FLOW / TIER
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "FLOW / TIER",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        letterSpacing = 0.6.sp
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        account.availableFlowCredits?.toString() ?: account.userTier.ifBlank { "Standard" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun formatLastUsedTime(timestamp: Long): String {
    if (timestamp <= 0L) return "Recent"
    val diff = System.currentTimeMillis() - timestamp
    return when {
        diff < 60_000L -> "Just now"
        diff < 3600_000L -> "${diff / 60_000L}m ago"
        diff < 86400_000L -> "${diff / 3600_000L}h ago"
        else -> "${diff / 86400_000L}d ago"
    }
}
