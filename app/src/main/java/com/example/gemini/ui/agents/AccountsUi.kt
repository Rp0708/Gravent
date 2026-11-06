package com.example.gemini.ui.agents

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.example.gemini.data.agent.claude.ClaudeCliStatus
import com.example.gemini.data.remote.AgyHubClient
import com.example.gemini.domain.model.AgentKind
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.ui.components.accent
import com.example.gemini.ui.components.label
import com.example.gemini.ui.drawer.parseProfileAvatarModel

/** Account avatar: the profile picture or initial when signed in, a sign-in glyph otherwise; status badge corner. */
@Composable
private fun AccountAvatar(
    agent: AgentKind,
    entry: AgentStatusEntry,
    initial: Char?,
    pictureUrl: String? = null,
    size: Dp = 26.dp
) {
    val accent = agent.accent()
    Box(Modifier.size(size)) {
        val ring = Modifier.size(size).clip(CircleShape).border(1.5.dp, accent.copy(alpha = 0.7f), CircleShape)
        when {
            !entry.isReady -> Box(ring.background(accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                if (entry.isSignedOut) {
                    Icon(Icons.AutoMirrored.Outlined.Login, null, tint = accent, modifier = Modifier.size(size * 0.5f))
                } else {
                    AgentMark(agent, size = size)
                }
            }
            pictureUrl != null -> SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(parseProfileAvatarModel(pictureUrl)).crossfade(true).build(),
                contentDescription = null,
                modifier = ring,
                contentScale = ContentScale.Crop,
                loading = { InitialCircle(initial, accent, size) },
                error = { InitialCircle(initial, accent, size) }
            )
            else -> Box(ring) { InitialCircle(initial, accent, size) }
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 2.dp, y = 2.dp)
                .size(size * 0.38f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface)
                .padding(1.5.dp)
                .clip(CircleShape)
                .background(entry.color)
        )
    }
}

