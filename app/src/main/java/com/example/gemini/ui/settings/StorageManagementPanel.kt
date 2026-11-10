package com.example.gemini.ui.settings

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.local.*
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.QuotaGreen
import kotlinx.coroutines.launch

private data class StorageUiPalette(
    val cardBg: Color,
    val cardBorder: BorderStroke,
    val nestedBg: Color,
    val subChipBg: Color,
    val subtleBorder: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val mutedText: Color,
    val isDark: Boolean
)

private fun buildStoragePalette(cardBg: Color, cardBorder: BorderStroke): StorageUiPalette {
    val isDark = cardBg.luminance() < 0.5f
    return StorageUiPalette(
        cardBg = cardBg,
        cardBorder = cardBorder,
        nestedBg = if (isDark) Color(0xFF141418) else Color(0xFFFAFAFA),
        subChipBg = if (isDark) Color(0xFF18181B) else Color(0xFFF4F4F5),
        subtleBorder = if (isDark) Color(0xFF27272A) else Color(0xFFE4E4E7),
        primaryText = if (isDark) Color(0xFFFAFAFA) else Color(0xFF18181B),
        secondaryText = if (isDark) Color(0xFFA1A1AA) else Color(0xFF52525B),
        mutedText = if (isDark) Color(0xFF71717A) else Color(0xFF71717A),
        isDark = isDark
    )
}

private sealed class PendingConfirmAction {
    data class CleanDevCaches(val bytes: Long, val count: Int) : PendingConfirmAction()
    data class CleanBucket(val bucket: StorageBucketItem) : PendingConfirmAction()
    data class DeleteArtifact(val artifact: WorkspaceArtifactItem) : PendingConfirmAction()
    data class DeleteLargeFile(val entry: LargeFileEntry) : PendingConfirmAction()
    data class DeleteExternalBackup(val item: ExternalBackupItem) : PendingConfirmAction()
}

