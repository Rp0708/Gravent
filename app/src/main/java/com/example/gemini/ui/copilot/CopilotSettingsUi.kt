package com.example.gemini.ui.copilot

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.agent.copilot.CopilotAccountManager
import com.example.gemini.data.agent.copilot.CopilotChatBackend
import com.example.gemini.data.agent.copilot.DeviceCodeResponse
import kotlinx.coroutines.launch

val CopilotPurple = Color(0xFF8957E5)

@Composable
fun CopilotSettingsSection(
    accountManager: CopilotAccountManager,
    backend: CopilotChatBackend,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by accountManager.authState.collectAsState()
    val deviceCode by accountManager.deviceCodeFlow.collectAsState()
    val selectedModel by backend.selectedModelId.collectAsState()

    var showTokenDialog by remember { mutableStateOf(false) }
    var isStartingFlow by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(CopilotPurple.copy(alpha = 0.18f))
                        .border(1.dp, CopilotPurple.copy(alpha = 0.4f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "CP",
                        color = CopilotPurple,
                        fontWeight = FontWeight.Black,
                        fontSize = 17.sp,
                        letterSpacing = (-0.5).sp
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "GitHub Copilot",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(8.dp))
                        if (authState.isLoggedIn && authState.hasCopilotAccess) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFF22C55E).copy(alpha = 0.18f),
                                contentColor = Color(0xFF22C55E)
                            ) {
                                Text(
                                    "Active",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        if (authState.isLoggedIn) {
                            "Connected as @${authState.user?.login ?: "user"}"
                        } else {
                            "Use your GitHub Copilot subscription as an agent"
                        },
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            if (authState.isLoggedIn) {
                // Logged in controls
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Model Picker for Copilot
                    Text("Copilot Model", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "claude-3.7-sonnet" to "Claude 3.7",
                            "gpt-4o" to "GPT-4o",
                            "o3-mini" to "o3-mini",
                            "gemini-2.0-flash" to "Gemini 2.0"
                        ).forEach { (id, label) ->
                            val isSel = selectedModel == id
                            Surface(
                                onClick = { backend.selectModel(id) },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) CopilotPurple.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (isSel) CopilotPurple else Color.Transparent)
                            ) {
                                Text(
                                    label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSel) CopilotPurple else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }

                    if (authState.error != null) {
                        Text(
                            authState.error!!,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { accountManager.refreshStatus() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", Modifier.size(18.dp))
                        }
                        OutlinedButton(
                            onClick = { accountManager.logout() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Disconnect", fontSize = 13.sp)
                        }
                    }
                }
            } else {
                // Not logged in buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            isStartingFlow = true
                            scope.launch {
                                val result = accountManager.startDeviceFlow()
                                isStartingFlow = false
                                result.onFailure {
                                    Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                                }
                            }
                        },
                        enabled = !isStartingFlow,
                        colors = ButtonDefaults.buttonColors(containerColor = CopilotPurple),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isStartingFlow) {
                            CircularProgressIndicator(Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.AutoMirrored.Outlined.Login, contentDescription = null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Login with GitHub", fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    OutlinedButton(
                        onClick = { showTokenDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Key, contentDescription = null, Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    // Device Flow Verification Dialog
    deviceCode?.let { codeResp ->
        GitHubDeviceFlowDialog(
            codeResponse = codeResp,
            onDismiss = { accountManager.cancelDeviceFlow() },
            onApproved = { accountManager.refreshStatus() },
            accountManager = accountManager
        )
    }

    // Direct PAT Entry Dialog
    if (showTokenDialog) {
        GitHubTokenDialog(
            onDismiss = { showTokenDialog = false },
            onLogin = { token ->
                accountManager.loginWithDirectToken(token) { success, err ->
                    if (success) {
                        showTokenDialog = false
                        Toast.makeText(context, "GitHub Copilot connected!", Toast.LENGTH_SHORT).show()
                    } else if (err != null) {
                        Toast.makeText(context, err, Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }
}

@Composable
fun GitHubDeviceFlowDialog(
    codeResponse: DeviceCodeResponse,
    onDismiss: () -> Unit,
    onApproved: () -> Unit,
    accountManager: CopilotAccountManager
) {
    val context = LocalContext.current
    var hasCopied by remember { mutableStateOf(false) }

    // Start auto polling
    remember(codeResponse.deviceCode) {
        accountManager.startPollingForToken(codeResponse.deviceCode, codeResponse.interval) { success ->
            if (success) {
                Toast.makeText(context, "GitHub account connected successfully!", Toast.LENGTH_SHORT).show()
                onApproved()
            }
        }
    }

    fun copyAndOpen() {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("GitHub Code", codeResponse.userCode))
        hasCopied = true
        Toast.makeText(context, "Code copied to clipboard!", Toast.LENGTH_SHORT).show()

        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(codeResponse.verificationUri))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(22.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(CopilotPurple.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CP", color = CopilotPurple, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Text("Authorize GitHub", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Enter this one-time code on GitHub to authorize Gravent with your Copilot subscription:",
                    fontSize = 13.5.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )

                Spacer(Modifier.height(16.dp))

                // One-time code card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    border = BorderStroke(1.5.dp, CopilotPurple.copy(alpha = 0.6f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = codeResponse.userCode,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 4.sp,
                            fontFamily = FontFamily.Monospace,
                            color = CopilotPurple
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = CopilotPurple
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Waiting for approval on GitHub…",
                        fontSize = 12.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { copyAndOpen() },
                colors = ButtonDefaults.buttonColors(containerColor = CopilotPurple),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(if (hasCopied) Icons.Default.Check else Icons.Default.ContentCopy, contentDescription = null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Copy & Open GitHub", fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun GitHubTokenDialog(
    onDismiss: () -> Unit,
    onLogin: (String) -> Unit
) {
    var token by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = { Text("Enter GitHub Token", fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = {
            Column {
                Text(
                    "Paste a GitHub Personal Access Token (PAT) with 'repo' and 'copilot' scopes:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    placeholder = { Text("ghp_... or gho_...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onLogin(token) },
                enabled = token.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = CopilotPurple),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Connect")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