@Composable
private fun InitialCircle(initial: Char?, accent: Color, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(accent),
        contentAlignment = Alignment.Center
    ) {
        Text((initial?.uppercaseChar() ?: '•').toString(), fontSize = (size.value * 0.44f).sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

/**
 * Drawer footer button for the agent accounts: one avatar per enabled agent (sign-in glyph when signed out) with
 * its status badge. Long-press opens quick switcher dropdown menu. Opens [AccountsDialog] on click.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountsButton(
    agy: AgentStatusEntry?,
    claude: AgentStatusEntry?,
    agyAuth: AgyHubClient.AgyAuthInfo,
    claudeStatus: ClaudeCliStatus?,
    savedAgyAccounts: List<com.example.gemini.data.agent.agy.SavedAgyAccount> = emptyList(),
    activeAgyAccountId: String? = null,
    onSwitchAgyAccount: (com.example.gemini.data.agent.agy.SavedAgyAccount) -> Unit = {},
    onAddAgyAccount: () -> Unit = {},
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showQuickSwitchMenu by remember { mutableStateOf(false) }
    val entries = listOfNotNull(agy, claude)
    val needsSignIn = entries.any { it.isSignedOut }
    val single = entries.singleOrNull()

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.18f)),
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (onLongClick != null) {
                            onLongClick()
                        } else if (savedAgyAccounts.isNotEmpty()) {
                            showQuickSwitchMenu = true
                        } else {
                            onClick()
                        }
                    }
                )
        ) {
            Row(Modifier.padding(start = 5.dp, end = 10.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy((-7).dp)) {
                    agy?.let { AccountAvatar(AgentKind.AGY, it, agyAuth.displayName.firstOrNull(), agyAuth.profilePictureUrl?.takeIf { u -> u.isNotBlank() }) }
                    claude?.let { AccountAvatar(AgentKind.CLAUDE, it, claudeStatus?.auth?.email?.firstOrNull()) }
                }
                Spacer(Modifier.width(7.dp))
                val text = when {
                    needsSignIn -> "Sign in"
                    single?.agent == AgentKind.AGY && single.isReady -> agyAuth.displayName.ifBlank { "Account" }
                    single?.agent == AgentKind.CLAUDE && single.isReady -> claudeStatus?.auth?.email?.substringBefore('@') ?: "Account"
                    single != null -> single.label
                    else -> "Accounts"
                }
                Text(
                    text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (needsSignIn) (entries.first { it.isSignedOut }.agent.accent()) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 104.dp)
                )
            }
        }

        // Quick Switcher Dropdown Menu anchored to AccountsButton
        DropdownMenu(
            expanded = showQuickSwitchMenu,
            onDismissRequest = { showQuickSwitchMenu = false },
            modifier = Modifier
                .background(Color(0xFF141418))
                .border(1.dp, Color(0xFF282832), RoundedCornerShape(12.dp))
                .widthIn(min = 220.dp, max = 280.dp)
        ) {
            Text(
                "SWITCH ACCOUNT",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )

            savedAgyAccounts.forEach { acc ->
                val currentActiveId = activeAgyAccountId?.takeIf { it.isNotBlank() }
                    ?: agyAuth.email.takeIf { it.isNotBlank() }
                val isActive = currentActiveId != null && (
                    acc.id.equals(currentActiveId, ignoreCase = true) ||
                    acc.email.equals(currentActiveId, ignoreCase = true)
                )
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            InitialCircle(acc.displayName.firstOrNull() ?: acc.email.firstOrNull(), AgentKind.AGY.accent(), 24.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    acc.displayName.ifBlank { acc.email },
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    acc.email,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (isActive) {
                                Spacer(Modifier.width(6.dp))
                                Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFF22C55E).copy(alpha = 0.2f)) {
                                    Text("Active", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                }
                            }
                        }
                    },
                    onClick = {
                        showQuickSwitchMenu = false
                        if (!isActive) {
                            onSwitchAgyAccount(acc)
                        }
                    }
                )
            }

            HorizontalDivider(color = Color(0xFF282832))

            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null, tint = AgentKind.AGY.accent(), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("+ Add Account", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = AgentKind.AGY.accent())
                    }
                },
                onClick = {
                    showQuickSwitchMenu = false
                    onAddAgyAccount()
                }
            )
        }
    }
}

/** Both agents' accounts: who is signed in, plan, and sign in / out (usage lives in Settings and the model picker). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsDialog(
    agy: AgentStatusEntry?,
    claude: AgentStatusEntry?,
    agyAuth: AgyHubClient.AgyAuthInfo,
    claudeStatus: ClaudeCliStatus?,
    savedAgyAccounts: List<com.example.gemini.data.agent.agy.SavedAgyAccount> = emptyList(),
    activeAgyAccountId: String? = null,
    onSwitchAgyAccount: (com.example.gemini.data.agent.agy.SavedAgyAccount) -> Unit = {},
    onAddAgyAccount: () -> Unit = {},
    onRemoveAgyAccount: (String) -> Unit = {},
    onAgySignOut: () -> Unit,
    onAgyCancelSignIn: () -> Unit,
    onClaudeSignOut: () -> Unit,
    onClaudeSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF222226)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Accounts", fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Outlined.Close, "Close", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                }
                Text(
                    "Switch or configure multiple Google & Claude accounts.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                )
                Spacer(Modifier.height(14.dp))

                agy?.let { entry ->
                    AccountSection(
                        entry = entry,
                        avatar = { AccountAvatar(AgentKind.AGY, entry, agyAuth.displayName.firstOrNull(), agyAuth.profilePictureUrl?.takeIf { it.isNotBlank() }, size = 44.dp) },
                        title = if (entry.isReady) agyAuth.displayName.ifBlank { AgentKind.AGY.label() } else AgentKind.AGY.label(),
                        subtitle = if (entry.isReady) agyAuth.email.ifBlank { null } else entry.detail,
                        facts = if (entry.isReady) listOfNotNull(
                            agyAuth.userTier.ifBlank { agyAuth.planName }.ifBlank { null }
                        ) else emptyList()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Multi-account switcher list
                            if (savedAgyAccounts.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "CONFIGURED ACCOUNTS",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                                Spacer(Modifier.height(6.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    savedAgyAccounts.forEach { acc ->
                                        val currentActiveId = activeAgyAccountId?.takeIf { it.isNotBlank() }
                                            ?: agyAuth.email.takeIf { it.isNotBlank() }
                                        val isActive = currentActiveId != null && (
                                            acc.id.equals(currentActiveId, ignoreCase = true) ||
                                            acc.email.equals(currentActiveId, ignoreCase = true)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isActive) Color(0xFF16161C) else Color(0xFF0F0F12),
                                            border = BorderStroke(1.dp, if (isActive) Color(0xFF22C55E).copy(alpha = 0.5f) else Color(0xFF24242A)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                InitialCircle(acc.displayName.firstOrNull() ?: acc.email.firstOrNull(), AgentKind.AGY.accent(), 30.dp)
                                                Spacer(Modifier.width(10.dp))
                                                Column(Modifier.weight(1f)) {
                                                    Text(acc.displayName.ifBlank { acc.email }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    Text(acc.email, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                if (isActive) {
                                                    Surface(shape = RoundedCornerShape(6.dp), color = Color(0xFF22C55E).copy(alpha = 0.18f)) {
                                                        Text("Active", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF22C55E), modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                                    }
                                                } else {
                                                    Surface(
                                                        onClick = {
                                                            onSwitchAgyAccount(acc)
                                                            onDismiss()
                                                        },
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
                                                    IconButton(onClick = { onRemoveAgyAccount(acc.id) }, modifier = Modifier.size(28.dp)) {
                                                        Icon(Icons.Outlined.Close, "Remove account", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f), modifier = Modifier.size(14.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                Spacer(Modifier.height(8.dp))
                            }

                            // Action buttons row
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                when {
                                    entry.isReady -> {
                                        SectionButton("+ Add Account", Icons.AutoMirrored.Outlined.Login) {
                                            onAddAgyAccount()
                                            onDismiss()
                                        }
                                        if (agyAuth.upgradeSubscriptionUri.isNotBlank()) {
                                            SectionButton(agyAuth.upgradeSubscriptionText.ifBlank { "Manage plan" }, Icons.AutoMirrored.Outlined.OpenInNew) {
                                                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(agyAuth.upgradeSubscriptionUri))) }
                                            }
                                        }
                                        SectionButton("Sign out", Icons.AutoMirrored.Outlined.Logout, danger = true) {
                                            onAgySignOut()
                                            onDismiss()
                                        }
                                    }
                                    entry.pending && entry.action != null -> {
                                        entry.action.let { SectionButton(it.label, primary = AgentKind.AGY.accent()) { it.onClick() } }
                                        SectionButton("Cancel") { onAgyCancelSignIn() }
                                    }
                                    else -> entry.action?.let {
                                        SectionButton(it.label, primary = AgentKind.AGY.accent()) {
                                            it.onClick()
                                            onDismiss()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (agy != null && claude != null) Spacer(Modifier.height(12.dp))

                claude?.let { entry ->
                    val auth = claudeStatus?.auth
                    AccountSection(
                        entry = entry,
                        avatar = { AccountAvatar(AgentKind.CLAUDE, entry, auth?.email?.firstOrNull(), size = 44.dp) },
                        title = AgentKind.CLAUDE.label(),
                        subtitle = if (entry.isReady) auth?.email else entry.detail,
                        facts = if (entry.isReady) listOfNotNull(
                            auth?.subscriptionType?.let { claudePlanLabel(it) },
                            auth?.authMethod?.let { claudeAuthMethodLabel(it) },
                            claudeStatus?.version?.substringBefore(" ")?.let { "CLI $it" }
                        ) else listOfNotNull(claudeStatus?.version?.substringBefore(" ")?.let { "CLI $it" })
                    ) {
                        if (entry.isReady) {
                            SectionButton("Claude settings", Icons.Outlined.Settings) {
                                onClaudeSettings()
                                onDismiss()
                            }
                            SectionButton("Sign out", Icons.AutoMirrored.Outlined.Logout, danger = true) { onClaudeSignOut() }
                        } else {
                            entry.action?.let {
                                SectionButton(it.label, primary = AgentKind.CLAUDE.accent()) {
                                    it.onClick()
                                    onDismiss()
                                }
                            }
                            if (!entry.pending) SectionButton("Claude settings", Icons.Outlined.Settings) {
                                onClaudeSettings()
                                onDismiss()
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun claudeAuthMethodLabel(raw: String): String = when (raw.lowercase()) {
    "claude.ai", "claudeai", "oauth" -> "Claude account"
    "console", "api_key", "apikey" -> "Anthropic Console"
    else -> raw
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountSection(
    entry: AgentStatusEntry,
    avatar: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    facts: List<String>,
    actions: @Composable () -> Unit
) {
    val accent = entry.agent.accent()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.05f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                avatar()
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    subtitle?.let {
                        Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.width(8.dp))
                StatusChip(entry)
            }
            if (facts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    facts.forEach { fact ->
                        Text(
                            fact,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
        }
    }
}

@Composable
private fun StatusChip(entry: AgentStatusEntry) {
    Row(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(entry.color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(entry.color))
        Spacer(Modifier.width(5.dp))
        Text(entry.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = entry.color, maxLines = 1)
    }
}

@Composable
private fun SectionButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    danger: Boolean = false,
    primary: Color? = null,
    onClick: () -> Unit
) {
    val color = when {
        danger -> Color(0xFFEF4444)
        primary != null -> primary
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
    }
    if (primary != null) {
        Surface(onClick = onClick, shape = RoundedCornerShape(12.dp), color = primary) {
            Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp))
        }
        return
    }
    TextButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
        icon?.let {
            Icon(it, null, tint = color, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, color = color)
    }
}
