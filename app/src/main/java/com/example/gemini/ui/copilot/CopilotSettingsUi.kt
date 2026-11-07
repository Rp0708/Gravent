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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.filled.*
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
import com.example.gemini.data.agent.copilot.CopilotAccountManager
import com.example.gemini.data.agent.copilot.CopilotChatBackend
import com.example.gemini.data.agent.copilot.DeviceCodeResponse
import com.example.gemini.data.agent.copilot.GitHubRepo
import com.example.gemini.data.local.LocalEnvironmentManager
import com.example.gemini.theme.QuotaGreen
import com.example.gemini.theme.QuotaRed
import com.example.gemini.ui.components.GitCloneDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val CopilotPurple = Color(0xFF8957E5)

@Composable
fun CopilotSubScreen(
    cardBg: Color,
    cardBorder: BorderStroke,
    onBack: () -> Unit,
    accountManager: CopilotAccountManager,
    backend: CopilotChatBackend
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val authState by accountManager.authState.collectAsState()
    val deviceCode by accountManager.deviceCodeFlow.collectAsState()
    val selectedModel by backend.selectedModelId.collectAsState()
    val userRepos by accountManager.userRepos.collectAsState()
    val isLoadingRepos by accountManager.isLoadingRepos.collectAsState()

    var showTokenDialog by remember { mutableStateOf(false) }
    var isStartingFlow by remember { mutableStateOf(false) }
    var cloneRepoUrl by remember { mutableStateOf<String?>(null) }
    var repoSearchQuery by remember { mutableStateOf("") }

    // Tool detection state
    var gitVersion by remember { mutableStateOf<String?>(null) }
    var ghVersion by remember { mutableStateOf<String?>(null) }
    var copilotExtVersion by remember { mutableStateOf<String?>(null) }
    var isCheckingTools by remember { mutableStateOf(false) }

    // Tool install & terminal logging
    var isRunningToolOp by remember { mutableStateOf(false) }
    var toolOpName by remember { mutableStateOf("") }
    var toolLogs by remember { mutableStateOf<List<String>>(emptyList()) }
    var toolOpSuccess by remember { mutableStateOf<Boolean?>(null) }
    var isSyncingGhAuth by remember { mutableStateOf(false) }

    fun refreshToolStatus() {
        scope.launch {
            isCheckingTools = true
            withContext(Dispatchers.IO) {
                // Check git
                val gitRes = LocalEnvironmentManager.executeCommand("git --version", context = context)
                gitVersion = if (gitRes.exitCode == 0 && gitRes.output.isNotBlank()) {
                    gitRes.output.trim().removePrefix("git version ").trim()
                } else null

                // Check gh
                val ghRes = LocalEnvironmentManager.executeCommand("gh --version", context = context)
                ghVersion = if (ghRes.exitCode == 0 && ghRes.output.isNotBlank()) {
                    ghRes.output.lines().firstOrNull { it.contains("gh version") }
                        ?.removePrefix("gh version ")?.split(" ")?.firstOrNull()?.trim()
                        ?: "Installed"
                } else null

                // Check gh copilot extension
                if (ghVersion != null) {
                    val extRes = LocalEnvironmentManager.executeCommand("gh extension list", context = context)
                    copilotExtVersion = if (extRes.exitCode == 0 && extRes.output.contains("copilot")) {
                        val line = extRes.output.lines().firstOrNull { it.contains("copilot") } ?: ""
                        if (line.isNotBlank()) "Installed" else null
                    } else null
                } else {
                    copilotExtVersion = null
                }
            }
            isCheckingTools = false
        }
    }

    LaunchedEffect(Unit) {
        refreshToolStatus()
    }

    fun runToolCommand(opTitle: String, cmd: String, onDone: () -> Unit = {}) {
        scope.launch {
            isRunningToolOp = true
            toolOpName = opTitle
            toolOpSuccess = null
            toolLogs = listOf("⟳ Starting $opTitle…\n$ $cmd")
            val result = LocalEnvironmentManager.executeCommand(
                command = cmd,
                context = context,
                timeoutSeconds = 300,
                onOutputLine = { line ->
                    toolLogs = toolLogs + line
                }
            )
            isRunningToolOp = false
            if (result.exitCode == 0) {
                toolOpSuccess = true
                toolLogs = toolLogs + "✓ $opTitle completed successfully!"
                refreshToolStatus()
                onDone()
            } else {
                toolOpSuccess = false
                toolLogs = toolLogs + "✗ Failed with exit code ${result.exitCode}"
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ==========================================
        // 1. Tool Check & Installer Card
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
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
                            .background(CopilotPurple.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Build,
                            contentDescription = null,
                            tint = CopilotPurple,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Developer & CLI Tools",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Required binaries for GitHub Copilot & repositories",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                    IconButton(
                        onClick = { refreshToolStatus() },
                        enabled = !isCheckingTools && !isRunningToolOp
                    ) {
                        if (isCheckingTools) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Refresh", tint = Color(0xFFA1A1AA))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Status Chips Row
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Git Status
                    ToolStatusRow(
                        name = "Git (VCS)",
                        status = when {
                            isCheckingTools -> "Checking…"
                            gitVersion != null -> "v$gitVersion"
                            else -> "Not Installed"
                        },
                        isOk = gitVersion != null,
                        actionLabel = if (gitVersion == null) "Install" else "Update",
                        enabled = !isRunningToolOp,
                        onAction = {
                            runToolCommand("Install/Update Git", "pkg install -y git")
                        }
                    )

                    // gh CLI Status
                    ToolStatusRow(
                        name = "GitHub CLI (gh)",
                        status = when {
                            isCheckingTools -> "Checking…"
                            ghVersion != null -> "v$ghVersion"
                            else -> "Not Installed"
                        },
                        isOk = ghVersion != null,
                        actionLabel = if (ghVersion == null) "Install" else "Update",
                        enabled = !isRunningToolOp,
                        onAction = {
                            runToolCommand("Install/Update GitHub CLI", "pkg install -y gh")
                        }
                    )

                    // gh-copilot Extension Status
                    ToolStatusRow(
                        name = "Copilot Extension (gh-copilot)",
                        status = when {
                            isCheckingTools -> "Checking…"
                            copilotExtVersion != null -> "Installed"
                            ghVersion == null -> "Needs gh first"
                            else -> "Not Installed"
                        },
                        isOk = copilotExtVersion != null,
                        actionLabel = if (copilotExtVersion == null) "Install" else "Upgrade",
                        enabled = !isRunningToolOp && ghVersion != null,
                        onAction = {
                            val cmd = if (copilotExtVersion != null) {
                                "gh extension upgrade gh-copilot"
                            } else {
                                "gh extension install github/gh-copilot --force"
                            }
                            runToolCommand("Install Copilot CLI Extension", cmd)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // One-click Install All
                Button(
                    onClick = {
                        val allCmd = "pkg install -y gh git && gh extension install github/gh-copilot --force"
                        runToolCommand("Setup All Copilot Tools", allCmd)
                    },
                    enabled = !isRunningToolOp,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CopilotPurple.copy(alpha = 0.9f),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRunningToolOp) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(toolOpName.ifBlank { "Executing…" }, fontSize = 13.sp)
                    } else {
                        Icon(Icons.Outlined.Download, contentDescription = null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Download & Setup All Required Tools", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }

        // ==========================================
        // Live Tool Terminal Console
        // ==========================================
        if (toolLogs.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (toolOpSuccess == true) Icons.Outlined.CheckCircle else Icons.Outlined.Terminal,
                                contentDescription = null,
                                tint = if (toolOpSuccess == true) QuotaGreen else if (toolOpSuccess == false) QuotaRed else CopilotPurple,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Tools Execution Output",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Row {
                            TextButton(onClick = {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("Tool Logs", toolLogs.joinToString("\n")))
                                Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                            }) {
                                Text("Copy", fontSize = 11.5.sp)
                            }
                            TextButton(onClick = { toolLogs = emptyList() }) {
                                Text("Clear", fontSize = 11.5.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFF09090B),
                        border = BorderStroke(1.dp, Color(0xFF27272A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = toolLogs.takeLast(40).joinToString("\n"),
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

        // ==========================================
        // 2. GitHub Copilot Account Card
        // ==========================================
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = cardBorder
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
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
                                    color = QuotaGreen.copy(alpha = 0.18f),
                                    contentColor = QuotaGreen
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
                    // Logged In controls
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // User Profile details
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(CopilotPurple.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    (authState.user?.login ?: "U").take(2).uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = CopilotPurple
                                )
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    authState.user?.name ?: authState.user?.login ?: "GitHub User",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                                Text(
                                    "@${authState.user?.login ?: "user"}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            IconButton(onClick = { accountManager.refreshStatus() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh", Modifier.size(18.dp))
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        // Model Selector
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
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp)
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

                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                        // Actions: Sync gh CLI auth & Disconnect
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        isSyncingGhAuth = true
                                        val res = accountManager.syncTokenToGhCli()
                                        isSyncingGhAuth = false
                                        res.onSuccess {
                                            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
                                        }.onFailure {
                                            Toast.makeText(context, "Error: ${it.message}", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                },
                                enabled = !isSyncingGhAuth,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (isSyncingGhAuth) {
                                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Outlined.Sync, contentDescription = null, Modifier.size(16.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Sync to gh CLI", fontSize = 12.5.sp)
                                }
                            }

                            OutlinedButton(
                                onClick = { accountManager.logout() },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Outlined.Logout, contentDescription = null, Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Disconnect", fontSize = 12.5.sp)
                            }
                        }
                    }
                } else {
                    // Not Logged In buttons
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
                            Icon(Icons.Default.Key, contentDescription = "Enter PAT", Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // ==========================================
        // 3. GitHub Repositories Card
        // ==========================================
        if (authState.isLoggedIn) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = cardBorder
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = CopilotPurple, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Your Repositories (${userRepos.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = { accountManager.fetchUserRepos() }, enabled = !isLoadingRepos) {
                            if (isLoadingRepos) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh repos", Modifier.size(18.dp))
                            }
                        }
                    }

                    if (userRepos.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = repoSearchQuery,
                            onValueChange = { repoSearchQuery = it },
                            placeholder = { Text("Filter repositories…", fontSize = 13.sp) },
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, Modifier.size(18.dp)) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(10.dp))

                        val filteredRepos = userRepos.filter {
                            repoSearchQuery.isBlank() ||
                            it.name.contains(repoSearchQuery, ignoreCase = true) ||
                            (it.description?.contains(repoSearchQuery, ignoreCase = true) == true)
                        }.take(30)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            filteredRepos.forEach { repo ->
                                RepoItemRow(
                                    repo = repo,
                                    onClone = { cloneRepoUrl = repo.cloneUrl },
                                    onOpenWeb = {
                                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(repo.htmlUrl))
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
                                    }
                                )
                            }
                        }
                    } else if (isLoadingRepos) {
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = CopilotPurple, modifier = Modifier.size(24.dp))
                        }
                    } else {
                        Text(
                            "No repositories found for this account.",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }

    // ==========================================
    // Dialogs
    // ==========================================
    // Device Flow Verification Dialog
    deviceCode?.let { codeResp ->
        GitHubDeviceFlowDialog(
            codeResponse = codeResp,
            onDismiss = { accountManager.cancelDeviceFlow() },
            onApproved = {
                accountManager.refreshStatus()
                refreshToolStatus()
            },
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

    // Git Clone Dialog from Repo List
    cloneRepoUrl?.let { url ->
        GitCloneDialog(
            initialUrl = url,
            onCloned = { projectItem ->
                cloneRepoUrl = null
                Toast.makeText(context, "Cloned ${projectItem.name} to workspace!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { cloneRepoUrl = null }
        )
    }
}

@Composable
private fun ToolStatusRow(
    name: String,
    status: String,
    isOk: Boolean,
    actionLabel: String,
    enabled: Boolean,
    onAction: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isOk) QuotaGreen else QuotaRed)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        status,
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = if (isOk) QuotaGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            TextButton(
                onClick = onAction,
                enabled = enabled,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(actionLabel, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CopilotPurple)
            }
        }
    }
}

@Composable
private fun RepoItemRow(
    repo: GitHubRepo,
    onClone: () -> Unit,
    onOpenWeb: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (repo.isPrivate) Icons.Outlined.Lock else Icons.Outlined.Code,
                contentDescription = null,
                tint = if (repo.isPrivate) Color(0xFFF59E0B) else CopilotPurple,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    repo.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!repo.description.isNullOrBlank()) {
                    Text(
                        repo.description,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onOpenWeb, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Launch, contentDescription = "Open Web", Modifier.size(15.dp))
                }
                FilledTonalButton(
                    onClick = onClone,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null, Modifier.size(13.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Clone", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

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
                                color = QuotaGreen.copy(alpha = 0.18f),
                                contentColor = QuotaGreen
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
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

    deviceCode?.let { codeResp ->
        GitHubDeviceFlowDialog(
            codeResponse = codeResp,
            onDismiss = { accountManager.cancelDeviceFlow() },
            onApproved = { accountManager.refreshStatus() },
            accountManager = accountManager
        )
    }

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
