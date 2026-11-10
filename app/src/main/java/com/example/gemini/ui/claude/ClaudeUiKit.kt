package com.example.gemini.ui.claude

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.theme.QuotaAmber
import com.example.gemini.theme.QuotaGreen
import com.example.gemini.theme.QuotaRed
import com.example.gemini.theme.graventTokens
import com.example.gemini.theme.isAppInDarkTheme

/** Uppercase group label used to separate Antigravity / Claude Code / App settings. */
@Composable
fun SettingsGroupHeader(title: String, accent: Color, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(top = 14.dp, bottom = 4.dp, start = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(1.5.dp)).background(accent))
            Spacer(Modifier.width(8.dp))
            Text(
                title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                letterSpacing = 1.2.sp
            )
        }
        if (subtitle != null) {
            Text(
                subtitle,
                fontSize = 11.sp,
                color = graventTokens().textMuted,
                modifier = Modifier.padding(start = 14.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun claudeCardColor(): Color = graventTokens().cardBg

/** Navigation card matching the existing settings cards (Monochrome B&W standard). */
@Composable
fun ClaudeNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    badgeColor: Color = Color.Unspecified,
    tint: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    val t = graventTokens()
    val resolvedTint = if (tint == Color.Unspecified) t.textSecondary else tint
    val resolvedBadgeColor = if (badgeColor == Color.Unspecified) t.textPrimary else badgeColor
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(10.dp),
        color = t.cardBg,
        border = BorderStroke(1.dp, t.border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = resolvedTint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = t.textPrimary,
                    letterSpacing = (-0.2).sp
                )
                Spacer(Modifier.height(1.5.dp))
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = t.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = t.chipBg,
                    border = BorderStroke(1.dp, t.border)
                ) {
                    Text(
                        text = badge.uppercase(),
                        fontSize = 9.5.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = resolvedBadgeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = t.textMuted,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/** Plain content card. */
@Composable
fun ClaudeCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val t = graventTokens()
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = t.cardBg),
        border = BorderStroke(1.dp, t.border)
    ) { Column(Modifier.padding(14.dp)) { content() } }
}

fun usageColor(fraction: Double): Color = when {
    fraction >= 0.9 -> QuotaRed
    fraction >= 0.7 -> QuotaAmber
    else -> QuotaGreen
}

/** One plan-limit bar: label, percent used, reset time. */
@Composable
fun UsageBar(label: String, usedFraction: Double, resetText: String?, modifier: Modifier = Modifier, compact: Boolean = false) {
    val color = usageColor(usedFraction)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = if (compact) 10.5.sp else 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
            Text(
                "${(usedFraction * 100).toInt()}% used" + (resetText?.let { " · resets $it" } ?: ""),
                fontSize = if (compact) 10.sp else 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier.fillMaxWidth().height(if (compact) 4.dp else 6.dp).clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        ) {
            Box(
                Modifier.fillMaxWidth(usedFraction.toFloat().coerceIn(0f, 1f)).height(if (compact) 4.dp else 6.dp)
                    .clip(RoundedCornerShape(3.dp)).background(color)
            )
        }
    }
}

/** Small pill used in the chat controls strip. */
@Composable
fun ControlChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    color: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val t = graventTokens()
    Surface(
        modifier = modifier.clip(RoundedCornerShape(8.dp)).then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(8.dp),
        color = if (highlighted) t.accent else t.chipBg,
        border = BorderStroke(1.dp, if (highlighted) t.accent else t.border)
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    icon,
                    null,
                    tint = if (highlighted) t.onAccent else color,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (highlighted) t.onAccent else color,
                maxLines = 1
            )
        }
    }
}

/** "in 2h 14m" / "Tue 09:00" for an ISO-8601 or epoch-seconds reset time. */
fun formatReset(iso: String?, epochSeconds: Long? = null): String? {
    val millis = epochSeconds?.let { it * 1000 } ?: iso?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
    ?: return null
    val diff = millis - System.currentTimeMillis()
    if (diff <= 0) return "now"
    val mins = diff / 60_000
    return when {
        mins < 60 -> "in ${mins}m"
        mins < 24 * 60 -> "in ${mins / 60}h ${mins % 60}m"
        else -> java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))
    }
}

/** Human label for a Claude permission mode. */
fun permissionModeLabel(mode: String): String = when (mode) {
    "default" -> "Manual"
    "acceptEdits" -> "Accept edits"
    "plan" -> "Plan"
    "auto" -> "Auto"
    "dontAsk" -> "Don't ask"
    "bypassPermissions" -> "Bypass"
    else -> mode
}

fun permissionModeDescription(mode: String): String = when (mode) {
    "default" -> "Ask before edits and commands"
    "acceptEdits" -> "Apply file edits without asking"
    "plan" -> "Explore and propose a plan, no changes"
    "auto" -> "Claude decides what is safe to run"
    "dontAsk" -> "Never ask; deny anything not pre-allowed"
    "bypassPermissions" -> "Allow everything (sandboxes only)"
    else -> ""
}

fun effortLabel(level: String?): String = when (level) {
    null -> "Default"
    "xhigh" -> "Extra high"
    else -> level.replaceFirstChar { it.uppercase() }
}
