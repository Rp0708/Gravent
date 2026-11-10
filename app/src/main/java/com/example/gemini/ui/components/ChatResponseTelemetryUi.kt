package com.example.gemini.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.domain.model.TokenUsage
import com.example.gemini.domain.model.TurnDiffStats
import com.example.gemini.theme.graventTokens
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

internal fun formatCompactTokenCount(n: Int): String {
    return when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fm", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fk", n / 1_000.0)
        else -> n.toString()
    }
}

internal fun formatTelemetryDuration(ms: Long): String {
    return when {
        ms <= 0L -> ""
        ms < 1000L -> "${ms}ms"
        ms < 60_000L -> String.format(Locale.US, "%.1fs", ms / 1000.0)
        ms < 3_600_000L -> {
            val mins = ms / 60_000L
            val secs = (ms / 1000L) % 60L
            if (secs > 0L) "${mins}m ${secs}s" else "${mins}m"
        }
        ms < 86_400_000L -> {
            val hours = ms / 3_600_000L
            val mins = (ms % 3_600_000L) / 60_000L
            if (mins > 0L) "${hours}h ${mins}m" else "${hours}h"
        }
        else -> {
            val days = ms / 86_400_000L
            val hours = (ms % 86_400_000L) / 3_600_000L
            if (hours > 0L) "${days}d ${hours}h" else "${days}d"
        }
    }
}

internal fun formatFriendlyModelName(
    rawModelId: String?,
    resolver: ((String) -> String)? = null
): String {
    val trimmed = rawModelId?.trim().orEmpty()
    if (trimmed.isEmpty()) return "Default model"
    val resolved = resolver?.invoke(trimmed)?.trim()
    if (!resolved.isNullOrBlank() && resolved != trimmed && !resolved.startsWith("MODEL_PLACEHOLDER_")) {
        return resolved
    }
    return when {
        trimmed.startsWith("MODEL_PLACEHOLDER_") -> {
            val code = trimmed.removePrefix("MODEL_PLACEHOLDER_").replace('_', ' ').trim()
            if (code.isBlank()) "Gemini Model" else "Model ($code)"
        }
        trimmed.contains('/') -> trimmed.substringAfterLast('/')
        else -> trimmed
    }
}

internal fun formatTelemetryShortTime(timestampMs: Long): String {
    if (timestampMs <= 0L) return ""
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestampMs }
    val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    val sameYear = now.get(Calendar.YEAR) == target.get(Calendar.YEAR)
    val pattern = when {
        sameDay -> "h:mm a"
        sameYear -> "MMM d, h:mm a"
        else -> "MMM d, yyyy"
    }
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestampMs))
}

internal fun formatTelemetryFullTimestamp(timestampMs: Long): String {
    if (timestampMs <= 0L) return "Unknown"
    return SimpleDateFormat("MMM d, yyyy · h:mm:ss a", Locale.getDefault()).format(Date(timestampMs))
}

