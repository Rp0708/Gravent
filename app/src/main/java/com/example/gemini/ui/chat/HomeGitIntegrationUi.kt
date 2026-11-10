package com.example.gemini.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemini.data.daemon.GitApiClient
import com.example.gemini.data.daemon.GitBranchInfo
import com.example.gemini.data.daemon.GitDiffResponse
import com.example.gemini.data.daemon.GitFileStatus
import com.example.gemini.data.daemon.GitStatusResponse
import com.example.gemini.domain.model.TurnDiffStats
import com.example.gemini.domain.model.TurnFileDelta
import com.example.gemini.theme.graventCheckboxColors
import com.example.gemini.theme.graventNeutralButtonColors
import com.example.gemini.theme.graventPrimaryButtonColors
import com.example.gemini.theme.graventTokens
import com.example.gemini.ui.components.formatTelemetryShortTime
import com.example.gemini.ui.ide.UnifiedDiffViewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private val HomeCardBg: Color
    @Composable @ReadOnlyComposable get() = graventTokens().cardBg
private val HomeCardElevated: Color
    @Composable @ReadOnlyComposable get() = graventTokens().elevatedBg
private val HomeCardBorder: Color
    @Composable @ReadOnlyComposable get() = graventTokens().border
private val HomeCardDivider: Color
    @Composable @ReadOnlyComposable get() = graventTokens().subtleBorder
private val HomeWarmAccent: Color
    @Composable @ReadOnlyComposable get() = graventTokens().accent
private val HomeTextPrimary: Color
    @Composable @ReadOnlyComposable get() = graventTokens().textPrimary
private val HomeTextSecondary: Color
    @Composable @ReadOnlyComposable get() = graventTokens().textSecondary
private val HomeAddGreen: Color
    @Composable @ReadOnlyComposable get() = graventTokens().gitAdd
private val HomeDelRed: Color
    @Composable @ReadOnlyComposable get() = graventTokens().gitDel
private val HomeWarnAmber: Color
    @Composable @ReadOnlyComposable get() = graventTokens().warnAmber
