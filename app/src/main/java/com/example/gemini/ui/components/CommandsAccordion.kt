package com.example.gemini.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.remote.dto.AskQuestionResponseItemDto
import com.example.gemini.domain.model.ToolCall
import com.example.gemini.domain.model.ToolType
import com.example.gemini.theme.graventTokens

/**
 * Collapsible accordion for terminal, bash, and agent tool executions.
 *
 * Automatically keeps commands neatly collapsed into a single compact dropdown
 * once the turn finishes, allowing users to focus on reading the conversation text.
 * When commands are actively running in the background, they are segregated into their
 * own dedicated "Running in background" section.
 */
@Composable
fun CommandsAccordion(
    toolCalls: List<ToolCall>,
    isStreaming: Boolean,
    modifier: Modifier = Modifier,
    isBackgroundSection: Boolean = false,
    onApproveTool: ((ToolCall) -> Unit)? = null,
    onRejectTool: ((ToolCall) -> Unit)? = null,
    onTerminateTool: ((ToolCall) -> Unit)? = null,
    onSubmitChoices: ((ToolCall, List<AskQuestionResponseItemDto>, String) -> Unit)? = null,
    onSkipChoices: ((ToolCall, List<AskQuestionResponseItemDto>) -> Unit)? = null
) {
    if (toolCalls.isEmpty()) return

    val hasPendingAction = toolCalls.any {
        it.status == "PENDING_APPROVAL" || it.status == "AWAITING_CHOICE"
    }
    val hasActiveRunning = toolCalls.any { it.status == "RUNNING" }

    var isExpanded by remember { mutableStateOf(hasPendingAction || (isStreaming && hasActiveRunning)) }
    var hasUserManuallyToggled by remember { mutableStateOf(false) }

    LaunchedEffect(hasPendingAction, isStreaming, hasActiveRunning) {
        if (hasPendingAction && !isExpanded) {
            isExpanded = true
        } else if (isStreaming && hasActiveRunning && !hasUserManuallyToggled && !isExpanded) {
            isExpanded = true
        }
    }

    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 90f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "commands_chevron_rotation"
    )

    val anyError = toolCalls.any { it.status == "FAILED" || (it.exitCode != null && it.exitCode != 0) }
    val allSuccess = toolCalls.all { it.status == "SUCCESS" || it.exitCode == 0 }

    val summaryText = remember(toolCalls, isBackgroundSection) {
        val count = toolCalls.size
        val firstCmd = toolCalls.firstOrNull()?.command?.lines()?.firstOrNull()?.trim() ?: ""
        val truncatedFirst = if (firstCmd.length > 34) firstCmd.take(34) + "…" else firstCmd

        if (isBackgroundSection) {
            when {
                count == 1 && truncatedFirst.isNotBlank() -> "Running in background: $truncatedFirst"
                count == 1 -> "1 command running in background"
                else -> "$count commands running in background"
            }
        } else {
            when {
                count == 1 && truncatedFirst.isNotBlank() -> "Ran bash: $truncatedFirst"
                count == 1 -> "Ran 1 command"
                else -> "Ran $count commands"
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val t = graventTokens()
    val headerIconTint = if (isBackgroundSection) t.infoAccent else t.accent.copy(alpha = 0.85f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // Clean borderless inline header row matching ThinkingAccordion style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .clickable {
                    hasUserManuallyToggled = true
                    isExpanded = !isExpanded
                }
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = t.textMuted,
                modifier = Modifier
                    .size(15.dp)
                    .rotate(rotation)
            )

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                imageVector = Icons.Outlined.Terminal,
                contentDescription = if (isBackgroundSection) "Background commands" else "Terminal commands",
                tint = headerIconTint,
                modifier = Modifier.size(15.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = summaryText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                color = if (isBackgroundSection) t.infoAccent else t.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Status Badge
            if (isBackgroundSection) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(t.infoSoftBg)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(t.infoAccent.copy(alpha = pulseAlpha))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "background",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = t.infoAccent
                    )
                }
            } else if (hasActiveRunning) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(t.accent)
                )
            } else if (anyError) {
                GraventBadge(text = "failed", tone = GraventTone.ERROR)
            } else if (allSuccess) {
                GraventBadge(text = "exit 0", tone = GraventTone.SUCCESS)
            }
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(animationSpec = tween(durationMillis = 200)),
            exit = shrinkVertically(animationSpec = tween(durationMillis = 180))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (tc in toolCalls) {
                    AgentToolCallCard(
                        toolCall = tc,
                        onApprove = onApproveTool,
                        onReject = onRejectTool,
                        onTerminate = onTerminateTool,
                        onSubmitChoices = onSubmitChoices,
                        onSkipChoices = onSkipChoices
                    )
                }
            }
        }
    }
}