internal fun formatUsdEstimate(costUsd: Double?): String? {
    if (costUsd == null || costUsd <= 0.0) return null
    return if (costUsd < 0.01) {
        String.format(Locale.US, "$%.4f", costUsd)
    } else {
        String.format(Locale.US, "$%.2f", costUsd)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResponseTelemetryPill(
    usage: TokenUsage?,
    createdAtMs: Long = 0L,
    diffStats: TurnDiffStats? = null,
    onInspectDetails: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val hasDiff = diffStats != null && (diffStats.additions > 0 || diffStats.deletions > 0 || diffStats.filesChanged > 0)
    val hasTimestamp = createdAtMs > 0L
    if (usage == null && !hasDiff && !hasTimestamp) return

    val t = graventTokens()
    val pillBg = if (t.isDark) Color(0xFF131317) else t.cardBg
    val pillBorder = if (t.isDark) Color(0xFF26262C) else t.border
    val mutedColor = t.textSecondary
    val valueColor = t.textPrimary
    val inputAccent = if (t.isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val cacheColor = t.warnAmber
    val addColor = t.gitAdd
    val delColor = t.gitDel

    val cachePct = if (usage != null && usage.cachedTokens > 0 && (usage.promptTokens + usage.cachedTokens) > 0) {
        ((usage.cachedTokens.toDouble() / (usage.promptTokens + usage.cachedTokens)) * 100).toInt().coerceIn(0, 100)
    } else 0

    val timeLabel = remember(createdAtMs) { formatTelemetryShortTime(createdAtMs) }
    val durationLabel = remember(usage?.durationMs) { formatTelemetryDuration(usage?.durationMs ?: 0L) }

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (onInspectDetails != null) {
                    Modifier.clickable(onClick = onInspectDetails)
                } else Modifier
            ),
        shape = RoundedCornerShape(8.dp),
        color = pillBg,
        border = BorderStroke(0.8.dp, pillBorder)
    ) {
        FlowRow(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
            itemVerticalAlignment = Alignment.CenterVertically
        ) {
            if (usage != null && (usage.promptTokens > 0 || usage.outputTokens > 0 || usage.totalTokens > 0)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(inputAccent.copy(alpha = 0.85f), CircleShape)
                    )
                    Text(
                        text = "in",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = mutedColor
                    )
                    Text(
                        text = "${if (usage.isEstimated) "~" else ""}${formatCompactTokenCount(usage.promptTokens)}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = valueColor
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "out",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = mutedColor
                    )
                    Text(
                        text = formatCompactTokenCount(usage.outputTokens),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = valueColor
                    )
                }

                if (usage.cachedTokens > 0) {
                    TelemetryDotSeparator(mutedColor)
                    Text(
                        text = "${cachePct}% cached",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = cacheColor
                    )
                }
            }

            if (hasDiff && diffStats != null) {
                if (usage != null) TelemetryDotSeparator(mutedColor)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (diffStats.additions > 0) {
                        Text(
                            text = "+${diffStats.additions}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = addColor
                        )
                    }
                    if (diffStats.deletions > 0) {
                        Text(
                            text = "-${diffStats.deletions}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = delColor
                        )
                    }
                    if (diffStats.additions == 0 && diffStats.deletions == 0 && diffStats.filesChanged > 0) {
                        Text(
                            text = "${diffStats.filesChanged}f",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = mutedColor
                        )
                    }
                }
            }

            if (durationLabel.isNotEmpty()) {
                TelemetryDotSeparator(mutedColor)
                Text(
                    text = durationLabel,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = mutedColor
                )
            }

            if (timeLabel.isNotEmpty()) {
                if (usage != null || durationLabel.isNotEmpty()) {
                    TelemetryDotSeparator(mutedColor)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Schedule,
                        contentDescription = null,
                        tint = mutedColor,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = timeLabel,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.SansSerif,
                        color = mutedColor
                    )
                }
            }

            if (onInspectDetails != null) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View chat & token details",
                    tint = mutedColor,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
    }
}

/**
 * Compact `+additions -deletions [i]` pill displayed next to responses that modified repository files.
 * Tapping opens [TurnGitInfoDialog] showing detailed Git operations and diffs for that turn.
 */
@Composable
fun TurnGitOperationsChip(
    diffStats: TurnDiffStats?,
    onInspectGitDiff: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (diffStats == null || (diffStats.additions <= 0 && diffStats.deletions <= 0 && diffStats.filesChanged <= 0)) return

    val t = graventTokens()
    val chipBg = t.gitAddBg
    val chipBorder = t.gitAddBorder
    val dividerColor = t.subtleBorder
    val addColor = t.gitAdd
    val delColor = t.gitDel
    val mutedColor = t.textSecondary

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onInspectGitDiff),
        shape = RoundedCornerShape(8.dp),
        color = chipBg,
        border = BorderStroke(0.8.dp, chipBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (diffStats.additions > 0) {
                Text(
                    text = "+${diffStats.additions}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = addColor
                )
            }
            if (diffStats.deletions > 0) {
                Text(
                    text = "-${diffStats.deletions}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = delColor
                )
            }
            if (diffStats.additions == 0 && diffStats.deletions == 0 && diffStats.filesChanged > 0) {
                Text(
                    text = "${diffStats.filesChanged} ops",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = mutedColor
                )
            } else if (diffStats.filesChanged > 0) {
                Text(
                    text = "· ${diffStats.filesChanged}f",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = mutedColor
                )
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(11.dp)
                    .background(dividerColor)
            )
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Prompt Git diff details",
                tint = t.accent,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun TelemetryDotSeparator(color: Color) {
    Text(
        text = "·",
        fontSize = 11.sp,
        color = color.copy(alpha = 0.55f)
    )
}