private val HomeSkyBlue: Color
    @Composable @ReadOnlyComposable get() = if (graventTokens().isDark) Color(0xFF38BDF8) else Color(0xFF0284C7)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeCodexWorkspaceCard(
    projectName: String,
    projectPath: String,
    hasWorkspace: Boolean,
    isBridgeOnline: Boolean,
    gitStatus: GitStatusResponse?,
    isGitLoading: Boolean,
    onChangeWorkspace: () -> Unit,
    onRefreshGit: () -> Unit,
    onOpenInIde: (relativePath: String?) -> Unit,
    onAskReview: (prompt: String) -> Unit,
    onCommit: (message: String, alsoPush: Boolean, onResult: (Boolean, String?) -> Unit) -> Unit,
    onPush: (onResult: (Boolean, String?) -> Unit) -> Unit,
    onPull: (onResult: (Boolean, String?) -> Unit) -> Unit,
    onCheckoutBranch: (branch: String, create: Boolean, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDiscardAll: (paths: List<String>, onResult: (Boolean, String?) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    val shortPath = remember(projectPath) { abbreviateHomeDirectoryPath(projectPath) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = HomeCardBg,
        border = BorderStroke(1.dp, HomeCardBorder)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 1. Primary Workspace Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onChangeWorkspace)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(HomeCardElevated)
                        .padding(1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FolderOpen,
                        contentDescription = null,
                        tint = if (hasWorkspace) HomeWarmAccent else HomeTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(11.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = projectName,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HomeTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = if (hasWorkspace && shortPath.isNotBlank()) {
                            shortPath
                        } else {
                            "Choose a repository or working directory"
                        },
                        fontSize = 11.5.sp,
                        fontFamily = if (hasWorkspace && shortPath.isNotBlank()) FontFamily.Monospace else FontFamily.SansSerif,
                        color = HomeTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = HomeCardElevated,
                    border = BorderStroke(0.6.dp, HomeCardBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = if (hasWorkspace) "Switch" else "Select",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = HomeWarmAccent
                        )
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = HomeTextSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            // 2. Integrated Codex Core Git Strip (only when workspace is active)
            if (hasWorkspace) {
                HomeRepoGitSection(
                    projectPath = projectPath,
                    isBridgeOnline = isBridgeOnline,
                    gitStatus = gitStatus,
                    isGitLoading = isGitLoading,
                    alwaysExpanded = false,
                    onRefreshGit = onRefreshGit,
                    onOpenInIde = onOpenInIde,
                    onAskReview = onAskReview,
                    onCommit = onCommit,
                    onPush = onPush,
                    onPull = onPull,
                    onCheckoutBranch = onCheckoutBranch,
                    onDiscardAll = onDiscardAll
                )
            }
        }
    }
}

/**
 * Live GitHub / Git repository status modal launched from inside any active chat screen.
 * Reuses [HomeRepoGitSection] so Home and in-chat repo operations stay identical.
 */
@Composable
fun InChatRepoGitDialog(
    projectName: String,
    projectPath: String,
    hasWorkspace: Boolean,
    isBridgeOnline: Boolean,
    gitStatus: GitStatusResponse?,
    isGitLoading: Boolean,
    onRefreshGit: () -> Unit,
    onOpenInIde: (relativePath: String?) -> Unit,
    onAskReview: (prompt: String) -> Unit,
    onCommit: (message: String, alsoPush: Boolean, onResult: (Boolean, String?) -> Unit) -> Unit,
    onPush: (onResult: (Boolean, String?) -> Unit) -> Unit,
    onPull: (onResult: (Boolean, String?) -> Unit) -> Unit,
    onCheckoutBranch: (branch: String, create: Boolean, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDiscardAll: (paths: List<String>, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    val shortPath = remember(projectPath) { abbreviateHomeDirectoryPath(projectPath) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = HomeCardBg,
            border = BorderStroke(1.dp, HomeCardBorder),
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth(0.93f)
                .heightIn(max = 650.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(HomeCardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AltRoute,
                            contentDescription = null,
                            tint = HomeWarmAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = gitStatus?.repoSlug ?: projectName.ifBlank { "Repository" },
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (shortPath.isNotBlank()) {
                            Text(
                                text = shortPath,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                color = HomeTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onRefreshGit, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Git",
                            tint = HomeTextSecondary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(30.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = HomeTextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (hasWorkspace) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        HomeRepoGitSection(
                            projectPath = projectPath,
                            isBridgeOnline = isBridgeOnline,
                            gitStatus = gitStatus,
                            isGitLoading = isGitLoading,
                            alwaysExpanded = true,
                            onRefreshGit = onRefreshGit,
                            onOpenInIde = { relPath ->
                                onDismiss()
                                onOpenInIde(relPath)
                            },
                            onAskReview = { prompt ->
                                onDismiss()
                                onAskReview(prompt)
                            },
                            onCommit = onCommit,
                            onPush = onPush,
                            onPull = onPull,
                            onCheckoutBranch = onCheckoutBranch,
                            onDiscardAll = onDiscardAll
                        )
                    }
                } else {
                    Text(
                        text = "Select a project folder to inspect Git history and uncommitted changes.",
                        fontSize = 12.5.sp,
                        color = HomeTextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

/**
 * Per-prompt Git operations popup opened from the `+X -Y [i]` badge next to an assistant response.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TurnGitInfoDialog(
    diffStats: TurnDiffStats,
    fallbackProjectPath: String,
    liveGitStatus: GitStatusResponse?,
    onOpenRepoHub: (() -> Unit)? = null,
    onOpenInIde: (relativePath: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val resolvedProjectPath = diffStats.projectPath?.takeIf { it.isNotBlank() } ?: fallbackProjectPath
    var selectedDiffFile by remember { mutableStateOf<GitFileStatus?>(null) }

    val fileRows = remember(diffStats) {
        if (diffStats.fileDeltas.isNotEmpty()) {
            diffStats.fileDeltas.map { d ->
                GitFileStatus(
                    path = d.path,
                    status = d.status,
                    staged = d.staged,
                    additions = d.additions,
                    deletions = d.deletions
                )
            }
        } else {
            diffStats.files.map { p ->
                GitFileStatus(path = p, status = "M", staged = false)
            }
        }
    }

    val modifiedCount = remember(fileRows) { fileRows.count { it.status.equals("M", true) || it.status.equals("R", true) } }
    val addedCount = remember(fileRows) { fileRows.count { it.status.equals("A", true) || it.status.equals("U", true) || it.status == "?" } }
    val deletedCount = remember(fileRows) { fileRows.count { it.status.equals("D", true) } }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = HomeCardBg,
            border = BorderStroke(1.dp, HomeCardBorder),
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxWidth(0.92f)
                .heightIn(max = 640.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(HomeCardElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            tint = HomeWarmAccent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prompt Git Changes",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeTextPrimary
                        )
                        val repoInfo = listOfNotNull(
                            diffStats.repoSlug ?: liveGitStatus?.repoSlug,
                            diffStats.branch ?: liveGitStatus?.branch,
                            formatTelemetryShortTime(diffStats.timestampMs).takeIf { it.isNotEmpty() }
                        ).joinToString(" · ")
                        if (repoInfo.isNotBlank()) {
                            Text(
                                text = repoInfo,
                                fontSize = 11.5.sp,
                                color = HomeTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = HomeTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                // Summary Card (+additions, -deletions, operations breakdown & bar)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = HomeCardElevated,
                    border = BorderStroke(0.8.dp, HomeCardBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "+${diffStats.additions}",
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = HomeAddGreen
                                )
                                Text(
                                    text = "-${diffStats.deletions}",
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = HomeDelRed
                                )
                            }
                            Text(
                                text = "${maxOf(diffStats.filesChanged, fileRows.size)} ${if (maxOf(diffStats.filesChanged, fileRows.size) == 1) "file" else "files"}",
                                fontSize = 11.5.sp,
                                color = HomeTextSecondary
                            )
                        }

                        val totalDelta = (diffStats.additions + diffStats.deletions).coerceAtLeast(1)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(CircleShape)
                                .background(HomeCardBg)
                        ) {
                            if (diffStats.additions > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight((diffStats.additions.toFloat() / totalDelta.toFloat()).coerceAtLeast(0.04f))
                                        .height(5.dp)
                                        .background(HomeAddGreen)
                                )
                            }
                            if (diffStats.deletions > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight((diffStats.deletions.toFloat() / totalDelta.toFloat()).coerceAtLeast(0.04f))
                                        .height(5.dp)
                                        .background(HomeDelRed)
                                )
                            }
                        }

                        if (modifiedCount > 0 || addedCount > 0 || deletedCount > 0) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                if (modifiedCount > 0) OperationCountTag("$modifiedCount modified", HomeWarnAmber)
                                if (addedCount > 0) OperationCountTag("$addedCount added", HomeAddGreen)
                                if (deletedCount > 0) OperationCountTag("$deletedCount deleted", HomeDelRed)
                            }
                        }
                    }
                }

                // Changed Files List for this Prompt
                if (fileRows.isNotEmpty()) {
                    Text(
                        text = if (resolvedProjectPath.isNotBlank()) "MODIFIED IN THIS TURN (TAP FOR DIFF)" else "MODIFIED IN THIS TURN",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.6.sp,
                        color = HomeTextSecondary
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        fileRows.forEach { file ->
                            HomeGitChangedFileRow(
                                file = file,
                                onClick = {
                                    if (resolvedProjectPath.isNotBlank()) {
                                        selectedDiffFile = file
                                    }
                                }
                            )
                        }
                    }
                }

                // Bottom Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (onOpenRepoHub != null && resolvedProjectPath.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onOpenRepoHub()
                            },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, HomeCardBorder),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AltRoute,
                                contentDescription = null,
                                tint = HomeWarmAccent,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Repo Status", fontSize = 12.5.sp, color = HomeTextPrimary, fontWeight = FontWeight.Medium)
                        }
                    }
                    Button(
                        onClick = onDismiss,
                        colors = graventNeutralButtonColors(),
                        border = BorderStroke(1.dp, graventTokens().neutralBtnBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    selectedDiffFile?.let { fileStatus ->
        HomeFileDiffDialog(
            projectPath = resolvedProjectPath,
            file = fileStatus,
            onDismiss = { selectedDiffFile = null },
            onOpenInIde = { relPath ->
                selectedDiffFile = null
                onDismiss()
                onOpenInIde(relPath)
            }
        )
    }
}

@Composable
private fun OperationCountTag(label: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = HomeCardBg,
        border = BorderStroke(0.6.dp, tint.copy(alpha = 0.28f))
    ) {
        Text(
            text = label,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HomeRepoGitSection(
    projectPath: String,
    isBridgeOnline: Boolean,
    gitStatus: GitStatusResponse?,
    isGitLoading: Boolean,
    alwaysExpanded: Boolean,
    onRefreshGit: () -> Unit,
    onOpenInIde: (relativePath: String?) -> Unit,
    onAskReview: (prompt: String) -> Unit,
    onCommit: (message: String, alsoPush: Boolean, onResult: (Boolean, String?) -> Unit) -> Unit,
    onPush: (onResult: (Boolean, String?) -> Unit) -> Unit,
    onPull: (onResult: (Boolean, String?) -> Unit) -> Unit,
    onCheckoutBranch: (branch: String, create: Boolean, onResult: (Boolean, String?) -> Unit) -> Unit,
    onDiscardAll: (paths: List<String>, onResult: (Boolean, String?) -> Unit) -> Unit
) {
    var isExpandedState by rememberSaveable(projectPath, alwaysExpanded) { mutableStateOf(alwaysExpanded) }
    val isExpanded = if (alwaysExpanded) true else isExpandedState
    var showAllFiles by remember(projectPath) { mutableStateOf(alwaysExpanded) }
    var selectedDiffFile by remember { mutableStateOf<GitFileStatus?>(null) }
    var showBranchDialog by remember { mutableStateOf(false) }
    var showCommitDialog by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }
    var actionFeedback by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    if (isGitLoading && gitStatus != null) {
        LinearProgressIndicator(
            color = HomeWarmAccent,
            trackColor = HomeCardDivider,
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
        )
    } else {
        HorizontalDivider(thickness = 0.6.dp, color = HomeCardDivider)
    }

    when {
        !isBridgeOnline -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(HomeTextSecondary.copy(alpha = 0.6f), CircleShape)
                )
                Text(
                    text = "Local bridge offline · Git status unavailable",
                    fontSize = 11.sp,
                    color = HomeTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        isGitLoading && gitStatus == null -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(11.dp),
                    strokeWidth = 1.5.dp,
                    color = HomeTextSecondary
                )
                Text(
                    text = "Checking repository status…",
                    fontSize = 11.sp,
                    color = HomeTextSecondary
                )
            }
        }

        gitStatus != null && !gitStatus.isGitRepo -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Local folder · not initialized with Git",
                    fontSize = 11.sp,
                    color = HomeTextSecondary
                )
            }
        }

        gitStatus != null -> {
            val hasUncommitted = gitStatus.hasChanges
            val hasSyncPending = gitStatus.ahead > 0 || gitStatus.behind > 0
            val canExpand = hasUncommitted || hasSyncPending

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (canExpand && !alwaysExpanded) {
                            Modifier.clickable { isExpandedState = !isExpandedState }
                        } else Modifier
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { showBranchDialog = true },
                        shape = RoundedCornerShape(6.dp),
                        color = HomeCardElevated,
                        border = BorderStroke(0.6.dp, HomeCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AltRoute,
                                contentDescription = "Branch",
                                tint = if (hasUncommitted) HomeWarnAmber else HomeAddGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = gitStatus.branch,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                color = HomeTextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp)
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Switch branch",
                                tint = HomeTextSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }

                    val slug = gitStatus.repoSlug
                    if (!slug.isNullOrBlank()) {
                        Text(
                            text = slug,
                            fontSize = 11.sp,
                            color = HomeTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    if (gitStatus.ahead > 0) {
                        Text(
                            text = "↑${gitStatus.ahead}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeSkyBlue
                        )
                    }
                    if (gitStatus.behind > 0) {
                        Text(
                            text = "↓${gitStatus.behind}",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeWarnAmber
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (hasUncommitted) {
                        Surface(
                            shape = RoundedCornerShape(7.dp),
                            color = HomeCardElevated,
                            border = BorderStroke(0.6.dp, HomeCardBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = "${gitStatus.totalChangesCount}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = HomeTextPrimary
                                )
                                if (gitStatus.totalAdditions > 0) {
                                    Text(
                                        text = "+${gitStatus.totalAdditions}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = HomeAddGreen
                                    )
                                }
                                if (gitStatus.totalDeletions > 0) {
                                    Text(
                                        text = "-${gitStatus.totalDeletions}",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        color = HomeDelRed
                                    )
                                }
                                if (!alwaysExpanded) {
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse changes" else "Expand changes",
                                        tint = HomeTextSecondary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(HomeAddGreen, CircleShape)
                            )
                            Text(
                                text = "Clean",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = HomeAddGreen
                            )
                            if (hasSyncPending && !alwaysExpanded) {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = null,
                                    tint = HomeTextSecondary,
                                    modifier = Modifier.size(14.dp)
                                )
                            } else if (!alwaysExpanded) {
                                IconButton(
                                    onClick = onRefreshGit,
                                    modifier = Modifier.size(22.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Git status",
                                        tint = HomeTextSecondary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            actionFeedback?.let { (isOk, msg) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = msg,
                        fontSize = 11.sp,
                        color = if (isOk) HomeAddGreen else HomeDelRed,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { actionFeedback = null },
                        modifier = Modifier.size(18.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = HomeTextSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = isExpanded && canExpand,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                val combinedFiles = remember(gitStatus) {
                    (gitStatus.stagedFiles + gitStatus.unstagedFiles + gitStatus.untrackedFiles)
                }
                val visibleFiles = if (showAllFiles) combinedFiles else combinedFiles.take(5)
                val countsSummary = remember(gitStatus) {
                    buildList {
                        if (gitStatus.stagedFiles.isNotEmpty()) add("${gitStatus.stagedFiles.size} staged")
                        if (gitStatus.unstagedFiles.isNotEmpty()) add("${gitStatus.unstagedFiles.size} modified")
                        if (gitStatus.untrackedFiles.isNotEmpty()) add("${gitStatus.untrackedFiles.size} untracked")
                    }.joinToString(" · ")
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(graventTokens().canvasBg)
                ) {
                    HorizontalDivider(thickness = 0.6.dp, color = HomeCardDivider)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = countsSummary.ifBlank { "Sync pending with remote" },
                            fontSize = 10.5.sp,
                            color = HomeTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onRefreshGit,
                                modifier = Modifier.size(22.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh",
                                    tint = HomeTextSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = "Open SCM",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = HomeWarmAccent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { onOpenInIde(null) }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (visibleFiles.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (!alwaysExpanded && showAllFiles && combinedFiles.size > 6) {
                                        Modifier
                                            .heightIn(max = 210.dp)
                                            .verticalScroll(rememberScrollState())
                                    } else Modifier
                                )
                                .padding(horizontal = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            visibleFiles.forEach { file ->
                                HomeGitChangedFileRow(
                                    file = file,
                                    onClick = { selectedDiffFile = file }
                                )
                            }
                        }

                        if (!alwaysExpanded && combinedFiles.size > 5) {
                            TextButton(
                                onClick = { showAllFiles = !showAllFiles },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text(
                                    text = if (showAllFiles) {
                                        "Show fewer files"
                                    } else {
                                        "Show all ${combinedFiles.size} changed files"
                                    },
                                    fontSize = 11.sp,
                                    color = HomeTextSecondary
                                )
                            }
                        }
                    }

                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (hasUncommitted) {
                            HomeGitActionChip(
                                label = "Review changes",
                                icon = Icons.Outlined.AutoAwesome,
                                accent = HomeWarmAccent,
                                enabled = !isGitLoading,
                                onClick = {
                                    val fileSample = combinedFiles.take(8).joinToString(", ") { it.path }
                                    val suffix = if (combinedFiles.size > 8) " (+${combinedFiles.size - 8} more)" else ""
                                    val prompt = "Review the ${gitStatus.totalChangesCount} uncommitted files " +
                                        "(+${gitStatus.totalAdditions} -${gitStatus.totalDeletions}) on branch `${gitStatus.branch}` " +
                                        "[$fileSample$suffix]. Identify any bugs, edge cases, or regressions."
                                    onAskReview(prompt)
                                }
                            )

                            HomeGitActionChip(
                                label = "Commit",
                                icon = Icons.Default.Check,
                                accent = HomeWarmAccent,
                                enabled = !isGitLoading,
                                onClick = { showCommitDialog = true }
                            )
                        }

                        if (gitStatus.ahead > 0) {
                            HomeGitActionChip(
                                label = "Push ↑${gitStatus.ahead}",
                                icon = Icons.Default.CloudUpload,
                                accent = HomeSkyBlue,
                                enabled = !isGitLoading,
                                onClick = {
                                    onPush { ok, err ->
                                        actionFeedback = ok to (err ?: "Pushed to remote.")
                                    }
                                }
                            )
                        }

                        if (gitStatus.behind > 0) {
                            HomeGitActionChip(
                                label = "Pull ↓${gitStatus.behind}",
                                icon = Icons.Default.CloudDownload,
                                accent = HomeWarnAmber,
                                enabled = !isGitLoading,
                                onClick = {
                                    onPull { ok, err ->
                                        actionFeedback = ok to (err ?: "Pulled latest changes.")
                                    }
                                }
                            )
                        }

                        if (hasUncommitted) {
                            HomeGitActionChip(
                                label = "Discard",
                                icon = Icons.Default.Undo,
                                accent = HomeDelRed,
                                enabled = !isGitLoading,
                                onClick = { showDiscardConfirm = true }
                            )
                        }
                    }
                }
            }
        }

        else -> Unit
    }

    if (showBranchDialog && gitStatus != null) {
        HomeBranchPickerDialog(
            projectPath = projectPath,
            currentBranch = gitStatus.branch,
            onDismiss = { showBranchDialog = false },
            onSelectBranch = { branch, create ->
                onCheckoutBranch(branch, create) { ok, err ->
                    actionFeedback = ok to (err ?: "Switched to branch $branch")
                    if (ok) showBranchDialog = false
                }
            }
        )
    }

    if (showCommitDialog && gitStatus != null) {
        HomeCommitDialog(
            gitStatus = gitStatus,
            isBusy = isGitLoading,
            onDismiss = { showCommitDialog = false },
            onCommit = { msg, alsoPush, onInlineError ->
                onCommit(msg, alsoPush) { ok, err ->
                    if (ok) {
                        actionFeedback = true to if (alsoPush) "Committed and pushed changes." else "Changes committed."
                        showCommitDialog = false
                    } else {
                        onInlineError(err ?: "Commit failed.")
                    }
                }
            }
        )
    }

    if (showDiscardConfirm && gitStatus != null) {
        val allPaths = remember(gitStatus) {
            (gitStatus.stagedFiles + gitStatus.unstagedFiles + gitStatus.untrackedFiles)
                .map { it.path }
                .distinct()
        }
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            containerColor = HomeCardBg,
            title = {
                Text(
                    text = "Discard uncommitted changes?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeTextPrimary
                )
            },
            text = {
                Text(
                    text = "This will revert ${allPaths.size} modified/untracked files (-${gitStatus.totalAdditions} additions, -${gitStatus.totalDeletions} deletions removed from working tree). This cannot be undone.",
                    fontSize = 13.sp,
                    color = HomeTextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirm = false
                        onDiscardAll(allPaths) { ok, err ->
                            actionFeedback = ok to (err ?: "Working tree restored.")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HomeDelRed, contentColor = Color.Black)
                ) {
                    Text("Discard All", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirm = false }) {
                    Text("Cancel", color = HomeWarmAccent)
                }
            }
        )
    }

    selectedDiffFile?.let { fileStatus ->
        HomeFileDiffDialog(
            projectPath = projectPath,
            file = fileStatus,
            onDismiss = { selectedDiffFile = null },
            onOpenInIde = { relPath ->
                selectedDiffFile = null
                onOpenInIde(relPath)
            }
        )
    }
}

@Composable
fun HomeRecentDiffBadge(
    diffStats: TurnDiffStats?,
    modifier: Modifier = Modifier
) {
    if (diffStats == null || (diffStats.additions <= 0 && diffStats.deletions <= 0)) return
    val t = graventTokens()
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(6.dp),
        color = t.gitAddBg,
        border = BorderStroke(0.6.dp, t.gitAddBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (diffStats.additions > 0) {
                Text(
                    text = "+${diffStats.additions}",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeAddGreen
                )
            }
            if (diffStats.deletions > 0) {
                Text(
                    text = "-${diffStats.deletions}",
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold,
                    color = HomeDelRed
                )
            }
        }
    }
}

@Composable
private fun HomeGitChangedFileRow(
    file: GitFileStatus,
    onClick: () -> Unit
) {
    val fileName = remember(file.path) { file.path.substringAfterLast('/') }
    val dirPath = remember(file.path) {
        val idx = file.path.lastIndexOf('/')
        if (idx > 0) file.path.substring(0, idx) else ""
    }
    val (badgeText, badgeColor) = when (file.status.uppercase()) {
        "A" -> "A" to HomeAddGreen
        "D" -> "D" to HomeDelRed
        "R" -> "R" to HomeSkyBlue
        "U", "?" -> "U" to HomeAddGreen
        else -> "M" to HomeWarnAmber
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = HomeCardElevated
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 9.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = badgeText,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = badgeColor,
                modifier = Modifier.width(15.dp)
            )
            if (file.staged) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(HomeAddGreen, CircleShape)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = HomeTextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (dirPath.isNotBlank()) {
                    Text(
                        text = dirPath,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = HomeTextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (file.additions > 0) {
                    Text(
                        text = "+${file.additions}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = HomeAddGreen
                    )
                }
                if (file.deletions > 0) {
                    Text(
                        text = "-${file.deletions}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = HomeDelRed
                    )
                }
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "View diff",
                    tint = HomeTextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeGitActionChip(
    label: String,
    icon: ImageVector,
    accent: Color,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = HomeCardElevated,
        border = BorderStroke(0.7.dp, HomeCardBorder)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) accent else HomeTextSecondary,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (enabled) HomeTextPrimary else HomeTextSecondary
            )
        }
    }
}

@Composable
private fun HomeBranchPickerDialog(
    projectPath: String,
    currentBranch: String,
    onDismiss: () -> Unit,
    onSelectBranch: (branch: String, create: Boolean) -> Unit
) {
    var branches by remember { mutableStateOf<List<GitBranchInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var newBranchName by remember { mutableStateOf("") }

    LaunchedEffect(projectPath) {
        isLoading = true
        branches = withContext(Dispatchers.IO) {
            GitApiClient.getBranches(projectPath)
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = HomeCardBg,
            border = BorderStroke(1.dp, HomeCardBorder),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.9f)
                .heightIn(max = 520.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AltRoute,
                            contentDescription = null,
                            tint = HomeWarmAccent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Switch or Create Branch",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = HomeTextPrimary
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = HomeTextSecondary, modifier = Modifier.size(18.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it },
                        placeholder = { Text("New branch name…", fontSize = 12.5.sp) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = HomeWarmAccent,
                            unfocusedBorderColor = HomeCardBorder,
                            focusedTextColor = HomeTextPrimary,
                            unfocusedTextColor = HomeTextPrimary
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            val clean = newBranchName.trim()
                            if (clean.isNotBlank()) onSelectBranch(clean, true)
                        },
                        enabled = newBranchName.trim().isNotBlank(),
                        colors = graventPrimaryButtonColors(),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("Create", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = HomeWarmAccent)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        branches.filter { !it.isRemote }.forEach { b ->
                            val isCurrent = b.isCurrent || b.name == currentBranch
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (!isCurrent) onSelectBranch(b.name, false) else onDismiss()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isCurrent) HomeCardElevated else Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = b.name,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isCurrent) HomeWarmAccent else HomeTextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (isCurrent) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Active",
                                            tint = HomeAddGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeCommitDialog(
    gitStatus: GitStatusResponse,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onCommit: (message: String, alsoPush: Boolean, onInlineError: (String) -> Unit) -> Unit
) {
    var commitMessage by remember { mutableStateOf("") }
    var alsoPush by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = HomeCardBg,
            border = BorderStroke(1.dp, HomeCardBorder),
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Commit Changes (${gitStatus.branch})",
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = HomeTextPrimary
                )
                Text(
                    text = "Stage & commit ${gitStatus.totalChangesCount} files (+${gitStatus.totalAdditions} -${gitStatus.totalDeletions}).",
                    fontSize = 12.sp,
                    color = HomeTextSecondary
                )

                val suggestedCommitMsg = remember(gitStatus) {
                    val paths = (gitStatus.stagedFiles + gitStatus.unstagedFiles + gitStatus.untrackedFiles)
                        .map { it.path.substringAfterLast('/') }
                        .distinct()
                    when {
                        paths.isEmpty() -> ""
                        paths.size == 1 -> "Update ${paths.first()}"
                        paths.size == 2 -> "Update ${paths[0]} and ${paths[1]}"
                        else -> "Update ${paths[0]}, ${paths[1]} (+${paths.size - 2} more)"
                    }
                }
                if (commitMessage.isBlank() && suggestedCommitMsg.isNotBlank()) {
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(7.dp))
                            .clickable {
                                commitMessage = suggestedCommitMsg
                                errorMessage = null
                            },
                        shape = RoundedCornerShape(7.dp),
                        color = HomeCardElevated,
                        border = BorderStroke(0.6.dp, HomeCardBorder)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoAwesome,
                                contentDescription = null,
                                tint = HomeWarmAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Suggest: $suggestedCommitMsg",
                                fontSize = 11.sp,
                                color = HomeWarmAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = commitMessage,
                    onValueChange = {
                        commitMessage = it
                        errorMessage = null
                    },
                    label = { Text("Commit message", fontSize = 12.sp) },
                    placeholder = { Text("Describe your changes…", fontSize = 12.5.sp) },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = HomeWarmAccent,
                        unfocusedBorderColor = HomeCardBorder,
                        focusedTextColor = HomeTextPrimary,
                        unfocusedTextColor = HomeTextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                if (!gitStatus.remoteUrl.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { alsoPush = !alsoPush }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = alsoPush,
                            onCheckedChange = { alsoPush = it },
                            colors = graventCheckboxColors()
                        )
                        Text(
                            text = "Push to origin immediately",
                            fontSize = 12.5.sp,
                            color = HomeTextPrimary
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage!!,
                        fontSize = 11.5.sp,
                        color = HomeDelRed
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isBusy) {
                        Text("Cancel", color = HomeTextSecondary)
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Button(
                        onClick = {
                            onCommit(commitMessage, alsoPush) { err -> errorMessage = err }
                        },
                        enabled = !isBusy && commitMessage.trim().isNotBlank(),
                        colors = graventPrimaryButtonColors(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = if (isBusy) "Working…" else if (alsoPush) "Commit & Push" else "Commit",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeFileDiffDialog(
    projectPath: String,
    file: GitFileStatus,
    onDismiss: () -> Unit,
    onOpenInIde: (String) -> Unit
) {
    var diffResponse by remember { mutableStateOf<GitDiffResponse?>(null) }
    var untrackedPreviewDiff by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(projectPath, file.path, file.staged) {
        isLoading = true
        val fetched = withContext(Dispatchers.IO) {
            GitApiClient.getDiff(projectPath, file.path, file.staged)
        }
        diffResponse = fetched
        if (fetched?.diff.isNullOrBlank() && (file.status == "U" || file.status == "?" || file.status == "A")) {
            untrackedPreviewDiff = withContext(Dispatchers.IO) {
                runCatching {
                    val target = File(projectPath, file.path)
                    if (target.isFile && target.length() <= 64 * 1024L) {
                        val lines = target.readLines()
                        buildString {
                            append("--- /dev/null\n")
                            append("+++ b/${file.path}\n")
                            append("@@ -0,0 +1,${lines.size} @@\n")
                            lines.forEach { append("+$it\n") }
                        }
                    } else null
                }.getOrNull()
            }
        }
        isLoading = false
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFF0E0E11),
            border = BorderStroke(1.dp, HomeCardBorder),
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.86f)
        ) {
            val rawDiff = diffResponse?.diff?.takeIf { it.isNotBlank() } ?: untrackedPreviewDiff.orEmpty()
            when {
                isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = HomeWarmAccent)
                    }
                }
                rawDiff.isNotBlank() -> {
                    UnifiedDiffViewer(
                        filePath = file.path,
                        rawDiff = rawDiff,
                        isStaged = file.staged,
                        commitHash = null,
                        onOpenCurrentFile = {
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    runCatching {
                                        val diskFile = File(projectPath, file.path)
                                        if (diskFile.isFile && diskFile.length() <= 512 * 1024L) {
                                            com.example.gemini.data.daemon.TermuxDaemonManager.openOrSelectTab(
                                                diskFile.absolutePath,
                                                diskFile.name,
                                                diskFile.readText()
                                            )
                                        }
                                    }
                                }
                                onOpenInIde(file.path)
                            }
                        },
                        onOpenHistoricalFile = {},
                        onClose = onDismiss,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = file.path,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = HomeTextPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "No textual diff available (binary, empty, or submodule entry).",
                            fontSize = 12.5.sp,
                            color = HomeTextSecondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onDismiss) {
                                Text("Close", color = HomeTextPrimary)
                            }
                            Button(
                                onClick = { onOpenInIde(file.path) },
                                colors = graventPrimaryButtonColors()
                            ) {
                                Text("Open IDE", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun abbreviateHomeDirectoryPath(path: String): String {
    if (path.isBlank()) return ""
    val clean = path.removePrefix("file://").trimEnd('/')
    val homePrefix = "/data/user/0/com.termux/files/home"
    val legacyPrefix = "/data/data/com.termux/files/home"
    val normalized = when {
        clean.startsWith(homePrefix) -> "~" + clean.removePrefix(homePrefix)
        clean.startsWith(legacyPrefix) -> "~" + clean.removePrefix(legacyPrefix)
        else -> clean
    }
    if (normalized.length <= 36) return normalized
    val parts = normalized.split('/').filter { it.isNotBlank() }
    return if (parts.size <= 2) normalized else "…/" + parts.takeLast(2).joinToString("/")
}