@Composable
fun StorageManagementPanel(
    cardBg: Color,
    cardBorder: BorderStroke,
    modifier: Modifier = Modifier,
    extraBottomContent: (@Composable () -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val palette = remember(cardBg, cardBorder) { buildStoragePalette(cardBg, cardBorder) }

    val cachedReport by AppStorageManager.latestReport.collectAsState()
    var isScanning by remember { mutableStateOf(cachedReport == null) }
    var isCleaning by remember { mutableStateOf(false) }
    var activeCleanLabel by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<StorageCleanupResult?>(null) }

    var expandedCategories by remember {
        mutableStateOf(setOf(StorageCategoryId.CACHES_TEMP, StorageCategoryId.AI_AGENTS, StorageCategoryId.DEV_TOOLS))
    }
    var showExtraCachesList by remember { mutableStateOf(true) }
    var showToolsPackages by remember { mutableStateOf(false) }
    var showDpkgPackages by remember { mutableStateOf(false) }
    var showWorkspaces by remember { mutableStateOf(false) }
    var showLargestFiles by remember { mutableStateOf(false) }
    var showExternalBackups by remember { mutableStateOf(false) }
    var showAccuracyAudit by remember { mutableStateOf(false) }

    var pendingConfirm by remember { mutableStateOf<PendingConfirmAction?>(null) }

    fun triggerScan() {
        if (isCleaning) return
        scope.launch {
            isScanning = true
            runCatching { AppStorageManager.analyzeStorage(context) }
            isScanning = false
        }
    }

    fun runClean(label: String, action: suspend () -> StorageCleanupResult) {
        if (isCleaning) return
        scope.launch {
            isCleaning = true
            activeCleanLabel = label
            val res = runCatching { action() }.getOrElse {
                StorageCleanupResult(0L, 0L, 0, 1, "Cleanup error: ${it.message}")
            }
            lastResult = res
            activeCleanLabel = null
            isCleaning = false
        }
    }

    LaunchedEffect(Unit) {
        triggerScan()
    }

    val report = cachedReport

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Hero Exact App Storage Card
        item(key = "hero_storage") {
            StorageHeroCard(
                report = report,
                isScanning = isScanning,
                isCleaning = isCleaning,
                onRefresh = { triggerScan() },
                palette = palette
            )
        }

        // 2. Result Banner (after cleanup)
        if (lastResult != null) {
            item(key = "cleanup_banner") {
                val res = lastResult!!
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = QuotaGreen.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, QuotaGreen.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = QuotaGreen,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = res.summary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.primaryText
                            )
                            val detail = buildString {
                                append("${res.removedEntries} items removed")
                                if (res.skippedActiveEntries > 0) append(" • ${res.skippedActiveEntries} active files kept safe")
                                if (res.errorCount > 0) append(" • ${res.errorCount} skipped")
                            }
                            Text(
                                text = detail,
                                fontSize = 12.sp,
                                color = palette.secondaryText
                            )
                        }
                        IconButton(onClick = { lastResult = null }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Dismiss", tint = palette.secondaryText, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }

        // 3. Optimize Storage Card
        if (report != null) {
            item(key = "optimize_card") {
                StorageOptimizeCard(
                    report = report,
                    isBusy = isCleaning || isScanning,
                    activeCleanLabel = activeCleanLabel,
                    onQuickOptimize = {
                        runClean("Quick Optimize") { AppStorageManager.quickOptimize(context) }
                    },
                    onClearDevCaches = {
                        val devCount = report.extraCaches.count {
                            it.clearability == CacheClearability.DEV_REBUILD_CACHE && it.bytes > 0L
                        }
                        pendingConfirm = PendingConfirmAction.CleanDevCaches(report.reclaimableDevCacheBytes, devCount)
                    },
                    palette = palette
                )
            }
        }

        // 4. Extra Caches & Residual Cache Inspector
        if (report != null) {
            item(key = "extra_caches_header") {
                val activeCacheCount = report.extraCaches.count { it.bytes > 0L }
                StorageSectionHeaderCard(
                    icon = Icons.Outlined.CleaningServices,
                    iconTint = Color(0xFFF59E0B),
                    title = "Extra Caches & Residual Temp",
                    subtitle = if (activeCacheCount > 0) {
                        "$activeCacheCount active caches across OS, WebView, APT, dev tools & agents"
                    } else {
                        "All detected caches are clean (0 B)"
                    },
                    badgeText = Formatter.formatFileSize(context, report.totalAllCachesBytes),
                    badgeColor = if (report.totalAllCachesBytes > 0L) Color(0xFFF59E0B) else QuotaGreen,
                    isExpanded = showExtraCachesList,
                    onToggle = { showExtraCachesList = !showExtraCachesList },
                    palette = palette
                )
            }

            if (showExtraCachesList) {
                items(
                    items = report.extraCaches,
                    key = { "extra_cache_${it.id}" }
                ) { bucket ->
                    ExtraCacheRow(
                        bucket = bucket,
                        isBusy = isCleaning,
                        onClearClick = {
                            if (bucket.clearability == CacheClearability.SAFE_CACHE) {
                                runClean(bucket.title) { AppStorageManager.clearBucket(context, bucket) }
                            } else {
                                pendingConfirm = PendingConfirmAction.CleanBucket(bucket)
                            }
                        },
                        palette = palette
                    )
                }
            }
        }

        // 5. Storage Classification Section Header
        if (report != null) {
            item(key = "classification_title") {
                Text(
                    text = "STORAGE CLASSIFICATION",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = palette.secondaryText,
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }

            items(
                items = report.categories,
                key = { "cat_${it.category.name}" }
            ) { catSummary ->
                val isExpanded = catSummary.category in expandedCategories
                StorageCategoryCard(
                    summary = catSummary,
                    totalBytes = report.totalBytes,
                    isExpanded = isExpanded,
                    isBusy = isCleaning,
                    onToggle = {
                        expandedCategories = if (isExpanded) {
                            expandedCategories - catSummary.category
                        } else {
                            expandedCategories + catSummary.category
                        }
                    },
                    onClearBucket = { bucket ->
                        if (bucket.clearability == CacheClearability.SAFE_CACHE) {
                            runClean(bucket.title) { AppStorageManager.clearBucket(context, bucket) }
                        } else {
                            pendingConfirm = PendingConfirmAction.CleanBucket(bucket)
                        }
                    },
                    palette = palette
                )
            }
        }

        // 6. Tool & Package Deep Inspectors
        if (report != null) {
            item(key = "deep_inspectors_title") {
                Text(
                    text = "TOOL & PACKAGE INSPECTOR",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = palette.secondaryText,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp)
                )
            }

            // 6A. Workspaces & Rebuildable Artifacts
            if (report.workspaces.isNotEmpty() || report.workspaceArtifacts.isNotEmpty()) {
                item(key = "workspaces_header") {
                    val wsBytes = report.workspaces.sumOf { it.totalBytes }
                    val artBytes = report.workspaceArtifacts.sumOf { it.bytes }
                    StorageSectionHeaderCard(
                        icon = Icons.Outlined.FolderOpen,
                        iconTint = ClaudeTerracotta,
                        title = "Workspaces & Build Artifacts",
                        subtitle = "${report.workspaces.size} workspaces • ${Formatter.formatFileSize(context, artBytes)} in node_modules / build / venv",
                        badgeText = Formatter.formatFileSize(context, wsBytes),
                        badgeColor = ClaudeTerracotta,
                        isExpanded = showWorkspaces,
                        onToggle = { showWorkspaces = !showWorkspaces },
                        palette = palette
                    )
                }

                if (showWorkspaces) {
                    items(
                        items = report.workspaces,
                        key = { "ws_${it.path}" }
                    ) { ws ->
                        WorkspaceRowCard(ws = ws, palette = palette)
                    }
                    if (report.workspaceArtifacts.isNotEmpty()) {
                        item(key = "ws_artifacts_note") {
                            Text(
                                text = "Reclaimable dependencies/builds inside workspaces (included in workspace sizes above):",
                                fontSize = 12.sp,
                                color = palette.secondaryText,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                        items(
                            items = report.workspaceArtifacts,
                            key = { "ws_art_${it.path}" }
                        ) { artifact ->
                            WorkspaceArtifactRow(
                                artifact = artifact,
                                isBusy = isCleaning,
                                onDelete = { pendingConfirm = PendingConfirmAction.DeleteArtifact(artifact) },
                                palette = palette
                            )
                        }
                    }
                }
            }

            // 6B. Global npm Tool Packages
            if (report.globalPackages.isNotEmpty()) {
                item(key = "global_npm_header") {
                    val pkgTotal = report.globalPackages.sumOf { it.bytes }
                    StorageSectionHeaderCard(
                        icon = Icons.Outlined.Extension,
                        iconTint = Color(0xFFEC4899),
                        title = "Global Node.js / npm Packages",
                        subtitle = "${report.globalPackages.size} installed global npm packages",
                        badgeText = Formatter.formatFileSize(context, pkgTotal),
                        badgeColor = Color(0xFFEC4899),
                        isExpanded = showToolsPackages,
                        onToggle = { showToolsPackages = !showToolsPackages },
                        palette = palette
                    )
                }
                if (showToolsPackages) {
                    items(
                        items = report.globalPackages,
                        key = { "npm_pkg_${it.path}" }
                    ) { pkg ->
                        ToolPackageRow(pkg = pkg, palette = palette)
                    }
                }
            }

            // 6C. Linux Installed APT/DPKG Packages
            if (report.dpkgPackages.isNotEmpty()) {
                item(key = "dpkg_header") {
                    val dpkgSum = report.dpkgPackages.sumOf { it.estimatedBytes }
                    StorageSectionHeaderCard(
                        icon = Icons.Outlined.Inventory2,
                        iconTint = Color(0xFF10B981),
                        title = "Installed Linux Packages (dpkg)",
                        subtitle = "Top ${report.dpkgPackages.size} APT packages (dpkg registry metadata, already counted in Linux OS above)",
                        badgeText = "~${Formatter.formatFileSize(context, dpkgSum)}",
                        badgeColor = Color(0xFF10B981),
                        isExpanded = showDpkgPackages,
                        onToggle = { showDpkgPackages = !showDpkgPackages },
                        palette = palette
                    )
                }
                if (showDpkgPackages) {
                    items(
                        items = report.dpkgPackages,
                        key = { "dpkg_${it.environment}_${it.name}" }
                    ) { pkg ->
                        DpkgPackageRow(pkg = pkg, palette = palette)
                    }
                }
            }

            // 6D. Largest Files Finder
            if (report.largestFiles.isNotEmpty()) {
                item(key = "largest_files_header") {
                    val bigSum = report.largestFiles.sumOf { it.bytes }
                    StorageSectionHeaderCard(
                        icon = Icons.Outlined.Archive,
                        iconTint = Color(0xFF38BDF8),
                        title = "Top ${report.largestFiles.size} Largest Files",
                        subtitle = "Biggest individual files inside app private storage",
                        badgeText = Formatter.formatFileSize(context, bigSum),
                        badgeColor = Color(0xFF38BDF8),
                        isExpanded = showLargestFiles,
                        onToggle = { showLargestFiles = !showLargestFiles },
                        palette = palette
                    )
                }
                if (showLargestFiles) {
                    items(
                        items = report.largestFiles,
                        key = { "big_file_${it.absolutePath}" }
                    ) { fileEntry ->
                        LargeFileRow(
                            entry = fileEntry,
                            isBusy = isCleaning,
                            onDelete = { pendingConfirm = PendingConfirmAction.DeleteLargeFile(fileEntry) },
                            palette = palette
                        )
                    }
                }
            }

            // 7. External / Shared Storage Backups (Separate from Private Footprint!)
            item(key = "external_backups_header") {
                val ext = report.externalBackups
                StorageSectionHeaderCard(
                    icon = Icons.Outlined.FolderZip,
                    iconTint = Color(0xFF8B5CF6),
                    title = "Shared Downloads & Backups",
                    subtitle = if (!ext.hasPermission) {
                        "Separate from App Footprint • Grant file access to scan /sdcard/Download/Antigem"
                    } else if (ext.items.isEmpty()) {
                        "No archives in /sdcard/Download/Antigem (Separate from App Footprint)"
                    } else {
                        "${ext.items.size} archive/binary files in Downloads (Not inside App Footprint)"
                    },
                    badgeText = if (ext.totalBytes > 0L) Formatter.formatFileSize(context, ext.totalBytes) else "EXTERNAL",
                    badgeColor = Color(0xFF8B5CF6),
                    isExpanded = showExternalBackups,
                    onToggle = { showExternalBackups = !showExternalBackups },
                    palette = palette
                )
            }
            if (showExternalBackups && report.externalBackups.items.isNotEmpty()) {
                items(
                    items = report.externalBackups.items,
                    key = { "ext_bk_${it.path}" }
                ) { item ->
                    ExternalBackupRow(
                        item = item,
                        isBusy = isCleaning,
                        onDelete = { pendingConfirm = PendingConfirmAction.DeleteExternalBackup(item) },
                        palette = palette
                    )
                }
            }

            // 8. Measurement Accuracy & Scan Audit Card
            item(key = "accuracy_audit") {
                AccuracyAuditCard(
                    report = report,
                    isExpanded = showAccuracyAudit,
                    onToggle = { showAccuracyAudit = !showAccuracyAudit },
                    palette = palette
                )
            }
        }

        if (extraBottomContent != null) {
            item(key = "extra_bottom_content") {
                extraBottomContent()
            }
        }
    }

    // Confirmation Dialogs
    pendingConfirm?.let { action ->
        ConfirmStorageActionDialog(
            action = action,
            palette = palette,
            onDismiss = { pendingConfirm = null },
            onConfirm = {
                pendingConfirm = null
                when (action) {
                    is PendingConfirmAction.CleanDevCaches ->
                        runClean("Dev & Package Caches") { AppStorageManager.clearDevCaches(context) }
                    is PendingConfirmAction.CleanBucket ->
                        runClean(action.bucket.title) { AppStorageManager.clearBucket(context, action.bucket) }
                    is PendingConfirmAction.DeleteArtifact ->
                        runClean("Remove ${action.artifact.kind}") { AppStorageManager.deleteWorkspaceArtifact(context, action.artifact) }
                    is PendingConfirmAction.DeleteLargeFile ->
                        runClean("Delete ${action.entry.name}") { AppStorageManager.deleteLargeFile(context, action.entry) }
                    is PendingConfirmAction.DeleteExternalBackup ->
                        runClean("Delete ${action.item.name}") { AppStorageManager.deleteExternalBackupFile(context, action.item) }
                }
            }
        )
    }
}

@Composable
private fun StorageHeroCard(
    report: StorageReport?,
    isScanning: Boolean,
    isCleaning: Boolean,
    onRefresh: () -> Unit,
    palette: StorageUiPalette
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.cardBg,
        border = palette.cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(ClaudeTerracotta.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Storage,
                        contentDescription = null,
                        tint = ClaudeTerracotta,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Total App Storage Used",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.secondaryText
                    )
                    Text(
                        text = report?.let { Formatter.formatFileSize(context, it.totalBytes) } ?: "Scanning disk…",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = palette.primaryText
                    )
                }

                if (report != null && report.accuracy.deviceFreeBytes > 0L) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.subChipBg,
                        border = BorderStroke(1.dp, palette.subtleBorder)
                    ) {
                        Text(
                            text = "${Formatter.formatFileSize(context, report.accuracy.deviceFreeBytes)} free",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.primaryText.copy(alpha = 0.85f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                IconButton(
                    onClick = onRefresh,
                    enabled = !isScanning && !isCleaning,
                    modifier = Modifier.size(48.dp)
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color(0xFF38BDF8)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Rescan Storage",
                            tint = palette.primaryText,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (report != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${report.accuracy.scannedFiles} files • ${report.accuracy.scannedDirs} dirs • scanned in ${report.accuracy.scanDurationMs}ms (hardlink-deduped)",
                    fontSize = 12.sp,
                    color = palette.mutedText
                )

                val totalForBar = report.totalBytes.coerceAtLeast(1L)
                val visibleSegments = report.categories.filter { it.bytes > 0L }
                if (visibleSegments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(palette.subChipBg),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        visibleSegments.forEach { seg ->
                            val fraction = (seg.bytes.toFloat() / totalForBar.toFloat()).coerceAtLeast(0.015f)
                            Box(
                                modifier = Modifier
                                    .weight(fraction)
                                    .fillMaxHeight()
                                    .background(Color(seg.category.colorHex))
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HeroStatTile(
                            label = "App Binary",
                            value = Formatter.formatFileSize(context, report.appBinaryBytes),
                            accentColor = Color(0xFF6366F1),
                            palette = palette,
                            modifier = Modifier.weight(1f)
                        )
                        HeroStatTile(
                            label = "Tools & Data",
                            value = Formatter.formatFileSize(context, report.netUserDataBytes),
                            accentColor = Color(0xFF10B981),
                            palette = palette,
                            modifier = Modifier.weight(1f)
                        )
                        HeroStatTile(
                            label = "Caches & Temp",
                            value = Formatter.formatFileSize(context, report.totalAllCachesBytes),
                            accentColor = Color(0xFFF59E0B),
                            palette = palette,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroStatTile(
    label: String,
    value: String,
    accentColor: Color,
    palette: StorageUiPalette,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.subChipBg,
        border = BorderStroke(1.dp, palette.subtleBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = label,
                    fontSize = 12.sp,
                    color = palette.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = palette.primaryText,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun StorageOptimizeCard(
    report: StorageReport,
    isBusy: Boolean,
    activeCleanLabel: String?,
    onQuickOptimize: () -> Unit,
    onClearDevCaches: () -> Unit,
    palette: StorageUiPalette
) {
    val context = LocalContext.current
    val safeSize = report.reclaimableSafeCacheBytes
    val devSize = report.reclaimableDevCacheBytes

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.cardBg,
        border = palette.cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Optimize Storage",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.primaryText
                    )
                }

                val reclaimableTotal = safeSize + devSize
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (reclaimableTotal > 0L) Color(0xFFF59E0B).copy(alpha = 0.15f) else QuotaGreen.copy(alpha = 0.15f),
                    border = BorderStroke(
                        1.dp,
                        if (reclaimableTotal > 0L) Color(0xFFF59E0B).copy(alpha = 0.35f) else QuotaGreen.copy(alpha = 0.35f)
                    )
                ) {
                    Text(
                        text = if (reclaimableTotal > 0L) {
                            "Reclaim ${Formatter.formatFileSize(context, reclaimableTotal)}"
                        } else {
                            "Optimized"
                        },
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace,
                        color = if (reclaimableTotal > 0L) Color(0xFFF59E0B) else QuotaGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Text(
                text = "Quick Optimize clears OS cache, WebView HTTP/GPU cache, APT .deb archives, AI debug logs & stale /tmp without touching chats, logins, source code or packages.",
                fontSize = 12.sp,
                color = palette.secondaryText,
                lineHeight = 16.sp
            )

            Button(
                onClick = onQuickOptimize,
                enabled = !isBusy && safeSize > 0L,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (palette.isDark) Color(0xFFE8E5DF) else Color(0xFF18181B),
                    contentColor = if (palette.isDark) Color(0xFF09090B) else Color(0xFFFAFAFA),
                    disabledContainerColor = palette.subChipBg,
                    disabledContentColor = palette.mutedText
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                if (isBusy && activeCleanLabel == "Quick Optimize") {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = if (palette.isDark) Color(0xFF09090B) else Color(0xFFFAFAFA)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cleaning Safe Caches…", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                } else {
                    Icon(Icons.Outlined.CleaningServices, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (safeSize > 0L) {
                            "Quick Optimize • Clean ${Formatter.formatFileSize(context, safeSize)}"
                        } else {
                            "Safe Caches Already Clean (0 B)"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }

            if (devSize > 0L) {
                OutlinedButton(
                    onClick = onClearDevCaches,
                    enabled = !isBusy,
                    border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.45f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Clean Package Caches (npm, pip, Go, Cargo, Gradle) • ${Formatter.formatFileSize(context, devSize)}",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageSectionHeaderCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    badgeText: String,
    badgeColor: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    palette: StorageUiPalette
) {
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "sectionChevron"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.cardBg,
        border = palette.cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(19.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primaryText
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = palette.secondaryText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.35f))
            ) {
                Text(
                    text = badgeText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = badgeColor,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                tint = palette.secondaryText,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation)
            )
        }
    }
}

@Composable
private fun ExtraCacheRow(
    bucket: StorageBucketItem,
    isBusy: Boolean,
    onClearClick: () -> Unit,
    palette: StorageUiPalette
) {
    val context = LocalContext.current
    val hasBytes = bucket.bytes > 0L

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.nestedBg,
        border = BorderStroke(1.dp, palette.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = bucket.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasBytes) palette.primaryText else palette.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    ClearabilityTag(bucket.clearability)
                }
                Text(
                    text = "${bucket.pathHint} • ${bucket.fileCount} files",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = palette.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = Formatter.formatFileSize(context, bucket.bytes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = if (hasBytes) Color(0xFFF59E0B) else QuotaGreen
            )
            if (hasBytes) {
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = onClearClick,
                    enabled = !isBusy,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, palette.subtleBorder),
                    modifier = Modifier.heightIn(min = 36.dp)
                ) {
                    Text("Clear", fontSize = 12.sp, color = palette.primaryText)
                }
            }
        }
    }
}

@Composable
private fun ClearabilityTag(clearability: CacheClearability) {
    val (label, tint) = when (clearability) {
        CacheClearability.SAFE_CACHE -> "SAFE" to QuotaGreen
        CacheClearability.DEV_REBUILD_CACHE -> "REBUILDABLE" to Color(0xFFF59E0B)
        CacheClearability.CONFIRM_DATA -> "CONFIRM" to Color(0xFFEF4444)
        CacheClearability.NONE -> return
    }
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = tint.copy(alpha = 0.14f)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun StorageCategoryCard(
    summary: StorageCategorySummary,
    totalBytes: Long,
    isExpanded: Boolean,
    isBusy: Boolean,
    onToggle: () -> Unit,
    onClearBucket: (StorageBucketItem) -> Unit,
    palette: StorageUiPalette
) {
    val context = LocalContext.current
    val catColor = Color(summary.category.colorHex)
    val pct = if (totalBytes > 0L) ((summary.bytes.toDouble() / totalBytes.toDouble()) * 100.0) else 0.0
    val pctLabel = if (pct >= 1.0 || pct == 0.0) "${pct.toInt()}%" else "<1%"
    val rotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 180),
        label = "catChevron"
    )

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.cardBg,
        border = palette.cardBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(catColor)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.category.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.primaryText
                    )
                    Text(
                        text = "${summary.items.size} components • ${summary.fileCount} files",
                        fontSize = 12.sp,
                        color = palette.secondaryText
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = Formatter.formatFileSize(context, summary.bytes),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = palette.primaryText
                    )
                    Text(
                        text = pctLabel,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = catColor
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Outlined.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = palette.secondaryText,
                    modifier = Modifier
                        .size(20.dp)
                        .rotate(rotation)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = palette.subtleBorder)
                    Text(
                        text = summary.category.description,
                        fontSize = 12.sp,
                        color = palette.mutedText,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    summary.items.forEach { bucket ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.subChipBg,
                            border = BorderStroke(1.dp, palette.subtleBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = bucket.title,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = palette.primaryText
                                    )
                                    Text(
                                        text = bucket.subtitle,
                                        fontSize = 12.sp,
                                        color = palette.secondaryText
                                    )
                                    Text(
                                        text = "${bucket.pathHint} • ${bucket.fileCount} files",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = palette.mutedText,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = Formatter.formatFileSize(context, bucket.bytes),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                        color = palette.primaryText
                                    )
                                    if (bucket.clearability != CacheClearability.NONE && bucket.bytes > 0L) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        TextButton(
                                            onClick = { onClearBucket(bucket) },
                                            enabled = !isBusy,
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.heightIn(min = 36.dp)
                                        ) {
                                            Text(
                                                text = "Clear",
                                                fontSize = 12.sp,
                                                color = if (bucket.clearability == CacheClearability.CONFIRM_DATA) Color(0xFFEF4444) else Color(0xFFF59E0B)
                                            )
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Core",
                                            fontSize = 12.sp,
                                            color = palette.mutedText
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
private fun WorkspaceRowCard(
    ws: WorkspaceStorageItem,
    palette: StorageUiPalette
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.nestedBg,
        border = BorderStroke(1.dp, palette.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(ws.name, fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
                val detail = buildString {
                    append("${ws.locationLabel} • ${ws.fileCount} files")
                    if (ws.artifactBytes > 0L) {
                        append(" • ${Formatter.formatFileSize(context, ws.artifactBytes)} deps/builds")
                    }
                }
                Text(detail, fontSize = 12.sp, color = palette.secondaryText)
            }
            Text(
                text = Formatter.formatFileSize(context, ws.totalBytes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = ClaudeTerracotta
            )
        }
    }
}

@Composable
private fun WorkspaceArtifactRow(
    artifact: WorkspaceArtifactItem,
    isBusy: Boolean,
    onDelete: () -> Unit,
    palette: StorageUiPalette
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.nestedBg,
        border = BorderStroke(1.dp, palette.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${artifact.projectName} / ${artifact.kind}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${artifact.fileCount} files • Rebuildable dependency/build directory",
                    fontSize = 12.sp,
                    color = palette.mutedText
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = Formatter.formatFileSize(context, artifact.bytes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFF59E0B)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onDelete,
                enabled = !isBusy,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                modifier = Modifier.heightIn(min = 36.dp)
            ) {
                Text("Remove", fontSize = 12.sp, color = Color(0xFFEF4444))
            }
        }
    }
}

@Composable
private fun ToolPackageRow(
    pkg: InstalledToolPackageItem,
    palette: StorageUiPalette
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.nestedBg,
        border = BorderStroke(1.dp, palette.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pkg.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = palette.primaryText)
                Text("${pkg.runtime} • ${pkg.fileCount} files", fontSize = 12.sp, color = palette.secondaryText)
            }
            Text(
                text = Formatter.formatFileSize(context, pkg.bytes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFEC4899)
            )
        }
    }
}

@Composable
private fun DpkgPackageRow(
    pkg: DpkgPackageEntry,
    palette: StorageUiPalette
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.nestedBg,
        border = BorderStroke(1.dp, palette.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(pkg.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace, color = palette.primaryText)
                Text("${pkg.environment} • v${pkg.version}", fontSize = 12.sp, color = palette.secondaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(
                text = "~${Formatter.formatFileSize(context, pkg.estimatedBytes)}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF10B981)
            )
        }
    }
}

@Composable
private fun LargeFileRow(
    entry: LargeFileEntry,
    isBusy: Boolean,
    onDelete: () -> Unit,
    palette: StorageUiPalette
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.nestedBg,
        border = BorderStroke(1.dp, palette.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${entry.relativePath} • ${entry.category.title}",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = palette.mutedText,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = Formatter.formatFileSize(context, entry.bytes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF38BDF8)
            )
            if (entry.canDeleteSafely) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onDelete,
                    enabled = !isBusy,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete File", tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun ExternalBackupRow(
    item: ExternalBackupItem,
    isBusy: Boolean,
    onDelete: () -> Unit,
    palette: StorageUiPalette
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.nestedBg,
        border = BorderStroke(1.dp, palette.subtleBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = palette.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${item.categoryLabel} • ${item.path}", fontSize = 12.sp, color = palette.mutedText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = Formatter.formatFileSize(context, item.bytes),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF8B5CF6)
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onDelete,
                enabled = !isBusy,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Delete Backup", tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun AccuracyAuditCard(
    report: StorageReport,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    palette: StorageUiPalette
) {
    val context = LocalContext.current
    val m = report.accuracy
    val os = report.osQuota

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = palette.cardBg,
        border = palette.cardBorder,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable { onToggle() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Verified, contentDescription = null, tint = QuotaGreen, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Scan Accuracy & Audit Details",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.primaryText,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = palette.secondaryText,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(
                    modifier = Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HorizontalDivider(color = palette.subtleBorder)
                    AuditLine("Measurement Method", "Physical blocks (Os.lstat) + StorageStats", palette)
                    AuditLine("Display Units", "Android SI Metric (1 MB = 1,000,000 B)", palette)
                    AuditLine("Live Physical Data Used", Formatter.formatFileSize(context, m.physicalBytesTotal), palette)
                    AuditLine("Logical File Size", Formatter.formatFileSize(context, m.logicalBytesTotal), palette)
                    AuditLine("Hardlinks Deduplicated", "${m.hardlinksSkipped} (${Formatter.formatFileSize(context, m.hardlinkSavedBytes)} saved)", palette)
                    AuditLine("Symlinks Safely Skipped", "${m.symlinksSkipped} (prevents loop double-count)", palette)
                    if (os.supported) {
                        AuditLine("OS Quota Total", Formatter.formatFileSize(context, os.totalBytes), palette)
                        AuditLine("OS Quota Net Data", Formatter.formatFileSize(context, os.netDataBytes), palette)
                        AuditLine("OS Quota Cache", Formatter.formatFileSize(context, os.cacheBytes), palette)
                    }
                    if (m.deviceTotalBytes > 0L) {
                        AuditLine(
                            "Device Internal Storage",
                            "${Formatter.formatFileSize(context, m.deviceFreeBytes)} free of ${Formatter.formatFileSize(context, m.deviceTotalBytes)}",
                            palette
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AuditLine(label: String, value: String, palette: StorageUiPalette) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.sp, color = palette.secondaryText)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = palette.primaryText.copy(alpha = 0.85f)
        )
    }
}

@Composable
private fun ConfirmStorageActionDialog(
    action: PendingConfirmAction,
    palette: StorageUiPalette,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val context = LocalContext.current
    val (title, body, confirmText, isDanger) = when (action) {
        is PendingConfirmAction.CleanDevCaches -> Quad(
            "Clean Developer & Package Caches?",
            "Clears ${Formatter.formatFileSize(context, action.bytes)} across ${action.count} package caches (npm, pnpm, pip, Go modules, Cargo, Gradle & APT index lists).\n\nYour projects, chats, binaries & installed packages are untouched. Package managers will re-fetch dependencies on demand.",
            "Clean Caches",
            false
        )
        is PendingConfirmAction.CleanBucket -> {
            val extraNote = if (action.bucket.isWebViewSiteData) {
                "This clears all cookies, localStorage, IndexedDB, saved site logins & open browser tabs in Gravent's built-in browser."
            } else {
                "Target: ${action.bucket.pathHint}\n\n${action.bucket.subtitle}."
            }
            Quad(
                "Clear ${action.bucket.title}?",
                "Reclaims ${Formatter.formatFileSize(context, action.bucket.bytes)}.\n\n$extraNote",
                "Clear",
                action.bucket.clearability == CacheClearability.CONFIRM_DATA
            )
        }
        is PendingConfirmAction.DeleteArtifact -> Quad(
            "Remove ${action.artifact.kind}?",
            "Permanently deletes ${action.artifact.path} (${Formatter.formatFileSize(context, action.artifact.bytes)}).\n\nYou can reinstall or rebuild it later inside your workspace.",
            "Remove",
            true
        )
        is PendingConfirmAction.DeleteLargeFile -> Quad(
            "Delete ${action.entry.name}?",
            "Permanently deletes this ${Formatter.formatFileSize(context, action.entry.bytes)} cache/temp file at:\n${action.entry.relativePath}",
            "Delete",
            false
        )
        is PendingConfirmAction.DeleteExternalBackup -> Quad(
            "Delete ${action.item.name}?",
            "Permanently deletes this ${Formatter.formatFileSize(context, action.item.bytes)} file from shared storage:\n${action.item.path}",
            "Delete",
            true
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.cardBg,
        titleContentColor = palette.primaryText,
        textContentColor = palette.secondaryText,
        title = { Text(title, fontWeight = FontWeight.Bold, fontSize = 17.sp) },
        text = { Text(body, fontSize = 13.5.sp, lineHeight = 19.sp) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDanger) MaterialTheme.colorScheme.error else Color(0xFFF59E0B),
                    contentColor = Color.Black
                )
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = palette.primaryText) }
        }
    )
}

private data class Quad(
    val title: String,
    val body: String,
    val confirmText: String,
    val isDanger: Boolean
)
