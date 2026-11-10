package com.example.gemini.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Analytics
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Functions
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemini.domain.model.AgentKind
import com.example.gemini.domain.model.ChatMessage
import com.example.gemini.domain.model.Conversation
import com.example.gemini.domain.model.MessageRole
import com.example.gemini.domain.model.TurnDiffStats
import com.example.gemini.theme.ClaudeTerracotta
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ChatTelemetryDialog(
    conversation: Conversation?,
    messages: List<ChatMessage>,
    focusedMessage: ChatMessage? = null,
    focusedTurnDiffStats: TurnDiffStats? = focusedMessage?.diffStats,
    conversationDiffStats: TurnDiffStats? = null,
    onViewRawPayload: ((String) -> Unit)? = null,
    onOpenSystemPrompt: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.US) }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val inputColor = if (isDark) Color(0xFF60A5FA) else Color(0xFF1D4ED8)
    val outputColor = if (isDark) Color(0xFF4ADE80) else Color(0xFF15803D)
    val cacheColor = if (isDark) Color(0xFFFBBF24) else Color(0xFFB45309)
    val cacheWriteColor = if (isDark) Color(0xFFA78BFA) else Color(0xFF6D28D9)
    val delColor = if (isDark) Color(0xFFF87171) else Color(0xFFB91C1C)
    val surfaceBg = if (isDark) Color(0xFF111114) else MaterialTheme.colorScheme.surface
    val cardBg = if (isDark) Color(0xFF18181D) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.48f)
    val subtleBorder = if (isDark) Color(0xFF27272D) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.11f)
    val mutedText = if (isDark) Color(0xFF8A8A96) else MaterialTheme.colorScheme.onSurfaceVariant

    // Aggregate token statistics across all messages in the chat
    val turnsWithUsage = remember(messages) { messages.mapNotNull { it.tokenUsage } }
    val totalPromptTokens = remember(turnsWithUsage) { turnsWithUsage.sumOf { it.promptTokens } }
    val totalOutputTokens = remember(turnsWithUsage) { turnsWithUsage.sumOf { it.outputTokens } }
    val totalCachedTokens = remember(turnsWithUsage) { turnsWithUsage.sumOf { it.cachedTokens } }
    val totalCreationTokens = remember(turnsWithUsage) { turnsWithUsage.sumOf { it.cacheCreationTokens } }
    val totalCombinedTokens = totalPromptTokens + totalOutputTokens
    val totalDurationMs = remember(turnsWithUsage) { turnsWithUsage.sumOf { it.durationMs } }
    val totalCostUsd = remember(turnsWithUsage) {
        val costs = turnsWithUsage.mapNotNull { it.costUsd }
        if (costs.isNotEmpty()) costs.sum() else null
    }

    val userMessagesCount = remember(messages) { messages.count { it.role == MessageRole.USER } }
    val assistantMessagesCount = remember(messages) { messages.count { it.role == MessageRole.ASSISTANT } }
    val toolsExecutedCount = remember(messages) { messages.sumOf { it.toolCalls.size } }

    val cacheEfficiencyPct = if (totalCachedTokens > 0 && (totalPromptTokens + totalCachedTokens) > 0) {
        ((totalCachedTokens.toDouble() / (totalPromptTokens + totalCachedTokens)) * 100).toInt().coerceIn(0, 100)
    } else 0

    val agentLabel = when (conversation?.agent) {
        AgentKind.CLAUDE -> "Claude Code"
        AgentKind.COPILOT -> "GitHub Copilot"
        else -> "Antigravity"
    }

    val rawWorkspace = conversation?.workspaceUri?.removePrefix("file://")?.trimEnd('/').orEmpty()
    val shortWorkspace = remember(rawWorkspace) { abbreviateWorkspacePath(rawWorkspace) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = surfaceBg,
            border = BorderStroke(1.dp, subtleBorder),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(ClaudeTerracotta.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Analytics,
                            contentDescription = null,
                            tint = ClaudeTerracotta,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = conversation?.title?.takeIf { it.isNotBlank() } ?: "Chat Usage & Telemetry",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$agentLabel · ${conversation?.modelId?.ifBlank { "Default model" } ?: "Current Chat"}",
                            fontSize = 11.5.sp,
                            color = mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = mutedText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = subtleBorder)
                Spacer(modifier = Modifier.height(12.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Section 1: Focused Response Breakdown (when opened from a response telemetry pill)
                    if (focusedMessage != null) {
                        val u = focusedMessage.tokenUsage
                        val turnCachePct = if (u != null && u.cachedTokens > 0 && (u.promptTokens + u.cachedTokens) > 0) {
                            ((u.cachedTokens.toDouble() / (u.promptTokens + u.cachedTokens)) * 100).toInt().coerceIn(0, 100)
                        } else 0
                        val turnCostStr = formatUsdEstimate(u?.costUsd)
                        val turnSpeedStr = if (u != null && u.outputTokens > 0 && u.durationMs >= 500L) {
                            val tps = u.outputTokens.toDouble() / (u.durationMs.toDouble() / 1000.0)
                            String.format(Locale.US, "%.1f tok/s", tps)
                        } else null
                        val payloadToShow = focusedMessage.rawContent ?: focusedMessage.rawPayload
                        val responseIdx = remember(messages, focusedMessage.id) {
                            val idx = messages.filter { it.role == MessageRole.ASSISTANT }.indexOfFirst { it.id == focusedMessage.id }
                            if (idx >= 0) idx + 1 else 0
                        }
                        val copyCtx = LocalContext.current

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val heading = if (responseIdx > 0 && assistantMessagesCount > 0) {
                                "THIS RESPONSE · #$responseIdx of $assistantMessagesCount"
                            } else {
                                "THIS RESPONSE"
                            }
                            SectionHeadingLabel(text = heading, color = ClaudeTerracotta)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        val report = buildString {
                                            appendLine("Chat: ${conversation?.title ?: "Current Chat"} ($agentLabel)")
                                            appendLine("Response: ${formatTelemetryFullTimestamp(focusedMessage.createdAt)}")
                                            u?.let {
                                                appendLine("Turn tokens: in=${it.promptTokens}, cached=${it.cachedTokens}, out=${it.outputTokens}, total=${it.promptTokens + it.outputTokens}")
                                                turnCostStr?.let { c -> appendLine("Turn cost: $c") }
                                            }
                                            focusedTurnDiffStats?.let { d ->
                                                appendLine("Turn code delta: +${d.additions} -${d.deletions} (${d.filesChanged} files)")
                                            }
                                            appendLine("Chat totals: in=$totalPromptTokens, cached=$totalCachedTokens ($cacheEfficiencyPct%), out=$totalOutputTokens, total=$totalCombinedTokens")
                                            formatUsdEstimate(totalCostUsd)?.let { c -> appendLine("Chat est. cost: $c") }
                                        }
                                        val cm = copyCtx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        cm.setPrimaryClip(ClipData.newPlainText("Chat Telemetry", report.trim()))
                                        Toast.makeText(copyCtx, "Copied telemetry summary", Toast.LENGTH_SHORT).show()
                                    }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy stats",
                                    tint = mutedText,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "Copy",
                                    fontSize = 11.sp,
                                    color = mutedText,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = cardBg,
                            border = BorderStroke(1.dp, ClaudeTerracotta.copy(alpha = 0.28f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(13.dp),
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                // Response Timestamp + Duration + Speed
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Schedule,
                                            contentDescription = null,
                                            tint = mutedText,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = formatTelemetryFullTimestamp(focusedMessage.createdAt),
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (u != null && u.durationMs > 0L) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = surfaceBg,
                                            border = BorderStroke(0.6.dp, subtleBorder)
                                        ) {
                                            Text(
                                                text = listOfNotNull(
                                                    formatTelemetryDuration(u.durationMs).takeIf { it.isNotEmpty() },
                                                    turnSpeedStr
                                                ).joinToString(" · "),
                                                fontSize = 10.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                color = mutedText,
                                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                                            )
                                        }
                                    }
                                }

                                if (u != null) {
                                    SegmentedTokenBar(
                                        promptTokens = u.promptTokens,
                                        cachedTokens = u.cachedTokens,
                                        cacheCreationTokens = u.cacheCreationTokens,
                                        outputTokens = u.outputTokens,
                                        inputColor = inputColor,
                                        cacheColor = cacheColor,
                                        cacheWriteColor = cacheWriteColor,
                                        outputColor = outputColor
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MiniTokenStatBox(
                                            label = if (u.isEstimated) "Input (~est)" else "Input",
                                            value = numberFormat.format(u.promptTokens),
                                            dotColor = inputColor,
                                            surfaceBg = surfaceBg,
                                            borderColor = subtleBorder,
                                            mutedText = mutedText,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MiniTokenStatBox(
                                            label = "Output",
                                            value = numberFormat.format(u.outputTokens),
                                            dotColor = outputColor,
                                            surfaceBg = surfaceBg,
                                            borderColor = subtleBorder,
                                            mutedText = mutedText,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        MiniTokenStatBox(
                                            label = if (u.cachedTokens > 0) "Cached ($turnCachePct%)" else "Cache Read",
                                            value = numberFormat.format(u.cachedTokens),
                                            dotColor = cacheColor,
                                            surfaceBg = surfaceBg,
                                            borderColor = subtleBorder,
                                            mutedText = mutedText,
                                            modifier = Modifier.weight(1f)
                                        )
                                        MiniTokenStatBox(
                                            label = if (turnCostStr != null) "Turn Total · Cost" else "Turn Total",
                                            value = buildString {
                                                append(numberFormat.format(u.promptTokens + u.outputTokens))
                                                if (turnCostStr != null) append(" · $turnCostStr")
                                            },
                                            dotColor = ClaudeTerracotta,
                                            surfaceBg = surfaceBg,
                                            borderColor = subtleBorder,
                                            mutedText = mutedText,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }

                                // Turn-specific metadata & code delta
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    val turnModel = focusedMessage.modelId ?: u?.model ?: conversation?.modelId
                                    if (!turnModel.isNullOrBlank()) {
                                        TelemetryRow(label = "Response Model", value = turnModel, mutedText = mutedText)
                                    }
                                    if (u != null && u.cacheCreationTokens > 0) {
                                        TelemetryRow(
                                            label = "Cache Write Tokens",
                                            value = numberFormat.format(u.cacheCreationTokens),
                                            mutedText = mutedText
                                        )
                                    }
                                    TelemetryRow(
                                        label = "Tools in Response",
                                        value = "${focusedMessage.toolCalls.size} executed",
                                        mutedText = mutedText
                                    )
                                    val thoughtMs = focusedMessage.thoughtDurationMs ?: 0L
                                    if (thoughtMs > 0L) {
                                        TelemetryRow(
                                            label = "Reasoning Time",
                                            value = formatTelemetryDuration(thoughtMs),
                                            mutedText = mutedText
                                        )
                                    }
                                }

                                if (focusedTurnDiffStats != null &&
                                    (focusedTurnDiffStats.additions > 0 || focusedTurnDiffStats.deletions > 0 || focusedTurnDiffStats.filesChanged > 0)
                                ) {
                                    DiffSummarySubCard(
                                        title = "Code Changed in Response",
                                        diffStats = focusedTurnDiffStats,
                                        addColor = outputColor,
                                        delColor = delColor,
                                        surfaceBg = surfaceBg,
                                        borderColor = subtleBorder,
                                        mutedText = mutedText
                                    )
                                }

                                if (!payloadToShow.isNullOrBlank() && onViewRawPayload != null) {
                                    OutlinedButton(
                                        onClick = {
                                            onDismiss()
                                            onViewRawPayload(payloadToShow)
                                        },
                                        shape = RoundedCornerShape(9.dp),
                                        border = BorderStroke(0.8.dp, subtleBorder),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.DataObject,
                                            contentDescription = null,
                                            tint = ClaudeTerracotta,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "View Raw Turn Payload",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Section 2: Entire Chat Totals
                    SectionHeadingLabel(
                        text = if (focusedMessage != null) "ENTIRE CHAT TOTALS" else "CHAT TOKEN USAGE & STATS",
                        color = mutedText
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryMetricCard(
                            title = "Total Input",
                            value = numberFormat.format(totalPromptTokens),
                            subtitle = if (totalCreationTokens > 0) "+${formatCompactTokenCount(totalCreationTokens)} cache write" else null,
                            icon = Icons.Outlined.ArrowDownward,
                            iconColor = inputColor,
                            cardBg = cardBg,
                            borderColor = subtleBorder,
                            mutedText = mutedText,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricCard(
                            title = "Total Output",
                            value = numberFormat.format(totalOutputTokens),
                            subtitle = if (totalDurationMs > 0L) formatTelemetryDuration(totalDurationMs) + " active" else null,
                            icon = Icons.Outlined.ArrowUpward,
                            iconColor = outputColor,
                            cardBg = cardBg,
                            borderColor = subtleBorder,
                            mutedText = mutedText,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TelemetryMetricCard(
                            title = "Cache Read Hits",
                            value = if (totalCachedTokens > 0) "${numberFormat.format(totalCachedTokens)} ($cacheEfficiencyPct%)" else "0",
                            subtitle = if (turnsWithUsage.isNotEmpty()) "${turnsWithUsage.size} metered turns" else null,
                            icon = Icons.Outlined.FlashOn,
                            iconColor = cacheColor,
                            cardBg = cardBg,
                            borderColor = subtleBorder,
                            mutedText = mutedText,
                            modifier = Modifier.weight(1f)
                        )
                        TelemetryMetricCard(
                            title = "Combined Tokens",
                            value = numberFormat.format(totalCombinedTokens),
                            subtitle = formatUsdEstimate(totalCostUsd)?.let { "Est. cost $it" },
                            icon = Icons.Outlined.Functions,
                            iconColor = ClaudeTerracotta,
                            cardBg = cardBg,
                            borderColor = subtleBorder,
                            mutedText = mutedText,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (totalCombinedTokens > 0 || totalCachedTokens > 0) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardBg,
                            border = BorderStroke(0.8.dp, subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(11.dp),
                                verticalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Text(
                                    text = "Token Mix Across Chat",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                SegmentedTokenBar(
                                    promptTokens = totalPromptTokens,
                                    cachedTokens = totalCachedTokens,
                                    cacheCreationTokens = totalCreationTokens,
                                    outputTokens = totalOutputTokens,
                                    inputColor = inputColor,
                                    cacheColor = cacheColor,
                                    cacheWriteColor = cacheWriteColor,
                                    outputColor = outputColor
                                )
                                LegendRow(
                                    inputColor = inputColor,
                                    cacheColor = cacheColor,
                                    outputColor = outputColor,
                                    mutedText = mutedText
                                )
                            }
                        }
                    }

                    if (conversationDiffStats != null &&
                        (conversationDiffStats.additions > 0 || conversationDiffStats.deletions > 0 || conversationDiffStats.filesChanged > 0)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = cardBg,
                            border = BorderStroke(0.8.dp, subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(11.dp)) {
                                DiffSummarySubCard(
                                    title = "Cumulative Chat Code Changes",
                                    diffStats = conversationDiffStats,
                                    addColor = outputColor,
                                    delColor = delColor,
                                    surfaceBg = surfaceBg,
                                    borderColor = subtleBorder,
                                    mutedText = mutedText
                                )
                            }
                        }
                    }

                    // Conversation Metadata Card
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = cardBg,
                        border = BorderStroke(0.8.dp, subtleBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text(
                                text = "Chat Details",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            TelemetryRow(label = "Agent", value = agentLabel, mutedText = mutedText)
                            TelemetryRow(
                                label = "Active Model",
                                value = conversation?.modelId?.ifBlank { "Default" } ?: "Default",
                                mutedText = mutedText
                            )
                            if (shortWorkspace.isNotBlank()) {
                                TelemetryRow(label = "Workspace", value = shortWorkspace, mutedText = mutedText)
                            }
                            if (conversation != null && conversation.createdAt > 0L) {
                                TelemetryRow(
                                    label = "Started",
                                    value = formatTelemetryShortTime(conversation.createdAt),
                                    mutedText = mutedText
                                )
                            }
                            if (conversation != null && conversation.updatedAt > 0L) {
                                TelemetryRow(
                                    label = "Last Activity",
                                    value = formatTelemetryShortTime(conversation.updatedAt),
                                    mutedText = mutedText
                                )
                            }
                            TelemetryRow(
                                label = "Messages & Tools",
                                value = "$userMessagesCount in · $assistantMessagesCount out · $toolsExecutedCount tools",
                                mutedText = mutedText
                            )
                            TelemetryRow(
                                label = "Context Summary",
                                value = if (!conversation?.summary.isNullOrBlank()) "Active" else "None",
                                mutedText = mutedText
                            )
                            TelemetryRow(
                                label = "System Prompt",
                                value = if (!conversation?.customSystemPrompt.isNullOrBlank()) "Configured" else "Default",
                                mutedText = mutedText
                            )
                        }
                    }

                    // Custom System Prompt Action Card
                    if (onOpenSystemPrompt != null) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp)),
                            color = cardBg,
                            border = BorderStroke(0.8.dp, ClaudeTerracotta.copy(alpha = 0.28f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Psychology,
                                    contentDescription = null,
                                    tint = ClaudeTerracotta,
                                    modifier = Modifier.size(19.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Chat System Prompt",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = if (!conversation?.customSystemPrompt.isNullOrBlank()) "Custom instructions active" else "Using default instructions",
                                        fontSize = 11.sp,
                                        color = mutedText
                                    )
                                }
                                TextButton(
                                    onClick = {
                                        onDismiss()
                                        onOpenSystemPrompt()
                                    }
                                ) {
                                    Text("Edit", fontSize = 12.5.sp, color = ClaudeTerracotta, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = ClaudeTerracotta, contentColor = Color.Black),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Close", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun SectionHeadingLabel(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = color
    )
}

@Composable
private fun SegmentedTokenBar(
    promptTokens: Int,
    cachedTokens: Int,
    cacheCreationTokens: Int,
    outputTokens: Int,
    inputColor: Color,
    cacheColor: Color,
    cacheWriteColor: Color,
    outputColor: Color
) {
    val total = (promptTokens.toLong() + cachedTokens + cacheCreationTokens + outputTokens).coerceAtLeast(1L)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    ) {
        val segments = listOf(
            promptTokens to inputColor,
            cachedTokens to cacheColor,
            cacheCreationTokens to cacheWriteColor,
            outputTokens to outputColor
        )
        segments.forEach { (count, color) ->
            if (count > 0) {
                val weight = (count.toFloat() / total.toFloat()).coerceAtLeast(0.03f)
                Box(
                    modifier = Modifier
                        .weight(weight)
                        .height(6.dp)
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun LegendRow(
    inputColor: Color,
    cacheColor: Color,
    outputColor: Color,
    mutedText: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem("Input", inputColor, mutedText)
        LegendItem("Cached", cacheColor, mutedText)
        LegendItem("Output", outputColor, mutedText)
    }
}

@Composable
private fun LegendItem(label: String, color: Color, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(color, CircleShape)
        )
        Text(text = label, fontSize = 10.5.sp, color = textColor)
    }
}

@Composable
private fun MiniTokenStatBox(
    label: String,
    value: String,
    dotColor: Color,
    surfaceBg: Color,
    borderColor: Color,
    mutedText: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(9.dp),
        color = surfaceBg,
        border = BorderStroke(0.7.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(dotColor, CircleShape)
                )
                Text(
                    text = label,
                    fontSize = 10.5.sp,
                    color = mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                fontSize = 12.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiffSummarySubCard(
    title: String,
    diffStats: TurnDiffStats,
    addColor: Color,
    delColor: Color,
    surfaceBg: Color,
    borderColor: Color,
    mutedText: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Code,
                    contentDescription = null,
                    tint = mutedText,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (diffStats.filesChanged > 0) {
                    Text(
                        text = "${diffStats.filesChanged} ${if (diffStats.filesChanged == 1) "file" else "files"}",
                        fontSize = 11.sp,
                        color = mutedText
                    )
                }
                Text(
                    text = "+${diffStats.additions}",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = addColor
                )
                Text(
                    text = "-${diffStats.deletions}",
                    fontSize = 11.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = delColor
                )
            }
        }

        if (diffStats.files.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                diffStats.files.take(6).forEach { filePath ->
                    val name = filePath.substringAfterLast('/')
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = surfaceBg,
                        border = BorderStroke(0.6.dp, borderColor)
                    ) {
                        Text(
                            text = name,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (diffStats.files.size > 6) {
                    Text(
                        text = "+${diffStats.files.size - 6} more",
                        fontSize = 10.5.sp,
                        color = mutedText,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    iconColor: Color,
    cardBg: Color,
    borderColor: Color,
    mutedText: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardBg,
        border = BorderStroke(0.8.dp, borderColor),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: String, mutedText: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 11.5.sp,
            color = mutedText,
            modifier = Modifier.padding(end = 10.dp)
        )
        Text(
            text = value,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun abbreviateWorkspacePath(path: String): String {
    if (path.isBlank()) return ""
    val homePrefix = "/data/user/0/com.termux/files/home"
    val normalized = if (path.startsWith(homePrefix)) "~" + path.removePrefix(homePrefix) else path
    return if (normalized.length <= 36) normalized else "…/" + normalized.split('/').takeLast(2).joinToString("/")
}
