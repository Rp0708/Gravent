package com.example.gemini.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.theme.graventPrimaryButtonColors
import com.example.gemini.theme.graventTokens

/**
 * Shared semantic tone mapped deterministically to accessible Dark & Light theme colors.
 */
enum class GraventTone {
    NEUTRAL,
    ACCENT,
    INFO,
    SUCCESS,
    WARNING,
    ERROR
}

data class GraventToneColors(
    val foreground: Color,
    val background: Color,
    val border: Color
)

@Composable
@ReadOnlyComposable
fun graventToneColors(tone: GraventTone): GraventToneColors {
    val t = graventTokens()
    return when (tone) {
        GraventTone.NEUTRAL -> GraventToneColors(
            foreground = t.textSecondary,
            background = t.chipBg,
            border = t.border
        )
        GraventTone.ACCENT -> GraventToneColors(
            foreground = t.accent,
            background = t.accentSoftBg,
            border = t.accent.copy(alpha = if (t.isDark) 0.30f else 0.22f)
        )
        GraventTone.INFO -> GraventToneColors(
            foreground = t.infoAccent,
            background = t.infoSoftBg,
            border = t.infoAccent.copy(alpha = if (t.isDark) 0.35f else 0.28f)
        )
        GraventTone.SUCCESS -> GraventToneColors(
            foreground = t.successAccent,
            background = t.successSoftBg,
            border = t.gitAddBorder
        )
        GraventTone.WARNING -> GraventToneColors(
            foreground = t.warnAmber,
            background = t.warnSoftBg,
            border = t.warnAmber.copy(alpha = if (t.isDark) 0.35f else 0.28f)
        )
        GraventTone.ERROR -> GraventToneColors(
            foreground = t.errorAccent,
            background = t.errorSoftBg,
            border = t.errorAccent.copy(alpha = if (t.isDark) 0.38f else 0.30f)
        )
    }
}

/**
 * Unified, high-contrast status badge used consistently across Chat, Drawer, IDE, Browser, and Settings.
 */
@Composable
fun GraventBadge(
    text: String,
    modifier: Modifier = Modifier,
    tone: GraventTone = GraventTone.NEUTRAL,
    icon: ImageVector? = null,
    leadingDot: Boolean = false,
    monospace: Boolean = false
) {
    val colors = graventToneColors(tone)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = colors.background,
        border = BorderStroke(0.75.dp, colors.border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (leadingDot) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(colors.foreground)
                )
            }
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colors.foreground,
                    modifier = Modifier.size(11.dp)
                )
            }
            Text(
                text = text,
                fontSize = 10.5.sp,
                fontFamily = if (monospace) FontFamily.Monospace else FontFamily.SansSerif,
                fontWeight = FontWeight.SemiBold,
                color = colors.foreground,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * Consistent uppercase section label with optional subtitle and trailing action slot.
 */
@Composable
fun GraventSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    showDot: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
) {
    val t = graventTokens()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDot) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(t.accent)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = title.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.85.sp,
                color = t.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailing != null) {
                trailing()
            }
        }
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = t.textSecondary
            )
        }
    }
}

/**
 * Standard Obsidian/Daylight card surface across screens (`14.dp` radius, `1.dp` border).
 */
@Composable
fun GraventCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 14.dp,
    containerColor: Color? = null,
    borderColor: Color? = null,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val t = graventTokens()
    val shape = RoundedCornerShape(cornerRadius)
    val bg = containerColor ?: t.cardBg
    val border = BorderStroke(1.dp, borderColor ?: t.border)
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = bg,
            border = border
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = bg,
            border = border
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

/**
 * Calm, centered empty state shared across Notes, File Manager, IDE Sidebar, and Logs.
 */
