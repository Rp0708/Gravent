package com.example.gemini.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.R
import com.example.gemini.domain.model.AgentKind
import com.example.gemini.theme.graventTokens

val ClaudeAccent: Color
    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = graventTokens().accent

val CopilotAccent: Color
    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = graventTokens().accent

/** Antigravity's color across the app (minimalist monochrome). */
val AgyAccent: Color
    @Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = graventTokens().accent

fun AgentKind.label(): String = when (this) {
    AgentKind.AGY -> "Antigravity"
    AgentKind.CLAUDE -> "Claude Code"
    AgentKind.COPILOT -> "Copilot"
}

@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun AgentKind.accent(): Color = graventTokens().accent

/**
 * Clean monochrome segmented picker for the agent of a new chat.
 * Uses official vector brand logos with strict developer aesthetic.
 */
@Composable
fun AgentPicker(
    selected: AgentKind,
    onSelect: (AgentKind) -> Unit,
    modifier: Modifier = Modifier,
    agents: Collection<AgentKind> = AgentKind.entries,
    statusColors: Map<AgentKind, Color> = emptyMap()
) {
    if (agents.size < 2) return
    val t = graventTokens()
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = modifier,
        shape = shape,
        color = t.cardBg,
        border = BorderStroke(1.dp, t.border)
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AgentKind.entries.filter { it in agents }.forEach { agent ->
                val isSelected = agent == selected
                val itemShape = RoundedCornerShape(11.dp)
                Surface(
                    onClick = { onSelect(agent) },
                    shape = itemShape,
                    color = if (isSelected) t.chipBg else Color.Transparent,
                    border = if (isSelected) BorderStroke(1.dp, t.border) else null
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val logoRes = when (agent) {
                            AgentKind.AGY -> R.drawable.ic_antigravity_logo
                            AgentKind.CLAUDE -> R.drawable.ic_claude_logo
                            AgentKind.COPILOT -> R.drawable.ic_copilot_logo
                        }
                        Icon(
                            painter = painterResource(id = logoRes),
                            contentDescription = agent.label(),
                            tint = if (isSelected) t.textPrimary else t.textMuted,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = agent.label(),
                            fontSize = 12.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) t.textPrimary else t.textMuted
                        )
                    }
                }
            }
        }
    }
}

/** Small badge marking a Claude Code conversation in lists. */
@Composable
fun ClaudeBadge(modifier: Modifier = Modifier) {
    val accent = graventTokens().accent
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(accent.copy(alpha = 0.16f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text = "Claude", fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, color = accent)
    }
}
