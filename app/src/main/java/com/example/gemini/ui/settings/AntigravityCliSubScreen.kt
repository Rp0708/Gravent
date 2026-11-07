package com.example.gemini.ui.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.local.LocalEnvironmentManager
import com.example.gemini.data.local.LocalServerManager
import com.example.gemini.data.preferences.AuthPreferences
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.QuotaGreen
import com.example.gemini.theme.QuotaRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun AntigravityCliSubScreen(
    cardBg: Color,
    cardBorder: BorderStroke,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authPrefs = remember { AuthPreferences(context) }

    var detectedPath by remember { mutableStateOf<String?>(null) }
    var cliVersion by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(false) }

    var isUpdating by remember { mutableStateOf(false) }
    var updateLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var updateSuccess by remember { mutableStateOf<Boolean?>(null) }

    var isRestartingServer by remember { mutableStateOf(false) }
    var restartFeedback by remember { mutableStateOf<String?>(null) }

    var quickOutputTitle by remember { mutableStateOf<String?>(null) }
    var quickOutputText by remember { mutableStateOf<String?>(null) }
    var isRunningQuickCmd by remember { mutableStateOf(false) }

    // Helper to detect agy binary path
    fun resolveAgyPath(): String {
        val configured = authPrefs.getAgyBinaryPathSync().trim()
        if (configured.isNotBlank()) return configured

        val homeAgy = File(LocalEnvironmentManager.getHomeDir(context), ".local/bin/agy")
        if (homeAgy.exists()) return homeAgy.absolutePath

        val binAgy = File(LocalEnvironmentManager.getBinDir(context), "agy")
        if (binAgy.exists()) return binAgy.absolutePath

        val ubuntuAgy = File(LocalEnvironmentManager.getUbuntuRootDir(context), "root/.local/bin/agy")
        if (ubuntuAgy.exists()) return ubuntuAgy.absolutePath

        return "agy"
    }

    // Refresh version and status
    fun refreshStatus() {
        scope.launch {
            isChecking = true
            val path = withContext(Dispatchers.IO) { resolveAgyPath() }
            detectedPath = path

            val result = LocalEnvironmentManager.executeCommand("agy --version", context = context)
            if (result.exitCode == 0 && result.output.isNotBlank()) {
                // Parse version number from output (e.g., 1.5.2)
                val lines = result.output.lines()
                val verLine = lines.lastOrNull { line ->
                    line.trim().matches(Regex(".*\\d+\\.\\d+\\.\\d+.*"))
                }?.trim()
                cliVersion = verLine ?: result.output.trim().lines().last()
            } else {
                cliVersion = "Not found / Offline"
            }
            isChecking = false
        }
    }

    LaunchedEffect(Unit) {
        refreshStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Status & Overview Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = cardBorder
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.SmartToy,
                            contentDescription = null,
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Google Antigravity CLI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        val versionText = when {
                            isChecking -> "Checking version…"
                            cliVersion != null -> "Installed: $cliVersion"
                            else -> "Detecting…"
                        }
                        Text(
                            text = versionText,
                            fontSize = 12.5.sp,
                            color = if (cliVersion?.contains("Not found") == true) QuotaRed else Color(0xFFA1A1AA)
                        )
                    }
                    IconButton(
                        onClick = { refreshStatus() },
                        enabled = !isChecking && !isUpdating
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Color(0xFFA1A1AA))
                        }
                    }
                }

                if (detectedPath != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Binary Path:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFFA1A1AA)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = detectedPath ?: "agy",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action buttons: Update to latest
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                isUpdating = true
                                updateSuccess = null
                                updateLogs = listOf("⟳ Starting agy update…")
                                restartFeedback = null

                                val result = LocalEnvironmentManager.executeCommand(
                                    command = "agy update",
                                    context = context,
                                    timeoutSeconds = 180,
                                    onOutputLine = { line ->
                                        updateLogs = updateLogs + line
                                    }
                                )

                                isUpdating = false
                                if (result.exitCode == 0) {
                                    updateSuccess = true
                                    updateLogs = updateLogs + "✓ Update completed successfully!"
                                    refreshStatus()
                                } else {
                                    updateSuccess = false
                                    updateLogs = updateLogs + "✗ Update exited with code ${result.exitCode}"
                                }
                            }
                        },
                        enabled = !isUpdating,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF38BDF8),
                            contentColor = Color(0xFF09090B)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF09090B)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Updating…", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        } else {
                            Icon(Icons.Outlined.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Update CLI to Latest", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isRestartingServer = true
                                restartFeedback = null
                                withContext(Dispatchers.IO) {
                                    LocalServerManager.restartServer(context)
                                }
                                isRestartingServer = false
                                restartFeedback = "Local engine restarted with current CLI."
                            }
                        },
                        enabled = !isRestartingServer && !isUpdating,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isRestartingServer) {
                            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restart Engine", fontSize = 12.sp)
                        }
                    }
                }

                if (restartFeedback != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = restartFeedback ?: "",
                        fontSize = 12.sp,
                        color = QuotaGreen
                    )
                }
            }
        }

        // 2. Live Update Console & Output
        if (updateLogs.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (updateSuccess == true) Icons.Outlined.CheckCircle else Icons.Outlined.Terminal,
                            contentDescription = null,
                            tint = if (updateSuccess == true) QuotaGreen else if (updateSuccess == false) QuotaRed else Color(0xFF38BDF8),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Update Console Log",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF09090B),
                        border = BorderStroke(1.dp, Color(0xFF27272A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = updateLogs.takeLast(40).joinToString("\n"),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = Color(0xFFD4D4D8),
                            modifier = Modifier
                                .heightIn(max = 200.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp)
                        )
                    }
                }
            }
        }

        // 3. Quick CLI Actions & Diagnostics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = cardBorder
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Diagnostics & Commands",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Inspect the installed CLI binary capabilities and release notes",
                    fontSize = 11.5.sp,
                    color = Color(0xFFA1A1AA)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isRunningQuickCmd = true
                                quickOutputTitle = "agy models"
                                val res = LocalEnvironmentManager.executeCommand("agy models", context = context)
                                quickOutputText = res.output
                                isRunningQuickCmd = false
                            }
                        },
                        enabled = !isRunningQuickCmd,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Models", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isRunningQuickCmd = true
                                quickOutputTitle = "agy changelog"
                                val res = LocalEnvironmentManager.executeCommand("agy changelog", context = context)
                                quickOutputText = res.output
                                isRunningQuickCmd = false
                            }
                        },
                        enabled = !isRunningQuickCmd,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Changelog", fontSize = 11.5.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                isRunningQuickCmd = true
                                quickOutputTitle = "agy --help"
                                val res = LocalEnvironmentManager.executeCommand("agy --help", context = context)
                                quickOutputText = res.output
                                isRunningQuickCmd = false
                            }
                        },
                        enabled = !isRunningQuickCmd,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Flags Help", fontSize = 11.5.sp)
                    }
                }

                if (quickOutputText != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Output of ${quickOutputTitle ?: "Command"}:",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFF38BDF8)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF09090B),
                        border = BorderStroke(1.dp, Color(0xFF27272A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = quickOutputText ?: "",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 15.sp,
                            color = Color(0xFFD4D4D8),
                            modifier = Modifier
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}