@Composable
fun GraventEmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val t = graventTokens()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(t.elevatedBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = t.textSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = t.textPrimary,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                fontSize = 12.5.sp,
                lineHeight = 18.sp,
                color = t.textSecondary,
                textAlign = TextAlign.Center
            )
        }
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(10.dp),
                colors = graventPrimaryButtonColors(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(text = actionLabel, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Compact error or notice banner replacing raw monospace stderr dumps.
 */
@Composable
fun GraventErrorBanner(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    tone: GraventTone = GraventTone.ERROR,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    val t = graventTokens()
    val colors = graventToneColors(tone)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.background,
        border = BorderStroke(1.dp, colors.border)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = title,
                tint = colors.foreground,
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(18.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.foreground
                )
                if (message.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = message,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = t.textPrimary
                    )
                }
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = actionLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.foreground
                    )
                }
            }
        }
    }
}

/**
 * Structured two-column key-value row used in diagnostics, inspect modals, and settings summaries.
 */
@Composable
fun GraventKeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    monoValue: Boolean = false,
    valueColor: Color? = null
) {
    val t = graventTokens()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            color = t.textSecondary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            fontSize = if (monoValue) 12.sp else 12.5.sp,
            fontFamily = if (monoValue) FontFamily.Monospace else FontFamily.SansSerif,
            fontWeight = FontWeight.Medium,
            color = valueColor ?: t.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

private enum class ParsedLogSeverity { ERROR, WARN, SUCCESS, INFO, DIFF_ADD, DIFF_DEL, DIFF_HUNK, PLAIN }

private data class ParsedLogLine(
    val timestamp: String?,
    val severity: ParsedLogSeverity,
    val message: String
)

private val LOG_TIMESTAMP_PREFIX = Regex("""^(\[?\d{2}:\d{2}(?::\d{2})?(?:\.\d+)?\]?)\s+(.*)$""")

private fun classifyLogLine(raw: String): ParsedLogLine {
    val tsMatch = LOG_TIMESTAMP_PREFIX.find(raw)
    val timestamp = tsMatch?.groupValues?.get(1)?.trim('[', ']')
    val body = tsMatch?.groupValues?.get(2) ?: raw
    val severity = when {
        body.startsWith("@@ ") || body.startsWith("diff --git") -> ParsedLogSeverity.DIFF_HUNK
        (body.startsWith("+") && !body.startsWith("+++")) -> ParsedLogSeverity.DIFF_ADD
        (body.startsWith("-") && !body.startsWith("---")) -> ParsedLogSeverity.DIFF_DEL
        body.contains("❌") || body.contains("ERROR", ignoreCase = true) ||
            body.contains("FATAL", ignoreCase = true) || body.contains("Exception") ||
            body.contains("failed", ignoreCase = true) -> ParsedLogSeverity.ERROR
        body.contains("⚠") || body.contains("WARN", ignoreCase = true) -> ParsedLogSeverity.WARN
        body.contains("✅") || body.contains("✓") || body.contains("🎉") ||
            body.contains("SUCCESS", ignoreCase = true) || body.contains("Connected", ignoreCase = true) -> ParsedLogSeverity.SUCCESS
        body.startsWith("INFO", ignoreCase = true) || body.startsWith("[") || body.startsWith("$ ") -> ParsedLogSeverity.INFO
        else -> ParsedLogSeverity.PLAIN
    }
    return ParsedLogLine(timestamp = timestamp, severity = severity, message = body)
}

/**
 * Context-aware log and diagnostic console that parses raw line streams into severity-tinted
 * rows with optional timestamp alignment instead of rendering unformatted monospace walls.
 */
@Composable
fun GraventLogConsole(
    lines: List<String>,
    modifier: Modifier = Modifier,
    title: String? = null,
    emptyText: String = "No log entries yet.",
    maxVisibleLines: Int = 80
) {
    val context = LocalContext.current
    val t = graventTokens()
    val parsedLines = remember(lines, maxVisibleLines) {
        lines.takeLast(maxVisibleLines).map(::classifyLogLine)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = t.codeBg,
        border = BorderStroke(1.dp, t.border)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!title.isNullOrBlank() || lines.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(t.codeHeaderBg)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = (title ?: "LOG OUTPUT").uppercase(),
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.7.sp,
                        color = t.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    if (lines.isNotEmpty()) {
                        Text(
                            text = "${lines.size} lines",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = t.textMuted
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText(title ?: "Log", lines.joinToString("\n")))
                                Toast.makeText(context, "Copied log output", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Copy log",
                                tint = t.textSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            if (parsedLines.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = emptyText,
                        fontSize = 12.sp,
                        color = t.textMuted
                    )
                }
            } else {
                SelectionContainer {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        parsedLines.forEach { entry ->
                            val rowColor = when (entry.severity) {
                                ParsedLogSeverity.ERROR -> t.errorAccent
                                ParsedLogSeverity.WARN -> t.warnAmber
                                ParsedLogSeverity.SUCCESS -> t.successAccent
                                ParsedLogSeverity.INFO -> t.infoAccent
                                ParsedLogSeverity.DIFF_ADD -> t.gitAdd
                                ParsedLogSeverity.DIFF_DEL -> t.gitDel
                                ParsedLogSeverity.DIFF_HUNK -> t.infoAccent
                                ParsedLogSeverity.PLAIN -> t.inlineCodeText
                            }
                            val bgTint = when (entry.severity) {
                                ParsedLogSeverity.ERROR -> t.errorSoftBg.copy(alpha = 0.45f)
                                ParsedLogSeverity.DIFF_ADD -> t.gitAddBg.copy(alpha = 0.65f)
                                ParsedLogSeverity.DIFF_DEL -> t.errorSoftBg.copy(alpha = 0.45f)
                                else -> Color.Transparent
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(bgTint, RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 1.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (entry.timestamp != null) {
                                    Text(
                                        text = entry.timestamp,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        lineHeight = 15.5.sp,
                                        color = t.textMuted
                                    )
                                }
                                Text(
                                    text = entry.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    lineHeight = 15.5.sp,
                                    color = rowColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Shared accessible file-extension visual tone used across File Citations, File Manager, and IDE Tree.
 */
fun resolveUnifiedFileAccent(fileName: String, isDark: Boolean): Color {
    val lower = fileName.lowercase()
    if (lower == "dockerfile" || lower == "makefile") {
        return if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
    }
    return when (lower.substringAfterLast('.', "")) {
        "kt", "kts" -> if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9)
        "java" -> if (isDark) Color(0xFFFB923C) else Color(0xFFC2410C)
        "py" -> if (isDark) Color(0xFFFACC15) else Color(0xFFA16207)
        "ts", "tsx" -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0369A1)
        "js", "jsx", "mjs", "cjs" -> if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
        "go", "rs", "c", "cpp", "cc", "h", "hpp", "swift", "dart" -> if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)
        "html", "htm", "xml", "css", "scss", "sass", "svg" -> if (isDark) Color(0xFFFB7185) else Color(0xFFBE123C)
        "json", "yaml", "yml", "toml", "proto" -> if (isDark) Color(0xFF2DD4BF) else Color(0xFF0F766E)
        "sh", "bash", "zsh", "fish" -> if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
        "png", "jpg", "jpeg", "webp", "gif", "bmp" -> if (isDark) Color(0xFFC084FC) else Color(0xFF7E22CE)
        "md", "markdown", "txt", "rst" -> if (isDark) Color(0xFFA1A1AA) else Color(0xFF52525B)
        else -> if (isDark) Color(0xFF93C5FD) else Color(0xFF2563EB)
    }
}

fun resolveUnifiedFileIcon(fileName: String): ImageVector {
    val lower = fileName.lowercase()
    if (lower == "dockerfile" || lower == "makefile") return Icons.Outlined.Terminal
    return when (lower.substringAfterLast('.', "")) {
        "kt", "kts", "java", "py", "ts", "tsx", "js", "jsx", "mjs", "cjs",
        "go", "rs", "c", "cpp", "cc", "h", "hpp", "swift", "dart",
        "html", "htm", "xml", "css", "scss", "sass", "svg" -> Icons.Outlined.Code
        "json", "yaml", "yml", "toml", "proto" -> Icons.Outlined.DataObject
        "sh", "bash", "zsh", "fish" -> Icons.Outlined.Terminal
        "png", "jpg", "jpeg", "webp", "gif", "bmp" -> Icons.Outlined.Image
        else -> Icons.Outlined.Description
    }
}
