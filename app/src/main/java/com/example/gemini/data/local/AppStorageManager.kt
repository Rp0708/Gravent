package com.example.gemini.data.local

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.system.Os
import android.system.OsConstants
import android.text.format.Formatter
import android.util.Log
import com.example.gemini.ui.browser.BrowserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.util.PriorityQueue

enum class StorageCategoryId(
    val title: String,
    val description: String,
    val colorHex: Long
) {
    APP_BINARY(
        title = "App Binary & Runtime",
        description = "APK package, compiled ART/DEX code & native .so libraries",
        colorHex = 0xFF6366F1
    ),
    LINUX_ENV(
        title = "Linux Environments & Core OS",
        description = "Ubuntu PRoot rootfs, Termux base system, glibc & shell configs",
        colorHex = 0xFF10B981
    ),
    DEV_TOOLS(
        title = "Developer Toolchains & Packages",
        description = "Node.js, Python, Go, Rust, Clang/LLVM, Java, Git & browsers",
        colorHex = 0xFFEC4899
    ),
    AI_AGENTS(
        title = "AI Agents, Chats & CLI Tools",
        description = "Antigravity, Claude Code, Copilot chats, MCP binaries & brain data",
        colorHex = 0xFF38BDF8
    ),
    WORKSPACES(
        title = "Workspaces & Projects",
        description = "Coding repositories, project source trees & user home files",
        colorHex = 0xFFCC785C
    ),
    CACHES_TEMP(
        title = "Caches, Temp & Logs",
        description = "Reclaimable Android, WebView, APT, npm/pip/Go caches, /tmp & logs",
        colorHex = 0xFFF59E0B
    ),
    BROWSER_DATA(
        title = "Browser & Extension Data",
        description = "WebView cookies, localStorage, IndexedDB & browser extensions",
        colorHex = 0xFF8B5CF6
    ),
    APP_DATABASE(
        title = "App Databases, Notes & Settings",
        description = "SQLite databases, DataStore auth preferences, notes & SharedPrefs",
        colorHex = 0xFF64748B
    ),
    OTHER_DATA(
        title = "Other App & OS Overhead",
        description = "Device-protected metadata, external app files & misc files",
        colorHex = 0xFF52525B
    )
}

enum class CacheClearability {
    NONE,               // Protected core/data — do not delete from cache cleaner
    SAFE_CACHE,         // Safe auto-clean: OS cache, WebView HTTP cache, APT debs, logs, /tmp, __pycache__
    DEV_REBUILD_CACHE,  // Safe rebuildable package manager cache (npm, pnpm, yarn, pip, go, cargo, gradle, apt lists)
    CONFIRM_DATA        // Destructive/slow-to-reacquire: cookies/site storage, Playwright browsers, ML weights, recordings
}

data class StorageBucketItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: StorageCategoryId,
    val clearability: CacheClearability,
    val pathHint: String,
    val bytes: Long,
    val fileCount: Long,
    val targetPaths: List<String>,
    val isWebViewHttpCache: Boolean = false,
    val isWebViewSiteData: Boolean = false,
    val isSharedTemp: Boolean = false,
    val isAptArchiveDir: Boolean = false
)

data class StorageCategorySummary(
    val category: StorageCategoryId,
    val bytes: Long,
    val fileCount: Long,
    val items: List<StorageBucketItem>
)

data class WorkspaceStorageItem(
    val name: String,
    val path: String,
    val locationLabel: String,
    val totalBytes: Long,
    val artifactBytes: Long,
    val fileCount: Long
)

data class WorkspaceArtifactItem(
    val kind: String,        // "node_modules", ".gradle", "build", "target", "__pycache__", etc.
    val projectName: String,
    val path: String,
    val bytes: Long,
    val fileCount: Long
)

data class InstalledToolPackageItem(
    val name: String,
    val runtime: String, // "npm (Termux)", "npm (Ubuntu)", "Tool", etc.
    val path: String,
    val bytes: Long,
    val fileCount: Long
)

data class DpkgPackageEntry(
    val name: String,
    val environment: String, // "Termux" or "Ubuntu"
    val version: String,
    val estimatedBytes: Long // dpkg Installed-Size KiB * 1024 (metadata)
)

data class LargeFileEntry(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val category: StorageCategoryId,
    val bytes: Long,
    val logicalBytes: Long,
    val canDeleteSafely: Boolean
)

data class ExternalBackupItem(
    val name: String,
    val path: String,
    val categoryLabel: String,
    val bytes: Long,
    val lastModifiedMs: Long
)

data class ExternalStorageReport(
    val rootPath: String,
    val hasPermission: Boolean,
    val totalBytes: Long,
    val items: List<ExternalBackupItem>
)

data class OsQuotaSnapshot(
    val supported: Boolean,
    val appBytes: Long,
    val netDataBytes: Long,
    val cacheBytes: Long,
    val totalBytes: Long
)

data class ScanAccuracyMetrics(
    val scanDurationMs: Long,
    val scannedFiles: Long,
    val scannedDirs: Long,
    val hardlinksSkipped: Long,
    val hardlinkSavedBytes: Long,
    val symlinksSkipped: Long,
    val logicalBytesTotal: Long,
    val physicalBytesTotal: Long,
    val deviceFreeBytes: Long,
    val deviceTotalBytes: Long
)

data class StorageReport(
    val timestampMs: Long,
    val totalBytes: Long,
    val appBinaryBytes: Long,
    val netUserDataBytes: Long,
    val reclaimableSafeCacheBytes: Long,
    val reclaimableDevCacheBytes: Long,
    val totalAllCachesBytes: Long,
    val categories: List<StorageCategorySummary>,
    val extraCaches: List<StorageBucketItem>,
    val workspaces: List<WorkspaceStorageItem>,
    val workspaceArtifacts: List<WorkspaceArtifactItem>,
    val globalPackages: List<InstalledToolPackageItem>,
    val dpkgPackages: List<DpkgPackageEntry>,
    val largestFiles: List<LargeFileEntry>,
    val externalBackups: ExternalStorageReport,
    val osQuota: OsQuotaSnapshot,
    val accuracy: ScanAccuracyMetrics
)

data class StorageCleanupResult(
    val freedBytes: Long,
    val removedEntries: Long,
    val skippedActiveEntries: Int,
    val errorCount: Int,
    val summary: String
)

private data class BucketDef(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: StorageCategoryId,
    val clearability: CacheClearability,
    val pathHint: String,
    val isWebViewHttpCache: Boolean = false,
    val isWebViewSiteData: Boolean = false,
    val isSharedTemp: Boolean = false,
    val isAptArchiveDir: Boolean = false
)

object AppStorageManager {
    private const val TAG = "AppStorageManager"

    private val _latestReport = MutableStateFlow<StorageReport?>(null)
    val latestReport: StateFlow<StorageReport?> = _latestReport.asStateFlow()

    fun formatSize(context: Context, bytes: Long): String {
        if (bytes <= 0L) return "0 B"
        return Formatter.formatFileSize(context, bytes)
    }

    private val BUCKET_DEFS: Map<String, BucketDef> = listOf(
        // App binary (synthetic OS bucket)
        BucketDef(
            id = "app_binary",
            title = "APK, Native Libs & ART Compiled Code",
            subtitle = "Read-only system package managed by Android OS",
            category = StorageCategoryId.APP_BINARY,
            clearability = CacheClearability.NONE,
            pathHint = "APK + oat/ + lib/"
        ),

        // Caches & Temp - Safe Tier
        BucketDef(
            id = "cache_internal_app",
            title = "Android App Internal Cache",
            subtitle = "OkHttp, Compose, HTTP responses & temporary files",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "cache/"
        ),
        BucketDef(
            id = "cache_ext_app",
            title = "External App Cache",
            subtitle = "Cached downloads and external temporary files",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "Android/data/.../cache/"
        ),
        BucketDef(
            id = "cache_update_apks",
            title = "Downloaded Update APKs",
            subtitle = "Installer APK payloads left after self-updates",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "Android/data/.../cache/*.apk"
        ),
        BucketDef(
            id = "cache_code",
            title = "ART JIT & Shader Code Cache",
            subtitle = "Compiled shader & runtime bytecode cache",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "code_cache/"
        ),
        BucketDef(
            id = "cache_coil_images",
            title = "Image & SVG Disk Cache",
            subtitle = "Cached chat & asset thumbnails (Coil)",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "cache/image_cache/"
        ),
        BucketDef(
            id = "cache_shares",
            title = "Share & Export Temp",
            subtitle = "Shared chat transcripts, shared files & plugin export zips",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "cache/shared_* & plugin-export/"
        ),
        BucketDef(
            id = "cache_media_temp",
            title = "Voice Note & Attachment Temp Cache",
            subtitle = "Recorded audio clips & resolved bridge attachments",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "cache/voice_note_* & claude-attachments/"
        ),
        BucketDef(
            id = "cache_installer",
            title = "Bootstrap & Rootfs Installer Temp",
            subtitle = "Downloaded bootstrap zips & deb extraction scratch dirs",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "cache/bootstrap-* & deb_*_tmp/"
        ),
        BucketDef(
            id = "cache_backup_temp",
            title = "Backup & Restore Staging Payloads",
            subtitle = "Temporary archive payloads created during backup/restore",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "cache/*restore_payload*"
        ),
        BucketDef(
            id = "cache_de",
            title = "Device-Protected Cache",
            subtitle = "Cache stored under device-encrypted storage",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "user_de/.../cache/"
        ),
        BucketDef(
            id = "cache_webview_http",
            title = "WebView HTTP, JS & GPU Cache",
            subtitle = "Safe to clear — keeps cookies, logins & open tabs intact",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "app_webview/**/Cache & GPUCache",
            isWebViewHttpCache = true
        ),
        BucketDef(
            id = "cache_textures",
            title = "WebView & UI Texture Cache",
            subtitle = "Chromium Skia/GL texture scratch cache",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "app_textures/"
        ),
        BucketDef(
            id = "cache_apt_termux",
            title = "Termux APT .deb Package Cache",
            subtitle = "Downloaded .deb archives equivalent to 'apt clean'",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "usr/var/cache/apt/",
            isAptArchiveDir = true
        ),
        BucketDef(
            id = "cache_apt_ubuntu",
            title = "Ubuntu APT .deb Package Cache",
            subtitle = "Downloaded .deb archives inside PRoot Ubuntu",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "ubuntu/var/cache/apt/",
            isAptArchiveDir = true
        ),
        BucketDef(
            id = "cache_tmp_shared",
            title = "PRoot Shared /tmp",
            subtitle = "Stale temporary files (active sockets & tmux preserved)",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "files/tmp/",
            isSharedTemp = true
        ),
        BucketDef(
            id = "cache_tmp_termux",
            title = "Termux /usr/tmp",
            subtitle = "Temporary session & compiler scratch files",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "usr/tmp/",
            isSharedTemp = true
        ),
        BucketDef(
            id = "cache_tmp_ubuntu",
            title = "Ubuntu /tmp & /var/tmp",
            subtitle = "Temporary files inside Ubuntu container",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "ubuntu/tmp/ & ubuntu/var/tmp/",
            isSharedTemp = true
        ),
        BucketDef(
            id = "cache_shm",
            title = "Shared Memory Scratch (/dev/shm)",
            subtitle = "PRoot shared memory files (active sockets preserved)",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "files/shm/",
            isSharedTemp = true
        ),
        BucketDef(
            id = "cache_linux_logs",
            title = "Linux & Daemon System Logs",
            subtitle = "Service logs inside var/log",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "var/log/"
        ),
        BucketDef(
            id = "cache_agy_logs",
            title = "Antigravity / Gemini Logs, Crashes & Cache",
            subtitle = "Daemon logs, crash dumps, implicit telemetry & cache",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = ".gemini/**/log, crashes, implicit, cache"
        ),
        BucketDef(
            id = "cache_claude_logs",
            title = "Claude Code Debug Logs & Snapshots",
            subtitle = "Debug logs, shell-snapshots, paste/image cache & telemetry",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = ".claude/{debug,shell-snapshots,telemetry}"
        ),
        BucketDef(
            id = "cache_project_dev",
            title = "Project Bundler & Python Caches",
            subtitle = "__pycache__, node_modules/.cache, .next/cache, .vite, .turbo",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "**/__pycache__ & node_modules/.cache"
        ),
        BucketDef(
            id = "cache_trash",
            title = "Linux Trash Bin",
            subtitle = "Deleted files awaiting permanent removal",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.SAFE_CACHE,
            pathHint = "~/.local/share/Trash"
        ),

        // Caches & Temp - Rebuildable Dev Cache Tier
        BucketDef(
            id = "cache_npm",
            title = "npm Download Cache",
            subtitle = "Cached npm tarballs; re-fetched on next npm install",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.DEV_REBUILD_CACHE,
            pathHint = "~/.npm/"
        ),
        BucketDef(
            id = "cache_pnpm",
            title = "pnpm Content-Addressable Store & Cache",
            subtitle = "Cached pnpm packages; safely rebuildable",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.DEV_REBUILD_CACHE,
            pathHint = "~/.pnpm-store & ~/.local/share/pnpm"
        ),
        BucketDef(
            id = "cache_yarn",
            title = "Yarn Package Cache",
            subtitle = "Cached Yarn archives",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.DEV_REBUILD_CACHE,
            pathHint = "~/.yarn/cache & ~/.cache/yarn"
        ),
        BucketDef(
            id = "cache_pip",
            title = "Python pip / uv Wheel Cache",
            subtitle = "Downloaded Python wheels & HTTP cache",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.DEV_REBUILD_CACHE,
            pathHint = "~/.cache/pip & ~/.cache/uv"
        ),
        BucketDef(
            id = "cache_go",
            title = "Go Build & Module Cache",
            subtitle = "Downloaded Go modules (~/go/pkg/mod) & build cache",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.DEV_REBUILD_CACHE,
            pathHint = "~/go/pkg/mod & ~/.cache/go-build"
        ),
        BucketDef(
            id = "cache_cargo",
            title = "Rust Cargo Registry & Git Cache",
            subtitle = "Downloaded crates & git index cache",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.DEV_REBUILD_CACHE,
            pathHint = "~/.cargo/registry & ~/.cargo/git"
        ),
        BucketDef(
            id = "cache_gradle",
            title = "Gradle & Maven Dependency Caches",
            subtitle = "Downloaded JARs, Gradle wrapper dists & build cache",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.DEV_REBUILD_CACHE,
            pathHint = "~/.gradle/caches & ~/.m2/repository"
        ),
        BucketDef(
            id = "cache_apt_lists_termux",
            title = "Termux APT Index Lists",
            subtitle = "Package repository metadata; rebuilt on 'pkg update'",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.DEV_REBUILD_CACHE,
            pathHint = "usr/var/lib/apt/lists/"
        ),
        BucketDef(
            id = "cache_apt_lists_ubuntu",
            title = "Ubuntu APT Index Lists",
            subtitle = "Package repository metadata; rebuilt on 'apt update'",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.DEV_REBUILD_CACHE,
            pathHint = "ubuntu/var/lib/apt/lists/"
        ),
        BucketDef(
            id = "cache_xdg_home",
            title = "General Linux User Cache (~/.cache)",
            subtitle = "TypeScript, node-gyp, fontconfig, eslint & miscellaneous caches",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.DEV_REBUILD_CACHE,
            pathHint = "~/.cache/"
        ),

        // Confirm Tier inside Caches/Tools
        BucketDef(
            id = "cache_ml_models",
            title = "ML / HuggingFace / PyTorch Cache",
            subtitle = "Downloaded model weights — slow to re-download",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.CONFIRM_DATA,
            pathHint = "~/.cache/{huggingface,torch}"
        ),
        BucketDef(
            id = "cache_agy_recordings",
            title = "Antigravity Browser Recordings",
            subtitle = "Recorded screenshots/videos captured by browser subagents",
            category = StorageCategoryId.CACHES_TEMP,
            clearability = CacheClearability.CONFIRM_DATA,
            pathHint = ".gemini/antigravity/browser_recordings"
        ),
        BucketDef(
            id = "tool_playwright",
            title = "Playwright & Puppeteer Headless Browsers",
            subtitle = "Downloaded Chromium/Firefox/WebKit binaries",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.CONFIRM_DATA,
            pathHint = "~/.cache/{ms-playwright,puppeteer}"
        ),

        // AI Agents Category
        BucketDef(
            id = "ai_agy_cli",
            title = "Antigravity CLI & Bridge Binaries",
            subtitle = "Local agy / agy_ide_bridge agent executables",
            category = StorageCategoryId.AI_AGENTS,
            clearability = CacheClearability.NONE,
            pathHint = ".gemini/bin & usr/bin/agy_ide_bridge"
        ),
        BucketDef(
            id = "ai_agy_chats",
            title = "Antigravity Chat Trajectories",
            subtitle = "Saved conversation histories & protobuf state",
            category = StorageCategoryId.AI_AGENTS,
            clearability = CacheClearability.NONE,
            pathHint = ".gemini/antigravity/conversations/"
        ),
        BucketDef(
            id = "ai_agy_brain",
            title = "Antigravity Artifacts, Plans & Media",
            subtitle = "Generated artifacts, scratch scripts & attached screenshots",
            category = StorageCategoryId.AI_AGENTS,
            clearability = CacheClearability.NONE,
            pathHint = ".gemini/antigravity/brain/"
        ),
        BucketDef(
            id = "ai_agy_knowledge",
            title = "Antigravity Knowledge Base",
            subtitle = "Persistent learned knowledge snapshots",
            category = StorageCategoryId.AI_AGENTS,
            clearability = CacheClearability.NONE,
            pathHint = ".gemini/antigravity/knowledge/"
        ),
        BucketDef(
            id = "ai_agy_config",
            title = "Antigravity Config, Auth & MCP State",
            subtitle = "MCP server configs, skills, accounts & daemon metadata",
            category = StorageCategoryId.AI_AGENTS,
            clearability = CacheClearability.NONE,
            pathHint = ".gemini/ & .antigravity/"
        ),
        BucketDef(
            id = "ai_claude_bin",
            title = "Claude Code CLI Binaries & Versions",
            subtitle = "Installed Claude Code binary versions & npm package",
            category = StorageCategoryId.AI_AGENTS,
            clearability = CacheClearability.NONE,
            pathHint = "~/.local/share/claude & @anthropic-ai/claude-code"
        ),
        BucketDef(
            id = "ai_claude_sessions",
            title = "Claude Code Sessions, Memory & Config",
            subtitle = "Conversation JSONL transcripts, CLAUDE.md, skills & auth",
            category = StorageCategoryId.AI_AGENTS,
            clearability = CacheClearability.NONE,
            pathHint = "~/.claude/ & ~/.claude.json"
        ),
        BucketDef(
            id = "ai_copilot_chats",
            title = "GitHub Copilot Chats & Auth",
            subtitle = "Saved Copilot threads & authentication state",
            category = StorageCategoryId.AI_AGENTS,
            clearability = CacheClearability.NONE,
            pathHint = "files/copilot_chats & ~/.config/github-copilot"
        ),
        BucketDef(
            id = "ai_other_agents",
            title = "Other AI CLI Agents (.codex / .opencode)",
            subtitle = "Third-party CLI coding agent configs & data",
            category = StorageCategoryId.AI_AGENTS,
            clearability = CacheClearability.NONE,
            pathHint = "~/.codex & ~/.opencode"
        ),

        // Developer Toolchains
        BucketDef(
            id = "tool_nodejs",
            title = "Node.js Runtime & Global Packages",
            subtitle = "Node binary & global node_modules packages",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.NONE,
            pathHint = "usr/lib/node_modules/"
        ),
        BucketDef(
            id = "tool_node_managers",
            title = "Node / Bun Version Managers (.nvm / .bun)",
            subtitle = "Managed Node.js or Bun runtime installations",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.NONE,
            pathHint = "~/.nvm, ~/.fnm, ~/.bun"
        ),
        BucketDef(
            id = "tool_python",
            title = "Python Runtime & Environments",
            subtitle = "Python stdlib, site-packages, pipenv, venv & conda",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.NONE,
            pathHint = "usr/lib/python* & ~/.local/lib/python*"
        ),
        BucketDef(
            id = "tool_compilers",
            title = "C/C++ Compilers (LLVM / Clang / GCC)",
            subtitle = "Native compiler toolchains, headers & linker binaries",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.NONE,
            pathHint = "usr/lib/{llvm,clang,gcc}"
        ),
        BucketDef(
            id = "tool_golang",
            title = "Go Compiler & Tools (~/go & stdlib)",
            subtitle = "Go compiler binaries, GOPATH/bin & SDK libraries",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.NONE,
            pathHint = "go/ & usr/lib/go*"
        ),
        BucketDef(
            id = "tool_rust",
            title = "Rustup & Cargo Toolchains",
            subtitle = "Rust compiler toolchains & cargo binaries",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.NONE,
            pathHint = "~/.rustup & ~/.cargo/bin"
        ),
        BucketDef(
            id = "tool_java",
            title = "Java / OpenJDK Runtime",
            subtitle = "JVM runtimes & JDK libraries",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.NONE,
            pathHint = "usr/lib/jvm*"
        ),
        BucketDef(
            id = "tool_git",
            title = "Git Version Control Core",
            subtitle = "Git binaries & git-core helpers",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.NONE,
            pathHint = "usr/libexec/git-core"
        ),
        BucketDef(
            id = "tool_android_sdk",
            title = "Android SDK & ADB Keys",
            subtitle = "Android SDK platforms, build-tools & ADB keys",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.NONE,
            pathHint = "~/.android & ~/android-sdk"
        ),
        BucketDef(
            id = "tool_local_user",
            title = "Local User Binaries & Data (~/.local)",
            subtitle = "Custom user binaries, pipx apps & XDG share data",
            category = StorageCategoryId.DEV_TOOLS,
            clearability = CacheClearability.NONE,
            pathHint = "~/.local/"
        ),

        // Linux Environment Category
        BucketDef(
            id = "linux_ubuntu_base",
            title = "Ubuntu PRoot Rootfs Base OS",
            subtitle = "Core Ubuntu packages, system libraries & rootfs structure",
            category = StorageCategoryId.LINUX_ENV,
            clearability = CacheClearability.NONE,
            pathHint = "files/ubuntu/"
        ),
        BucketDef(
            id = "linux_termux_base",
            title = "Termux Base Packages & Libraries",
            subtitle = "Core Termux utilities, shared libraries & manpages",
            category = StorageCategoryId.LINUX_ENV,
            clearability = CacheClearability.NONE,
            pathHint = "files/usr/"
        ),
        BucketDef(
            id = "linux_glibc",
            title = "Bundled Glibc Compatibility Runtime",
            subtitle = "Required loader for official Claude Code & Linux binaries",
            category = StorageCategoryId.LINUX_ENV,
            clearability = CacheClearability.NONE,
            pathHint = "usr/glibc/"
        ),
        BucketDef(
            id = "linux_proot_runtime",
            title = "Gravent PRoot & Native Bridge Runtime",
            subtitle = "PRoot loader, libtalloc & sysdata emulation files",
            category = StorageCategoryId.LINUX_ENV,
            clearability = CacheClearability.NONE,
            pathHint = "files/{bin,lib,sysdata}/"
        ),
        BucketDef(
            id = "linux_pkg_db",
            title = "APT / DPKG Package Registry",
            subtitle = "Installed package metadata & dpkg info database",
            category = StorageCategoryId.LINUX_ENV,
            clearability = CacheClearability.NONE,
            pathHint = "var/lib/dpkg/"
        ),
        BucketDef(
            id = "linux_dotfiles",
            title = "Shell Configs, .ssh & Dotfiles",
            subtitle = "SSH keys, shell profiles, gitconfig & terminal prefs",
            category = StorageCategoryId.LINUX_ENV,
            clearability = CacheClearability.NONE,
            pathHint = "~/.ssh, ~/.config, ~/.bashrc"
        ),

        // Workspaces Category
        BucketDef(
            id = "workspaces_projects",
            title = "Coding Workspaces (~/projects)",
            subtitle = "Project source code, Git repos & local dependencies",
            category = StorageCategoryId.WORKSPACES,
            clearability = CacheClearability.NONE,
            pathHint = "files/home/projects/"
        ),
        BucketDef(
            id = "workspaces_home_files",
            title = "Home User Files & Repositories",
            subtitle = "User folders, scripts & cloned repos in home directory",
            category = StorageCategoryId.WORKSPACES,
            clearability = CacheClearability.NONE,
            pathHint = "files/home/ & ubuntu/root/"
        ),

        // Browser Data Category
        BucketDef(
            id = "browser_site_data",
            title = "WebView Cookies, Logins & Site Storage",
            subtitle = "Website sessions, localStorage, IndexedDB & Service Workers",
            category = StorageCategoryId.BROWSER_DATA,
            clearability = CacheClearability.CONFIRM_DATA,
            pathHint = "app_webview/",
            isWebViewSiteData = true
        ),
        BucketDef(
            id = "browser_bundled_ext",
            title = "Browser Extension Host & Built-in Plugins",
            subtitle = "Unpacked browser runtime & bundled Gravent plugins",
            category = StorageCategoryId.BROWSER_DATA,
            clearability = CacheClearability.NONE,
            pathHint = "files/browser-extensions/ & browser-runtime/"
        ),
        BucketDef(
            id = "browser_user_ext",
            title = "User Installed Extensions & Plugins",
            subtitle = "Custom WebView MCP plugins & extensions",
            category = StorageCategoryId.BROWSER_DATA,
            clearability = CacheClearability.NONE,
            pathHint = "files/extensions/ & ~/extensions/"
        ),

        // App Database & Preferences Category
        BucketDef(
            id = "app_databases",
            title = "App SQLite Databases",
            subtitle = "Room/SQLite stores, journals & WorkManager DB",
            category = StorageCategoryId.APP_DATABASE,
            clearability = CacheClearability.NONE,
            pathHint = "databases/"
        ),
        BucketDef(
            id = "app_datastore",
            title = "DataStore Preferences",
            subtitle = "Server URLs, auth metadata & theme preferences",
            category = StorageCategoryId.APP_DATABASE,
            clearability = CacheClearability.NONE,
            pathHint = "files/datastore/"
        ),
        BucketDef(
            id = "app_shared_prefs",
            title = "SharedPreferences XML",
            subtitle = "Android XML settings, agent selection & terminal prefs",
            category = StorageCategoryId.APP_DATABASE,
            clearability = CacheClearability.NONE,
            pathHint = "shared_prefs/"
        ),
        BucketDef(
            id = "app_notes",
            title = "Gravent Notes Data",
            subtitle = "Local structured notes database (notes.json)",
            category = StorageCategoryId.APP_DATABASE,
            clearability = CacheClearability.NONE,
            pathHint = "files/notes/"
        ),
        BucketDef(
            id = "app_no_backup",
            title = "System No-Backup State",
            subtitle = "WorkManager state & system runtime markers",
            category = StorageCategoryId.APP_DATABASE,
            clearability = CacheClearability.NONE,
            pathHint = "no_backup/"
        ),
        BucketDef(
            id = "de_system_data",
            title = "Device-Protected App State",
            subtitle = "Direct-boot configuration & metadata",
            category = StorageCategoryId.APP_DATABASE,
            clearability = CacheClearability.NONE,
            pathHint = "user_de/"
        ),

        // Other / Misc
        BucketDef(
            id = "ext_app_files",
            title = "External App Files",
            subtitle = "App-specific files on shared storage (Android/data)",
            category = StorageCategoryId.OTHER_DATA,
            clearability = CacheClearability.NONE,
            pathHint = "Android/data/.../files/"
        ),
        BucketDef(
            id = "obb_data",
            title = "OBB & Media App Folders",
            subtitle = "Expansion or media folders managed by Android",
            category = StorageCategoryId.OTHER_DATA,
            clearability = CacheClearability.NONE,
            pathHint = "Android/obb/ & Android/media/"
        ),
        BucketDef(
            id = "other_files",
            title = "Other files/ Contents",
            subtitle = "Miscellaneous files inside app files directory",
            category = StorageCategoryId.OTHER_DATA,
            clearability = CacheClearability.NONE,
            pathHint = "files/"
        ),
        BucketDef(
            id = "other_appdata",
            title = "Other App Metadata",
            subtitle = "Miscellaneous internal app data & profiles",
            category = StorageCategoryId.OTHER_DATA,
            clearability = CacheClearability.NONE,
            pathHint = "dataDir/"
        )
    ).associateBy { it.id }

    suspend fun analyzeStorage(context: Context): StorageReport = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val startMs = System.currentTimeMillis()
        val osQuota = queryOsQuota(appContext)
        val (freeBytes, deviceTotalBytes) = queryDeviceDiskSpace(appContext)

        val scanRoots = collectDisjointScanRoots(appContext)
        val seenInodes = HashSet<Pair<Long, Long>>(1024)

        val bucketBytes = HashMap<String, Long>(64)
        val bucketFiles = HashMap<String, Long>(64)
        val bucketTargetPaths = HashMap<String, LinkedHashSet<String>>(64)

        val workspaceAccum = LinkedHashMap<String, WorkspaceAccum>()
        val artifactAccum = LinkedHashMap<String, ArtifactAccum>()
        val globalPkgAccum = LinkedHashMap<String, GlobalPkgAccum>()
        val topFilesHeap = PriorityQueue<LargeFileEntry>(21, compareBy { it.bytes })

        var scannedFiles = 0L
        var scannedDirs = 0L
        var hardlinksSkipped = 0L
        var hardlinkSavedBytes = 0L
        var symlinksSkipped = 0L
        var logicalBytesTotal = 0L
        var physicalBytesTotal = 0L

        fun recordToBucket(bucketId: String, bytes: Long, isFile: Boolean) {
            bucketBytes[bucketId] = (bucketBytes[bucketId] ?: 0L) + bytes
            if (isFile) {
                bucketFiles[bucketId] = (bucketFiles[bucketId] ?: 0L) + 1L
            }
        }

        fun recordTarget(bucketId: String, file: File) {
            val def = BUCKET_DEFS[bucketId] ?: return
            if (def.clearability != CacheClearability.NONE) {
                bucketTargetPaths.getOrPut(bucketId) { LinkedHashSet() }.add(file.absolutePath)
            }
        }

        val dataRootPath = resolveInternalDataDir(appContext).canonicalFile.absolutePath

        for (root in scanRoots) {
            currentCoroutineContext().ensureActive()
            val rootStat = lstatOrNull(root.dir) ?: continue
            val rootDev = rootStat.st_dev
            val stack = ArrayDeque<WalkNode>(128)
            val initBucket = initialBucketForRoot(root.kind)
            stack.addLast(
                WalkNode(
                    file = root.dir,
                    relPath = "",
                    bucketId = initBucket,
                    isTargetRoot = false,
                    projectKey = null,
                    activeArtifactKey = null,
                    globalPkgKey = null
                )
            )
            // Account root directory inode size
            val rootDirBytes = rootStat.st_blocks * 512L
            physicalBytesTotal += rootDirBytes
            scannedDirs++
            recordToBucket(initBucket, rootDirBytes, isFile = false)

            var ops = 0
            while (stack.isNotEmpty()) {
                if ((++ops and 255) == 0) {
                    currentCoroutineContext().ensureActive()
                }
                val node = stack.removeLast()
                val children = node.file.listFiles() ?: continue
                for (child in children) {
                    val st = lstatOrNull(child) ?: continue
                    val mode = st.st_mode
                    val childRel = if (node.relPath.isEmpty()) child.name else "${node.relPath}/${child.name}"

                    when {
                        OsConstants.S_ISLNK(mode) -> {
                            symlinksSkipped++
                            val linkBytes = st.st_blocks * 512L
                            physicalBytesTotal += linkBytes
                            recordToBucket(node.bucketId, linkBytes, isFile = true)
                            if (node.projectKey != null) {
                                workspaceAccum[node.projectKey]?.let { it.totalBytes += linkBytes }
                            }
                        }

                        OsConstants.S_ISDIR(mode) -> {
                            if (st.st_dev != rootDev) {
                                // Prevent crossing kernel bind mounts (e.g. /proc, /sys, /sdcard)
                                continue
                            }
                            scannedDirs++
                            val dirBytes = st.st_blocks * 512L
                            physicalBytesTotal += dirBytes

                            val decision = resolveDirectoryRule(
                                rootKind = root.kind,
                                relPath = childRel,
                                name = child.name,
                                parentBucket = node.bucketId,
                                parentIsTarget = node.isTargetRoot,
                                parentProjectKey = node.projectKey,
                                parentArtifactKey = node.activeArtifactKey,
                                parentGlobalPkgKey = node.globalPkgKey,
                                absPath = child.absolutePath
                            )

                            recordToBucket(decision.bucketId, dirBytes, isFile = false)
                            if (decision.recordThisDirAsTarget) {
                                recordTarget(decision.bucketId, child)
                            }
                            if (decision.workspaceItem != null) {
                                workspaceAccum.getOrPut(decision.workspaceItem.path) { decision.workspaceItem }
                            }
                            if (decision.projectKey != null) {
                                workspaceAccum[decision.projectKey]?.let { it.totalBytes += dirBytes }
                            }
                            if (decision.newArtifact != null) {
                                artifactAccum.getOrPut(decision.newArtifact.path) { decision.newArtifact }
                            }
                            if (decision.artifactKey != null) {
                                artifactAccum[decision.artifactKey]?.let { it.bytes += dirBytes }
                                if (decision.projectKey != null) {
                                    workspaceAccum[decision.projectKey]?.let { it.artifactBytes += dirBytes }
                                }
                            }
                            if (decision.newGlobalPkg != null) {
                                globalPkgAccum.getOrPut(decision.newGlobalPkg.path) { decision.newGlobalPkg }
                            }
                            if (decision.globalPkgKey != null) {
                                globalPkgAccum[decision.globalPkgKey]?.let { it.bytes += dirBytes }
                            }

                            stack.addLast(
                                WalkNode(
                                    file = child,
                                    relPath = childRel,
                                    bucketId = decision.bucketId,
                                    isTargetRoot = decision.propagateTargetMarker,
                                    projectKey = decision.projectKey,
                                    activeArtifactKey = decision.artifactKey,
                                    globalPkgKey = decision.globalPkgKey
                                )
                            )
                        }

                        else -> {
                            scannedFiles++
                            val rawBlocksBytes = st.st_blocks * 512L
                            val fileBucket = resolveFileRule(
                                rootKind = root.kind,
                                relPath = childRel,
                                fileName = child.name,
                                parentBucket = node.bucketId
                            )
                            val countThisInode = if (st.st_nlink > 1) {
                                seenInodes.add(st.st_dev to st.st_ino)
                            } else {
                                true
                            }
                            val effectiveBytes = if (countThisInode) {
                                logicalBytesTotal += st.st_size
                                physicalBytesTotal += rawBlocksBytes
                                rawBlocksBytes
                            } else {
                                hardlinksSkipped++
                                hardlinkSavedBytes += rawBlocksBytes
                                0L
                            }

                            recordToBucket(fileBucket, effectiveBytes, isFile = true)
                            val isDirectChildOfAndroidCache =
                                (root.kind == ScanRootKind.INTERNAL_DATA && node.relPath == "cache") ||
                                (root.kind == ScanRootKind.EXTERNAL && node.relPath == "cache")
                            if (fileBucket != node.bucketId || isDirectChildOfAndroidCache) {
                                recordTarget(fileBucket, child)
                            }
                            if (node.projectKey != null && countThisInode) {
                                workspaceAccum[node.projectKey]?.let {
                                    it.totalBytes += effectiveBytes
                                    it.fileCount += 1L
                                }
                            }
                            if (node.activeArtifactKey != null && countThisInode) {
                                artifactAccum[node.activeArtifactKey]?.let {
                                    it.bytes += effectiveBytes
                                    it.fileCount += 1L
                                }
                                if (node.projectKey != null) {
                                    workspaceAccum[node.projectKey]?.let { it.artifactBytes += effectiveBytes }
                                }
                            }
                            if (node.globalPkgKey != null && countThisInode) {
                                globalPkgAccum[node.globalPkgKey]?.let {
                                    it.bytes += effectiveBytes
                                    it.fileCount += 1L
                                }
                            }

                            if (effectiveBytes >= 256 * 1024L) { // Only track files >= 256 KB for Top 20 heap
                                val cat = BUCKET_DEFS[fileBucket]?.category ?: StorageCategoryId.OTHER_DATA
                                val clearable = BUCKET_DEFS[fileBucket]?.clearability == CacheClearability.SAFE_CACHE ||
                                    BUCKET_DEFS[fileBucket]?.clearability == CacheClearability.DEV_REBUILD_CACHE
                                val displayRel = when (root.kind) {
                                    ScanRootKind.INTERNAL_DATA -> childRel
                                    ScanRootKind.DEVICE_PROTECTED -> "user_de/$childRel"
                                    ScanRootKind.EXTERNAL -> "Android/data/$childRel"
                                    ScanRootKind.OBB -> "Android/obb/$childRel"
                                }
                                val entry = LargeFileEntry(
                                    name = child.name,
                                    relativePath = displayRel.ifEmpty { child.absolutePath.removePrefix(dataRootPath).trimStart('/') },
                                    absolutePath = child.absolutePath,
                                    category = cat,
                                    bytes = effectiveBytes,
                                    logicalBytes = st.st_size,
                                    canDeleteSafely = clearable
                                )
                                if (topFilesHeap.size < 20) {
                                    topFilesHeap.add(entry)
                                } else if ((topFilesHeap.peek()?.bytes ?: 0L) < entry.bytes) {
                                    topFilesHeap.poll()
                                    topFilesHeap.add(entry)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Resolve OS App Binary bytes (APK + ART/DEX + Native libs)
        val appBinaryBytes = if (osQuota.supported && osQuota.appBytes > 0L) {
            osQuota.appBytes
        } else {
            estimateApkFallbackBytes(appContext)
        }
        if (appBinaryBytes > 0L) {
            bucketBytes["app_binary"] = appBinaryBytes
            bucketFiles["app_binary"] = 1L
        }

        // Build StorageBucketItem list preserving ALL detected buckets > 0 bytes (plus primary caches even if 0 for visibility in Extra Caches)
        val bucketItems = BUCKET_DEFS.values.mapNotNull { def ->
            val b = bucketBytes[def.id] ?: 0L
            val f = bucketFiles[def.id] ?: 0L
            if (b == 0L && f == 0L && def.id !in PRIMARY_ZERO_VISIBLE_CACHES) return@mapNotNull null
            StorageBucketItem(
                id = def.id,
                title = def.title,
                subtitle = def.subtitle,
                category = def.category,
                clearability = def.clearability,
                pathHint = def.pathHint,
                bytes = b,
                fileCount = f,
                targetPaths = bucketTargetPaths[def.id]?.toList() ?: emptyList(),
                isWebViewHttpCache = def.isWebViewHttpCache,
                isWebViewSiteData = def.isWebViewSiteData,
                isSharedTemp = def.isSharedTemp,
                isAptArchiveDir = def.isAptArchiveDir
            )
        }

        // Group into categories using the exact invariant:
        // category.bytes == sum(items.bytes in that category)
        val categories = StorageCategoryId.entries.mapNotNull { catId ->
            val itemsInCat = bucketItems
                .filter { it.category == catId && (it.bytes > 0L || it.fileCount > 0L) }
                .sortedByDescending { it.bytes }
            val sumBytes = itemsInCat.sumOf { it.bytes }
            val sumFiles = itemsInCat.sumOf { it.fileCount }
            if (sumBytes == 0L && sumFiles == 0L && catId != StorageCategoryId.CACHES_TEMP) return@mapNotNull null
            StorageCategorySummary(
                category = catId,
                bytes = sumBytes,
                fileCount = sumFiles,
                items = itemsInCat
            )
        }.sortedByDescending { it.bytes }

        // Extra / All Caches list (sorted non-zero largest first, then zero-byte clean caches)
        val extraCaches = bucketItems
            .filter {
                it.clearability == CacheClearability.SAFE_CACHE ||
                    it.clearability == CacheClearability.DEV_REBUILD_CACHE ||
                    it.id == "tool_playwright" ||
                    it.id == "cache_agy_recordings" ||
                    it.id == "cache_ml_models"
            }
            .sortedWith(compareByDescending<StorageBucketItem> { it.bytes }.thenBy { it.title })

        val totalLiveDataBytes = physicalBytesTotal
        val totalAppBytes = appBinaryBytes + totalLiveDataBytes
        val safeCacheBytes = bucketItems.filter { it.clearability == CacheClearability.SAFE_CACHE }.sumOf { it.bytes }
        val devCacheBytes = bucketItems.filter { it.clearability == CacheClearability.DEV_REBUILD_CACHE }.sumOf { it.bytes }
        val allCachesBytes = bucketItems.filter { it.category == StorageCategoryId.CACHES_TEMP }.sumOf { it.bytes }
        val netUserDataBytes = (totalLiveDataBytes - allCachesBytes).coerceAtLeast(0L)

        // Workspaces, Artifacts & Global Packages
        val workspaces = workspaceAccum.values
            .map {
                WorkspaceStorageItem(
                    name = it.name,
                    path = it.path,
                    locationLabel = it.locationLabel,
                    totalBytes = it.totalBytes,
                    artifactBytes = it.artifactBytes,
                    fileCount = it.fileCount
                )
            }
            .filter { it.totalBytes > 0L || it.fileCount > 0L }
            .sortedByDescending { it.totalBytes }

        val artifacts = artifactAccum.values
            .map {
                WorkspaceArtifactItem(
                    kind = it.kind,
                    projectName = it.projectName,
                    path = it.path,
                    bytes = it.bytes,
                    fileCount = it.fileCount
                )
            }
            .filter { it.bytes > 0L }
            .sortedByDescending { it.bytes }

        val globalPackages = globalPkgAccum.values
            .map {
                InstalledToolPackageItem(
                    name = it.name,
                    runtime = it.runtime,
                    path = it.path,
                    bytes = it.bytes,
                    fileCount = it.fileCount
                )
            }
            .filter { it.bytes > 0L }
            .sortedByDescending { it.bytes }
            .take(50)

        val dpkgPackages = readInstalledDpkgPackages(appContext).take(60)
        val largestFiles = topFilesHeap.toList().sortedByDescending { it.bytes }
        val externalBackups = scanExternalBackups(appContext)
        val scanDurationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)

        val report = StorageReport(
            timestampMs = System.currentTimeMillis(),
            totalBytes = totalAppBytes,
            appBinaryBytes = appBinaryBytes,
            netUserDataBytes = netUserDataBytes,
            reclaimableSafeCacheBytes = safeCacheBytes,
            reclaimableDevCacheBytes = devCacheBytes,
            totalAllCachesBytes = allCachesBytes,
            categories = categories,
            extraCaches = extraCaches,
            workspaces = workspaces,
            workspaceArtifacts = artifacts,
            globalPackages = globalPackages,
            dpkgPackages = dpkgPackages,
            largestFiles = largestFiles,
            externalBackups = externalBackups,
            osQuota = osQuota,
            accuracy = ScanAccuracyMetrics(
                scanDurationMs = scanDurationMs,
                scannedFiles = scannedFiles,
                scannedDirs = scannedDirs,
                hardlinksSkipped = hardlinksSkipped,
                hardlinkSavedBytes = hardlinkSavedBytes,
                symlinksSkipped = symlinksSkipped,
                logicalBytesTotal = logicalBytesTotal,
                physicalBytesTotal = physicalBytesTotal,
                deviceFreeBytes = freeBytes,
                deviceTotalBytes = deviceTotalBytes
            )
        )
        _latestReport.value = report
        report
    }

    private val PRIMARY_ZERO_VISIBLE_CACHES = setOf(
        "cache_internal_app",
        "cache_ext_app",
        "cache_code",
        "cache_webview_http",
        "cache_apt_termux",
        "cache_apt_ubuntu",
        "cache_npm",
        "cache_pip"
    )

    // ==================== Cleanup Operations ====================

    suspend fun quickOptimize(context: Context): StorageCleanupResult {
        val current = _latestReport.value ?: analyzeStorage(context)
        val safeBuckets = current.extraCaches.filter {
            it.clearability == CacheClearability.SAFE_CACHE && (it.bytes > 0L || it.fileCount > 0L || it.isWebViewHttpCache)
        }
        return clearBuckets(context, safeBuckets, label = "Quick Optimize")
    }

    suspend fun clearDevCaches(context: Context): StorageCleanupResult {
        val current = _latestReport.value ?: analyzeStorage(context)
        val devBuckets = current.extraCaches.filter {
            it.clearability == CacheClearability.DEV_REBUILD_CACHE && (it.bytes > 0L || it.fileCount > 0L)
        }
        return clearBuckets(context, devBuckets, label = "Developer & Package Manager Caches")
    }

    suspend fun clearBucket(context: Context, bucket: StorageBucketItem): StorageCleanupResult {
        return clearBuckets(context, listOf(bucket), label = bucket.title)
    }

    suspend fun deleteWorkspaceArtifact(context: Context, artifact: WorkspaceArtifactItem): StorageCleanupResult =
        withContext(Dispatchers.IO) {
            val target = File(artifact.path)
            if (!isPathInsideAppSandbox(context, target)) {
                return@withContext StorageCleanupResult(0L, 0L, 0, 1, "Refused: path is outside app sandbox")
            }
            val stats = CleanCounters()
            deleteSafely(target, keepRootDir = false, preserveSharedRuntime = false, skipInstallerFiles = false, counters = stats)
            analyzeStorage(context)
            StorageCleanupResult(
                freedBytes = stats.freedBytes,
                removedEntries = stats.removedCount,
                skippedActiveEntries = stats.skippedActive,
                errorCount = stats.errors,
                summary = "Removed ${artifact.kind} in ${artifact.projectName}: freed ${formatSize(context, stats.freedBytes)}"
            )
        }

    suspend fun deleteLargeFile(context: Context, fileEntry: LargeFileEntry): StorageCleanupResult =
        withContext(Dispatchers.IO) {
            if (!fileEntry.canDeleteSafely) {
                return@withContext StorageCleanupResult(0L, 0L, 0, 1, "Protected file cannot be deleted here")
            }
            val target = File(fileEntry.absolutePath)
            if (!isPathInsideAppSandbox(context, target)) {
                return@withContext StorageCleanupResult(0L, 0L, 0, 1, "Refused: path outside sandbox")
            }
            val stats = CleanCounters()
            deleteSafely(target, keepRootDir = false, preserveSharedRuntime = false, skipInstallerFiles = false, counters = stats)
            analyzeStorage(context)
            StorageCleanupResult(
                freedBytes = stats.freedBytes,
                removedEntries = stats.removedCount,
                skippedActiveEntries = stats.skippedActive,
                errorCount = stats.errors,
                summary = "Deleted ${fileEntry.name}: freed ${formatSize(context, stats.freedBytes)}"
            )
        }

    suspend fun deleteExternalBackupFile(context: Context, item: ExternalBackupItem): StorageCleanupResult =
        withContext(Dispatchers.IO) {
            val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).canonicalFile
            val antigemDir = File(downloads, "Antigem").canonicalFile
            val bridgeFile = File(downloads, "agy_ide_bridge").canonicalFile
            val target = try { File(item.path).canonicalFile } catch (_: Exception) {
                return@withContext StorageCleanupResult(0L, 0L, 0, 1, "Invalid backup path")
            }
            val allowed = target.path.startsWith(antigemDir.path + File.separator) || target == bridgeFile
            if (!allowed) {
                return@withContext StorageCleanupResult(0L, 0L, 0, 1, "Refused: path outside Gravent external downloads")
            }
            val st = lstatOrNull(target)
            val bytes = st?.let { it.st_blocks * 512L } ?: target.length()
            val deleted = if (target.exists()) target.delete() else false
            analyzeStorage(context)
            StorageCleanupResult(
                freedBytes = if (deleted) bytes else 0L,
                removedEntries = if (deleted) 1L else 0L,
                skippedActiveEntries = 0,
                errorCount = if (deleted) 0 else 1,
                summary = if (deleted) "Deleted ${item.name} (${formatSize(context, bytes)})" else "Could not delete ${item.name}"
            )
        }

    private suspend fun clearBuckets(
        context: Context,
        buckets: List<StorageBucketItem>,
        label: String
    ): StorageCleanupResult = withContext(Dispatchers.IO) {
        val appContext = context.applicationContext
        val installerState = LocalEnvironmentManager.installerState.value
        val installerActive = installerState is LocalInstallerState.Downloading ||
            installerState is LocalInstallerState.Extracting ||
            installerState is LocalInstallerState.Configuring ||
            installerState is LocalInstallerState.Verifying

        val counters = CleanCounters()
        var ranWebViewHttpClear = false
        var ranWebViewSiteClear = false

        for (bucket in buckets) {
            currentCoroutineContext().ensureActive()
            if (bucket.clearability == CacheClearability.NONE) continue
            if (bucket.id == "cache_installer" && installerActive) {
                counters.skippedActive++
                continue
            }

            if (bucket.isWebViewHttpCache && !ranWebViewHttpClear) {
                ranWebViewHttpClear = true
                runCatching { BrowserSessionManager.instance.clearHttpCacheOnly(appContext) }
            }
            if (bucket.isWebViewSiteData) {
                if (!ranWebViewSiteClear) {
                    ranWebViewSiteClear = true
                    runCatching {
                        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
                        BrowserSessionManager.instance.clearAllBrowsingData(appContext) { done.complete(Unit) }
                        kotlinx.coroutines.withTimeoutOrNull(4000L) { done.await() }
                    }
                }
                // Let Chromium's WebView APIs wipe cookies/WebStorage cleanly without unlinking live SQLite WAL files.
                counters.freedBytes += bucket.bytes
                counters.removedCount += bucket.fileCount
                continue
            }

            for (path in bucket.targetPaths) {
                val target = File(path)
                if (!isPathInsideAppSandbox(appContext, target)) continue
                val keepDirItself = bucket.isSharedTemp || bucket.isAptArchiveDir ||
                    bucket.id in KEEP_ROOT_DIR_BUCKETS
                deleteSafely(
                    target = target,
                    keepRootDir = keepDirItself,
                    preserveSharedRuntime = bucket.isSharedTemp,
                    skipInstallerFiles = installerActive,
                    counters = counters
                )
            }

            // Re-create apt archives/partial so apt/pkg never breaks after cleanup
            if (bucket.isAptArchiveDir) {
                for (path in bucket.targetPaths) {
                    val f = File(path)
                    if (f.name == "apt" && f.isDirectory) {
                        runCatching { File(f, "archives/partial").mkdirs() }
                    }
                }
            }
        }

        // Ensure essential Android cache roots exist
        runCatching { appContext.cacheDir.mkdirs() }
        runCatching { appContext.codeCacheDir.mkdirs() }
        runCatching { File(appContext.filesDir, "tmp").mkdirs() }
        runCatching { File(appContext.filesDir, "shm").mkdirs() }

        analyzeStorage(appContext)
        StorageCleanupResult(
            freedBytes = counters.freedBytes,
            removedEntries = counters.removedCount,
            skippedActiveEntries = counters.skippedActive,
            errorCount = counters.errors,
            summary = "$label: freed ${formatSize(appContext, counters.freedBytes)}"
        )
    }

    private val KEEP_ROOT_DIR_BUCKETS = setOf(
        "cache_internal_app",
        "cache_ext_app",
        "cache_code",
        "cache_de",
        "cache_textures",
        "cache_apt_lists_termux",
        "cache_apt_lists_ubuntu",
        "cache_linux_logs"
    )

    private data class CleanCounters(
        var freedBytes: Long = 0L,
        var removedCount: Long = 0L,
        var skippedActive: Int = 0,
        var errors: Int = 0
    )

    private fun deleteSafely(
        target: File,
        keepRootDir: Boolean,
        preserveSharedRuntime: Boolean,
        skipInstallerFiles: Boolean,
        counters: CleanCounters,
        depth: Int = 0
    ): Boolean {
        val st = lstatOrNull(target) ?: return true
        val mode = st.st_mode

        if (OsConstants.S_ISLNK(mode)) {
            if (keepRootDir && depth == 0) return true
            val b = st.st_blocks * 512L
            return if (target.delete()) {
                counters.freedBytes += b
                counters.removedCount++
                true
            } else {
                counters.errors++
                false
            }
        }

        val name = target.name
        if (preserveSharedRuntime) {
            if (name.endsWith(".sock") ||
                name.startsWith("tmux-") ||
                name.startsWith(".s.") ||
                name.startsWith("dbus-") ||
                name.endsWith(".pid") ||
                name.endsWith(".lock")
            ) {
                counters.skippedActive++
                return false
            }
        }
        if (skipInstallerFiles && (name.startsWith("bootstrap-") || name.startsWith("inner_bootstrap") || name.startsWith("deb_"))) {
            counters.skippedActive++
            return false
        }

        if (OsConstants.S_ISDIR(mode)) {
            // Ensure directory is writable (fixes read-only Go mod cache 0555 dirs)
            if (!target.canWrite()) {
                runCatching { Os.chmod(target.absolutePath, 493 /* 0755 */) }
                target.setWritable(true, false)
            }
            var allChildrenDeleted = true
            val children = target.listFiles()
            if (children != null) {
                for (child in children) {
                    if (!deleteSafely(child, keepRootDir = false, preserveSharedRuntime = preserveSharedRuntime, skipInstallerFiles = skipInstallerFiles, counters = counters, depth = depth + 1)) {
                        allChildrenDeleted = false
                    }
                }
            }
            if (!keepRootDir && allChildrenDeleted) {
                val b = st.st_blocks * 512L
                if (target.delete()) {
                    counters.freedBytes += b
                    counters.removedCount++
                    return true
                }
                // Try parent chmod if needed
                target.parentFile?.let { runCatching { Os.chmod(it.absolutePath, 493) } }
                if (target.delete()) {
                    counters.freedBytes += b
                    counters.removedCount++
                    return true
                }
                counters.errors++
                return false
            }
            return allChildrenDeleted
        }

        // Regular file / FIFO / etc.
        if (preserveSharedRuntime) {
            val isSocket = (mode and OsConstants.S_IFMT) == OsConstants.S_IFSOCK
            if (OsConstants.S_ISFIFO(mode) || isSocket) {
                counters.skippedActive++
                return false
            }
        }
        val b = st.st_blocks * 512L
        if (!target.canWrite()) {
            runCatching { target.setWritable(true, false) }
        }
        if (target.delete()) {
            counters.freedBytes += b
            counters.removedCount++
            return true
        }
        target.parentFile?.let {
            runCatching { Os.chmod(it.absolutePath, 493) }
            runCatching { it.setWritable(true, false) }
        }
        return if (target.delete()) {
            counters.freedBytes += b
            counters.removedCount++
            true
        } else {
            counters.errors++
            false
        }
    }

    private fun isPathInsideAppSandbox(context: Context, target: File): Boolean {
        return try {
            val targetPath = target.canonicalPath
            val allowedRoots = listOfNotNull(
                resolveInternalDataDir(context).canonicalPath,
                safeDeviceProtectedDir(context)?.canonicalPath,
                context.getExternalFilesDir(null)?.parentFile?.canonicalPath,
                context.externalCacheDir?.canonicalPath,
                context.obbDir?.canonicalPath
            ).distinct()
            allowedRoots.any { root ->
                targetPath == root || targetPath.startsWith("$root${File.separator}")
            }
        } catch (_: Exception) {
            false
        }
    }

    // ==================== Internal Path & Rule Engine ====================

    private enum class ScanRootKind {
        INTERNAL_DATA,
        DEVICE_PROTECTED,
        EXTERNAL,
        OBB
    }

    private data class ScanRoot(val kind: ScanRootKind, val dir: File)

    private data class WalkNode(
        val file: File,
        val relPath: String,
        val bucketId: String,
        val isTargetRoot: Boolean,
        val projectKey: String?,
        val activeArtifactKey: String?,
        val globalPkgKey: String?
    )

    private data class WorkspaceAccum(
        val name: String,
        val path: String,
        val locationLabel: String,
        var totalBytes: Long = 0L,
        var artifactBytes: Long = 0L,
        var fileCount: Long = 0L
    )

    private data class ArtifactAccum(
        val kind: String,
        val projectName: String,
        val path: String,
        var bytes: Long = 0L,
        var fileCount: Long = 0L
    )

    private data class GlobalPkgAccum(
        val name: String,
        val runtime: String,
        val path: String,
        var bytes: Long = 0L,
        var fileCount: Long = 0L
    )

    private data class DirRuleDecision(
        val bucketId: String,
        val recordThisDirAsTarget: Boolean = false,
        val propagateTargetMarker: Boolean = false,
        val projectKey: String? = null,
        val workspaceItem: WorkspaceAccum? = null,
        val artifactKey: String? = null,
        val newArtifact: ArtifactAccum? = null,
        val globalPkgKey: String? = null,
        val newGlobalPkg: GlobalPkgAccum? = null
    )

    private fun resolveInternalDataDir(context: Context): File {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.dataDir
        } else {
            context.filesDir.parentFile ?: context.filesDir
        }
    }

    private fun safeDeviceProtectedDir(context: Context): File? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            runCatching { context.createDeviceProtectedStorageContext().dataDir }.getOrNull()
        } else {
            null
        }
    }

    private fun collectDisjointScanRoots(context: Context): List<ScanRoot> {
        val seenCanonical = HashSet<String>()
        val roots = mutableListOf<ScanRoot>()

        fun addIfNew(kind: ScanRootKind, file: File?) {
            if (file == null || !file.exists()) return
            val canonical = runCatching { file.canonicalFile }.getOrNull() ?: return
            val path = canonical.absolutePath
            if (seenCanonical.none { existing -> path == existing || path.startsWith("$existing${File.separator}") }) {
                seenCanonical.add(path)
                roots.add(ScanRoot(kind, canonical))
            }
        }

        addIfNew(ScanRootKind.INTERNAL_DATA, resolveInternalDataDir(context))
        addIfNew(ScanRootKind.DEVICE_PROTECTED, safeDeviceProtectedDir(context))
        addIfNew(ScanRootKind.EXTERNAL, context.getExternalFilesDir(null)?.parentFile)
        addIfNew(ScanRootKind.EXTERNAL, context.externalCacheDir)
        addIfNew(ScanRootKind.OBB, context.obbDir)
        return roots
    }

    private fun initialBucketForRoot(kind: ScanRootKind): String = when (kind) {
        ScanRootKind.INTERNAL_DATA -> "other_appdata"
        ScanRootKind.DEVICE_PROTECTED -> "de_system_data"
        ScanRootKind.EXTERNAL -> "ext_app_files"
        ScanRootKind.OBB -> "obb_data"
    }

    private fun resolveDirectoryRule(
        rootKind: ScanRootKind,
        relPath: String,
        name: String,
        parentBucket: String,
        parentIsTarget: Boolean,
        parentProjectKey: String?,
        parentArtifactKey: String?,
        parentGlobalPkgKey: String?,
        absPath: String
    ): DirRuleDecision {
        // Handle non-internal roots quickly
        when (rootKind) {
            ScanRootKind.DEVICE_PROTECTED -> {
                val b = when {
                    relPath == "cache" || relPath == "code_cache" -> "cache_de"
                    relPath.startsWith("cache/") || relPath.startsWith("code_cache/") -> "cache_de"
                    else -> "de_system_data"
                }
                val isNewTarget = (relPath == "cache" || relPath == "code_cache")
                return DirRuleDecision(bucketId = b, recordThisDirAsTarget = isNewTarget, propagateTargetMarker = parentIsTarget || isNewTarget)
            }
            ScanRootKind.EXTERNAL -> {
                val b = if (relPath == "cache" || relPath.startsWith("cache/")) "cache_ext_app" else "ext_app_files"
                // Record direct children inside external cache rather than root cache/, so update APKs remain cleanly separated
                val isDirectChildInExtCache = relPath.startsWith("cache/") && !relPath.removePrefix("cache/").contains('/')
                return DirRuleDecision(
                    bucketId = b,
                    recordThisDirAsTarget = isDirectChildInExtCache,
                    propagateTargetMarker = parentIsTarget || isDirectChildInExtCache
                )
            }
            ScanRootKind.OBB -> return DirRuleDecision(bucketId = "obb_data")
            ScanRootKind.INTERNAL_DATA -> { /* handled below */ }
        }

        // Top-level directories under context.dataDir
        if (relPath == "cache") return DirRuleDecision("cache_internal_app", recordThisDirAsTarget = false, propagateTargetMarker = false)
        if (relPath == "code_cache") return DirRuleDecision("cache_code", recordThisDirAsTarget = true, propagateTargetMarker = true)
        if (relPath == "app_textures") return DirRuleDecision("cache_textures", recordThisDirAsTarget = true, propagateTargetMarker = true)
        if (relPath == "databases") return DirRuleDecision("app_databases")
        if (relPath == "shared_prefs") return DirRuleDecision("app_shared_prefs")
        if (relPath == "no_backup") return DirRuleDecision("app_no_backup")

        // cache/ sub-classifications (record direct top-level entries individually so selective clear never spills into sibling cache buckets)
        if (relPath.startsWith("cache/")) {
            val sub = relPath.removePrefix("cache/")
            val top = sub.substringBefore('/')
            val bucket = when {
                top == "shared_chats" || top == "shared_files" || top == "plugin-export" -> "cache_shares"
                top == "claude-attachments" -> "cache_media_temp"
                top == "image_cache" -> "cache_coil_images"
                top == "deb_extract_tmp" || top.startsWith("deb_tmp_") -> "cache_installer"
                else -> "cache_internal_app"
            }
            val isDirectChildTarget = !parentIsTarget && !sub.contains('/')
            return DirRuleDecision(bucketId = bucket, recordThisDirAsTarget = isDirectChildTarget, propagateTargetMarker = parentIsTarget || isDirectChildTarget)
        }

        // WebView data & caches (Chromium / Android WebView)
        if (relPath == "app_webview" || relPath.startsWith("app_webview/") || relPath.startsWith("app_webview_")) {
            val isCacheDir = name in WEBVIEW_CACHE_DIR_NAMES ||
                (name == "CacheStorage" && parentBucket == "browser_site_data")
            if (isCacheDir) {
                val becomeTarget = parentBucket != "cache_webview_http"
                return DirRuleDecision("cache_webview_http", recordThisDirAsTarget = becomeTarget, propagateTargetMarker = true)
            }
            if (parentBucket == "cache_webview_http") {
                return DirRuleDecision("cache_webview_http", propagateTargetMarker = true)
            }
            return DirRuleDecision("browser_site_data", recordThisDirAsTarget = false, propagateTargetMarker = true)
        }

        // files/ subtree
        if (relPath == "files") return DirRuleDecision("other_files")
        if (!relPath.startsWith("files/")) {
            return DirRuleDecision(parentBucket)
        }

        val fRel = relPath.removePrefix("files/")

        // Simple top-level files/ directories
        when {
            fRel == "tmp" || fRel.startsWith("tmp/") -> {
                val isRoot = fRel == "tmp"
                return DirRuleDecision("cache_tmp_shared", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            fRel == "shm" || fRel.startsWith("shm/") -> {
                val isRoot = fRel == "shm"
                return DirRuleDecision("cache_shm", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            fRel == "datastore" || fRel.startsWith("datastore/") -> return DirRuleDecision("app_datastore")
            fRel == "notes" || fRel.startsWith("notes/") -> return DirRuleDecision("app_notes")
            fRel == "copilot_chats" || fRel.startsWith("copilot_chats/") -> return DirRuleDecision("ai_copilot_chats")
            fRel == "browser-runtime" || fRel.startsWith("browser-runtime/") ||
                fRel == "browser-extensions" || fRel.startsWith("browser-extensions/") -> return DirRuleDecision("browser_bundled_ext")
            fRel == "extensions" || fRel.startsWith("extensions/") -> return DirRuleDecision("browser_user_ext")
            fRel == "bin" || fRel.startsWith("bin/") ||
                fRel == "lib" || fRel.startsWith("lib/") ||
                fRel == "sysdata" || fRel.startsWith("sysdata/") -> return DirRuleDecision("linux_proot_runtime")
        }

        // Termux usr/
        if (fRel == "usr" || fRel.startsWith("usr/")) {
            return classifyLinuxUsrDir(
                usrRel = if (fRel == "usr") "" else fRel.removePrefix("usr/"),
                name = name,
                isUbuntu = false,
                parentBucket = if (fRel == "usr") "linux_termux_base" else parentBucket,
                parentIsTarget = parentIsTarget,
                parentGlobalPkgKey = parentGlobalPkgKey,
                absPath = absPath
            )
        }

        // Termux & Shared Home (files/home)
        if (fRel == "home" || fRel.startsWith("home/")) {
            return classifyHomeDir(
                homeRel = if (fRel == "home") "" else fRel.removePrefix("home/"),
                name = name,
                locationLabel = "~/ (Termux Home)",
                parentBucket = if (fRel == "home") "workspaces_home_files" else parentBucket,
                parentIsTarget = parentIsTarget,
                parentProjectKey = parentProjectKey,
                parentArtifactKey = parentArtifactKey,
                absPath = absPath
            )
        }

        // Ubuntu PRoot (files/ubuntu)
        if (fRel == "ubuntu" || fRel.startsWith("ubuntu/")) {
            val uRel = if (fRel == "ubuntu") "" else fRel.removePrefix("ubuntu/")
            if (uRel.isEmpty()) return DirRuleDecision("linux_ubuntu_base")

            // Inside Ubuntu root home
            if (uRel == "root" || uRel.startsWith("root/")) {
                val rRel = if (uRel == "root") "" else uRel.removePrefix("root/")
                // Skip fake workspace mountpoint inside ubuntu/root/workspace ONLY if files aren't stored there?
                // Actually if files ARE there, classify them under workspaces cleanly!
                return classifyHomeDir(
                    homeRel = rRel,
                    name = name,
                    locationLabel = "Ubuntu /root",
                    parentBucket = if (uRel == "root") "workspaces_home_files" else parentBucket,
                    parentIsTarget = parentIsTarget,
                    parentProjectKey = parentProjectKey,
                    parentArtifactKey = parentArtifactKey,
                    absPath = absPath
                )
            }

            // Ubuntu temp & var
            if (uRel == "tmp" || uRel.startsWith("tmp/") || uRel == "var/tmp" || uRel.startsWith("var/tmp/")) {
                val isRoot = uRel == "tmp" || uRel == "var/tmp"
                return DirRuleDecision("cache_tmp_ubuntu", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            if (uRel == "var/cache/apt" || uRel.startsWith("var/cache/apt/")) {
                val isRoot = uRel == "var/cache/apt"
                return DirRuleDecision("cache_apt_ubuntu", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            if (uRel == "var/lib/apt/lists" || uRel.startsWith("var/lib/apt/lists/")) {
                val isRoot = uRel == "var/lib/apt/lists"
                return DirRuleDecision("cache_apt_lists_ubuntu", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            if (uRel == "var/log" || uRel.startsWith("var/log/")) {
                val isRoot = uRel == "var/log"
                return DirRuleDecision("cache_linux_logs", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            if (uRel == "var/lib/dpkg" || uRel.startsWith("var/lib/dpkg/") || uRel == "var/lib/apt" || uRel.startsWith("var/lib/apt/")) {
                return DirRuleDecision("linux_pkg_db")
            }

            // Ubuntu usr/ & usr/local/
            if (uRel == "usr" || uRel.startsWith("usr/")) {
                return classifyLinuxUsrDir(
                    usrRel = if (uRel == "usr") "" else uRel.removePrefix("usr/"),
                    name = name,
                    isUbuntu = true,
                    parentBucket = "linux_ubuntu_base",
                    parentIsTarget = parentIsTarget,
                    parentGlobalPkgKey = parentGlobalPkgKey,
                    absPath = absPath
                )
            }

            // Cache-like folders under ubuntu var/cache etc.
            if (uRel == "var/cache" || uRel.startsWith("var/cache/")) {
                return DirRuleDecision(parentBucket)
            }

            return DirRuleDecision("linux_ubuntu_base")
        }

        return DirRuleDecision("other_files")
    }

    private fun classifyLinuxUsrDir(
        usrRel: String,
        name: String,
        isUbuntu: Boolean,
        parentBucket: String,
        parentIsTarget: Boolean,
        parentGlobalPkgKey: String?,
        absPath: String
    ): DirRuleDecision {
        val baseBucket = if (isUbuntu) "linux_ubuntu_base" else "linux_termux_base"
        if (usrRel.isEmpty()) return DirRuleDecision(baseBucket)

        // Normalize Ubuntu's local/ prefix for runtime inspection (local/lib/node_modules -> lib/node_modules)
        val norm = if (usrRel.startsWith("local/")) usrRel.removePrefix("local/") else usrRel

        if (!isUbuntu) {
            if (norm == "var/cache/apt" || norm.startsWith("var/cache/apt/")) {
                val isRoot = norm == "var/cache/apt"
                return DirRuleDecision("cache_apt_termux", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            if (norm == "var/lib/apt/lists" || norm.startsWith("var/lib/apt/lists/")) {
                val isRoot = norm == "var/lib/apt/lists"
                return DirRuleDecision("cache_apt_lists_termux", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            if (norm == "var/log" || norm.startsWith("var/log/")) {
                val isRoot = norm == "var/log"
                return DirRuleDecision("cache_linux_logs", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            if (norm == "tmp" || norm.startsWith("tmp/")) {
                val isRoot = norm == "tmp"
                return DirRuleDecision("cache_tmp_termux", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            if (norm == "glibc" || norm.startsWith("glibc/")) {
                return DirRuleDecision("linux_glibc")
            }
            if (norm == "var/lib/dpkg" || norm.startsWith("var/lib/dpkg/") ||
                norm == "var/lib/apt" || norm.startsWith("var/lib/apt/")) {
                return DirRuleDecision("linux_pkg_db")
            }
        }

        // node_modules/.cache is safe extra cache
        if (name == ".cache" && norm.contains("lib/node_modules/")) {
            return DirRuleDecision("cache_project_dev", recordThisDirAsTarget = !parentIsTarget, propagateTargetMarker = true)
        }
        if (parentBucket == "cache_project_dev") {
            return DirRuleDecision("cache_project_dev", propagateTargetMarker = true, globalPkgKey = parentGlobalPkgKey)
        }

        // Global node_modules + per-package tracking
        if (norm == "lib/node_modules" || norm.startsWith("lib/node_modules/")) {
            val nmSub = if (norm == "lib/node_modules") "" else norm.removePrefix("lib/node_modules/")
            val parts = if (nmSub.isEmpty()) emptyList() else nmSub.split('/')
            var newPkg: GlobalPkgAccum? = null
            var activePkgKey = parentGlobalPkgKey

            if (parts.size == 1 && !parts[0].startsWith("@") && parts[0] != ".bin" && parts[0] != ".cache") {
                val pkgName = parts[0]
                activePkgKey = absPath
                newPkg = GlobalPkgAccum(
                    name = pkgName,
                    runtime = if (isUbuntu) "npm global (Ubuntu)" else "npm global (Termux)",
                    path = absPath
                )
            } else if (parts.size == 2 && parts[0].startsWith("@")) {
                val pkgName = "${parts[0]}/${parts[1]}"
                activePkgKey = absPath
                newPkg = GlobalPkgAccum(
                    name = pkgName,
                    runtime = if (isUbuntu) "npm global (Ubuntu)" else "npm global (Termux)",
                    path = absPath
                )
            }

            val b = if (nmSub == "@anthropic-ai" || nmSub.startsWith("@anthropic-ai/claude-code")) {
                "ai_claude_bin"
            } else {
                "tool_nodejs"
            }
            return DirRuleDecision(
                bucketId = b,
                globalPkgKey = activePkgKey,
                newGlobalPkg = newPkg
            )
        }

        // Specific runtimes inside usr/
        val bucket = when {
            norm == "include/node" || norm.startsWith("include/node/") ||
                norm == "share/doc/nodejs" || norm.startsWith("share/doc/nodejs/") -> "tool_nodejs"
            norm.startsWith("lib/python") -> "tool_python"
            norm.startsWith("lib/clang") || norm.startsWith("lib/llvm") ||
                norm.startsWith("lib/gcc") || norm.startsWith("include/c++") -> "tool_compilers"
            norm.startsWith("lib/go") || norm == "go" || norm.startsWith("go/") -> "tool_golang"
            norm.startsWith("lib/jvm") || norm.startsWith("openjdk") -> "tool_java"
            norm.startsWith("libexec/git-core") || norm.startsWith("lib/git-core") -> "tool_git"
            else -> baseBucket
        }
        return DirRuleDecision(bucketId = bucket, globalPkgKey = parentGlobalPkgKey)
    }

    private fun classifyHomeDir(
        homeRel: String,
        name: String,
        locationLabel: String,
        parentBucket: String,
        parentIsTarget: Boolean,
        parentProjectKey: String?,
        parentArtifactKey: String?,
        absPath: String
    ): DirRuleDecision {
        if (homeRel.isEmpty()) return DirRuleDecision(parentBucket)
        val top = homeRel.substringBefore('/')

        // 1. Google Antigravity (.gemini, .antigravity, .antigem_bridge)
        if (top == ".gemini") {
            val gSub = if (homeRel == ".gemini") "" else homeRel.removePrefix(".gemini/")
            if (gSub.startsWith("bin") && (gSub == "bin" || gSub.startsWith("bin/")) ||
                GEMINI_SUB_BIN_REGEX.matches(gSub)) {
                return DirRuleDecision("ai_agy_cli")
            }
            if (gSub == "antigravity/browser_recordings" || gSub.startsWith("antigravity/browser_recordings/")) {
                val isRoot = gSub == "antigravity/browser_recordings"
                return DirRuleDecision("cache_agy_recordings", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            if (gSub == "antigravity/implicit" || gSub.startsWith("antigravity/implicit/") ||
                name == "cache" || name == ".cache" || name == "log" || name == "logs" || name == "crashes") {
                val becomeTarget = parentBucket != "cache_agy_logs"
                return DirRuleDecision("cache_agy_logs", recordThisDirAsTarget = becomeTarget, propagateTargetMarker = true)
            }
            if (parentBucket == "cache_agy_logs") {
                return DirRuleDecision("cache_agy_logs", propagateTargetMarker = true)
            }
            return when {
                gSub == "antigravity/conversations" || gSub.startsWith("antigravity/conversations/") -> DirRuleDecision("ai_agy_chats")
                gSub == "antigravity/brain" || gSub.startsWith("antigravity/brain/") -> DirRuleDecision("ai_agy_brain")
                gSub == "antigravity/knowledge" || gSub.startsWith("antigravity/knowledge/") -> DirRuleDecision("ai_agy_knowledge")
                else -> DirRuleDecision("ai_agy_config")
            }
        }
        if (top == ".antigravity" || top == ".antigem_bridge") {
            return DirRuleDecision("ai_agy_config")
        }

        // 2. Claude Code (.claude)
        if (top == ".claude") {
            val cSub = if (homeRel == ".claude") "" else homeRel.removePrefix(".claude/")
            val cTop = cSub.substringBefore('/')
            if (cTop in CLAUDE_EPHEMERAL_DIRS || name == "cache" || name == ".cache") {
                val becomeTarget = parentBucket != "cache_claude_logs"
                return DirRuleDecision("cache_claude_logs", recordThisDirAsTarget = becomeTarget, propagateTargetMarker = true)
            }
            if (parentBucket == "cache_claude_logs") {
                return DirRuleDecision("cache_claude_logs", propagateTargetMarker = true)
            }
            return DirRuleDecision("ai_claude_sessions")
        }

        // 3. Other AI Agents
        if (top == ".copilot" || homeRel == ".config/github-copilot" || homeRel.startsWith(".config/github-copilot/")) {
            return DirRuleDecision("ai_copilot_chats")
        }
        if (top == ".codex" || top == ".opencode") {
            return DirRuleDecision("ai_other_agents")
        }
        if (top == "extensions") {
            return DirRuleDecision("browser_user_ext")
        }

        // 4. ~/.local
        if (top == ".local") {
            val lSub = if (homeRel == ".local") "" else homeRel.removePrefix(".local/")
            return when {
                lSub == "share/claude" || lSub.startsWith("share/claude/") -> DirRuleDecision("ai_claude_bin")
                lSub == "share/pnpm" || lSub.startsWith("share/pnpm/") -> {
                    val isRoot = lSub == "share/pnpm"
                    DirRuleDecision("cache_pnpm", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
                }
                lSub == "share/Trash" || lSub.startsWith("share/Trash/") -> {
                    val isRoot = lSub == "share/Trash"
                    DirRuleDecision("cache_trash", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
                }
                lSub.startsWith("lib/python") -> DirRuleDecision("tool_python")
                else -> DirRuleDecision("tool_local_user")
            }
        }

        // 5. Dev & XDG Caches
        when {
            top == ".npm" -> {
                val isRoot = homeRel == ".npm"
                return DirRuleDecision("cache_npm", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            top == ".pnpm-store" -> {
                val isRoot = homeRel == ".pnpm-store"
                return DirRuleDecision("cache_pnpm", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            homeRel == ".yarn/cache" || homeRel.startsWith(".yarn/cache/") -> {
                val isRoot = homeRel == ".yarn/cache"
                return DirRuleDecision("cache_yarn", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
            }
            top == ".cache" -> {
                val cSub = if (homeRel == ".cache") "" else homeRel.removePrefix(".cache/")
                val cTop = cSub.substringBefore('/')
                val (bucket, isRootMatch) = when (cTop) {
                    "pip", "uv" -> "cache_pip" to (cSub == cTop)
                    "yarn" -> "cache_yarn" to (cSub == cTop)
                    "pnpm" -> "cache_pnpm" to (cSub == cTop)
                    "go-build" -> "cache_go" to (cSub == cTop)
                    "ms-playwright", "puppeteer" -> "tool_playwright" to (cSub == cTop)
                    "huggingface", "torch" -> "cache_ml_models" to (cSub == cTop)
                    "" -> "cache_xdg_home" to false
                    else -> "cache_xdg_home" to (cSub == cTop)
                }
                return DirRuleDecision(
                    bucketId = bucket,
                    recordThisDirAsTarget = isRootMatch,
                    propagateTargetMarker = parentIsTarget || isRootMatch
                )
            }
            top == "go" -> {
                if (homeRel == "go/pkg/mod" || homeRel.startsWith("go/pkg/mod/")) {
                    val isRoot = homeRel == "go/pkg/mod"
                    return DirRuleDecision("cache_go", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
                }
                return DirRuleDecision("tool_golang")
            }
            top == ".cargo" -> {
                if (homeRel == ".cargo/registry" || homeRel.startsWith(".cargo/registry/") ||
                    homeRel == ".cargo/git" || homeRel.startsWith(".cargo/git/")) {
                    val isRoot = homeRel == ".cargo/registry" || homeRel == ".cargo/git"
                    return DirRuleDecision("cache_cargo", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
                }
                return DirRuleDecision("tool_rust")
            }
            top == ".rustup" -> return DirRuleDecision("tool_rust")
            top == ".gradle" -> {
                if (homeRel == ".gradle/caches" || homeRel.startsWith(".gradle/caches/") ||
                    homeRel == ".gradle/wrapper/dists" || homeRel.startsWith(".gradle/wrapper/dists/")) {
                    val isRoot = homeRel == ".gradle/caches" || homeRel == ".gradle/wrapper/dists"
                    return DirRuleDecision("cache_gradle", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
                }
                return DirRuleDecision("tool_java")
            }
            top == ".m2" -> {
                if (homeRel == ".m2/repository" || homeRel.startsWith(".m2/repository/")) {
                    val isRoot = homeRel == ".m2/repository"
                    return DirRuleDecision("cache_gradle", recordThisDirAsTarget = isRoot, propagateTargetMarker = parentIsTarget || isRoot)
                }
                return DirRuleDecision("tool_java")
            }
            top == ".nvm" || top == ".fnm" || top == ".n" || top == ".bun" -> return DirRuleDecision("tool_node_managers")
            top == ".pyenv" || top == ".conda" || top == ".venv" || top == "venv" -> return DirRuleDecision("tool_python")
            top == ".android" || top == "android-sdk" -> return DirRuleDecision("tool_android_sdk")
        }

        // 6. Shell configs / Dotfiles / Termux storage symlinks
        if (top == ".ssh" || top == ".gnupg" || top == ".config" || top == ".termux" || top == "storage" || top.startsWith(".")) {
            return DirRuleDecision("linux_dotfiles")
        }

        // 7. Workspaces & Projects (~/projects/<name> or non-hidden top-level folders)
        val isUnderProjects = (top == "projects")
        var projectKey = parentProjectKey
        var workspaceItem: WorkspaceAccum? = null

        if (isUnderProjects && homeRel.startsWith("projects/") && homeRel.removePrefix("projects/").indexOf('/') == -1) {
            // Immediate project folder under ~/projects/<name>
            projectKey = absPath
            workspaceItem = WorkspaceAccum(name = name, path = absPath, locationLabel = "$locationLabel/projects")
        } else if (!isUnderProjects && homeRel == top && !(locationLabel.startsWith("Ubuntu") && top == "workspace")) {
            // Top-level non-dot folder directly in ~/ (e.g. ~/my-repo)
            projectKey = absPath
            workspaceItem = WorkspaceAccum(name = name, path = absPath, locationLabel = locationLabel)
        }

        val workspaceBucket = if (isUnderProjects) "workspaces_projects" else "workspaces_home_files"

        // Detect project bundler/python caches -> put directly into SAFE_CACHE so extra caches always clear them!
        if (name == "__pycache__" || name == ".pytest_cache" || name == ".mypy_cache" ||
            name == ".vite" || name == ".turbo" ||
            (name == ".cache" && homeRel.contains("/node_modules/")) ||
            (name == "cache" && homeRel.endsWith("/.next/cache"))
        ) {
            val becomeTarget = parentBucket != "cache_project_dev"
            return DirRuleDecision(
                bucketId = "cache_project_dev",
                recordThisDirAsTarget = becomeTarget,
                propagateTargetMarker = true,
                projectKey = projectKey,
                workspaceItem = workspaceItem
            )
        }
        if (parentBucket == "cache_project_dev") {
            return DirRuleDecision(
                bucketId = "cache_project_dev",
                propagateTargetMarker = true,
                projectKey = projectKey
            )
        }

        // Detect heavy rebuildable workspace artifacts (node_modules, .gradle, build, target, .next, .venv)
        var artifactKey = parentArtifactKey
        var newArtifact: ArtifactAccum? = null
        if (parentArtifactKey == null && name in WORKSPACE_ARTIFACT_DIRS) {
            artifactKey = absPath
            val pName = workspaceAccumHintName(homeRel)
            newArtifact = ArtifactAccum(
                kind = name,
                projectName = pName,
                path = absPath
            )
        }

        return DirRuleDecision(
            bucketId = workspaceBucket,
            projectKey = projectKey,
            workspaceItem = workspaceItem,
            artifactKey = artifactKey,
            newArtifact = newArtifact
        )
    }

    private fun workspaceAccumHintName(homeRel: String): String {
        val parts = homeRel.split('/')
        return if (parts.size >= 2 && parts[0] == "projects") parts[1] else parts[0]
    }

    private fun resolveFileRule(
        rootKind: ScanRootKind,
        relPath: String,
        fileName: String,
        parentBucket: String
    ): String {
        if (rootKind == ScanRootKind.EXTERNAL) {
            if (relPath.startsWith("cache/") && fileName.endsWith(".apk", ignoreCase = true)) {
                return "cache_update_apks"
            }
            return parentBucket
        }
        if (rootKind != ScanRootKind.INTERNAL_DATA) return parentBucket

        if (relPath.startsWith("cache/")) {
            val sub = relPath.removePrefix("cache/")
            if (!sub.contains('/')) {
                return when {
                    sub.startsWith("voice_note_") -> "cache_media_temp"
                    sub.startsWith("bootstrap-") || sub.startsWith("inner_bootstrap") -> "cache_installer"
                    sub.startsWith("restore_payload_") || sub.startsWith("project_restore_payload_") || sub.startsWith("backup_targets_") -> "cache_backup_temp"
                    else -> parentBucket
                }
            }
            return parentBucket
        }

        if (parentBucket == "ai_agy_config" && fileName.endsWith(".log", ignoreCase = true)) {
            return "cache_agy_logs"
        }
        if (parentBucket == "ai_claude_sessions" && fileName.endsWith(".log", ignoreCase = true)) {
            return "cache_claude_logs"
        }

        // Root dotfiles inside ~/ or /root
        if (relPath.startsWith("files/home/") || relPath.startsWith("files/ubuntu/root/")) {
            val sub = if (relPath.startsWith("files/home/")) {
                relPath.removePrefix("files/home/")
            } else {
                relPath.removePrefix("files/ubuntu/root/")
            }
            if (!sub.contains('/')) {
                return when {
                    sub == ".claude.json" -> "ai_claude_sessions"
                    sub.startsWith("agy") -> "ai_agy_cli"
                    sub.startsWith(".") -> "linux_dotfiles"
                    else -> parentBucket
                }
            }
        }

        if (relPath.startsWith("files/usr/bin/") || relPath.startsWith("files/ubuntu/usr/local/bin/") || relPath.startsWith("files/ubuntu/usr/bin/")) {
            return when {
                fileName == "agy" || fileName == "agy_ide_bridge" || fileName == "claude" -> "ai_agy_cli"
                fileName == "node" || fileName == "npm" || fileName == "npx" -> "tool_nodejs"
                fileName.startsWith("python") || fileName.startsWith("pip") -> "tool_python"
                fileName == "go" || fileName == "gofmt" -> "tool_golang"
                fileName.startsWith("git") -> "tool_git"
                fileName.startsWith("clang") || fileName.startsWith("gcc") || fileName.startsWith("lld") -> "tool_compilers"
                else -> parentBucket
            }
        }

        return parentBucket
    }

    private val GEMINI_SUB_BIN_REGEX = Regex("^[^/]+/bin(/.*)?$")

    private val WEBVIEW_CACHE_DIR_NAMES = setOf(
        "Cache",
        "Code Cache",
        "GPUCache",
        "DawnCache",
        "GraphiteDawnCache",
        "DawnWebGPUCache",
        "DawnGraphiteCache",
        "ShaderCache",
        "GrShaderCache",
        "blob_storage"
    )

    private val CLAUDE_EPHEMERAL_DIRS = setOf(
        "debug",
        "telemetry",
        "statsig",
        "shell-snapshots",
        "paste-cache",
        "image-cache",
        "cache",
        "todos"
    )

    private val WORKSPACE_ARTIFACT_DIRS = setOf(
        "node_modules",
        ".gradle",
        "build",
        "target",
        ".next",
        ".venv",
        "venv"
    )

    // ==================== System & Metadata Queries ====================

    private fun queryOsQuota(context: Context): OsQuotaSnapshot {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val statsMgr = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
                if (statsMgr != null) {
                    val stats = statsMgr.queryStatsForPackage(
                        StorageManager.UUID_DEFAULT,
                        context.packageName,
                        Process.myUserHandle()
                    )
                    val app = stats.appBytes.coerceAtLeast(0L)
                    val cache = stats.cacheBytes.coerceAtLeast(0L)
                    val netData = (stats.dataBytes - cache).coerceAtLeast(0L)
                    return OsQuotaSnapshot(
                        supported = true,
                        appBytes = app,
                        netDataBytes = netData,
                        cacheBytes = cache,
                        totalBytes = app + stats.dataBytes
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "StorageStatsManager query failed: ${e.message}")
            }
        }
        return OsQuotaSnapshot(supported = false, appBytes = 0L, netDataBytes = 0L, cacheBytes = 0L, totalBytes = 0L)
    }

    private fun estimateApkFallbackBytes(context: Context): Long {
        var total = 0L
        runCatching {
            val info = context.applicationInfo
            info.sourceDir?.let { src ->
                val apkFile = File(src)
                val st = lstatOrNull(apkFile)
                total += st?.let { it.st_blocks * 512L } ?: apkFile.length()
                // Also try split APKs if any
                info.splitSourceDirs?.forEach { split ->
                    val sf = File(split)
                    total += lstatOrNull(sf)?.let { it.st_blocks * 512L } ?: sf.length()
                }
            }
        }
        return total
    }

    private fun queryDeviceDiskSpace(context: Context): Pair<Long, Long> {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes to stat.totalBytes
        } catch (_: Exception) {
            0L to 0L
        }
    }

    private fun readInstalledDpkgPackages(context: Context): List<DpkgPackageEntry> {
        val candidates = listOf(
            "Termux" to File(context.filesDir, "usr/var/lib/dpkg/status"),
            "Ubuntu" to File(context.filesDir, "ubuntu/var/lib/dpkg/status")
        )
        val result = mutableListOf<DpkgPackageEntry>()
        for ((envName, statusFile) in candidates) {
            if (!statusFile.exists() || !statusFile.canRead()) continue
            runCatching {
                var pkgName = ""
                var pkgStatus = ""
                var pkgVersion = ""
                var installedSizeKb = 0L

                fun flushEntry() {
                    if (pkgName.isNotEmpty() && pkgStatus.contains("install ok installed")) {
                        result.add(
                            DpkgPackageEntry(
                                name = pkgName,
                                environment = envName,
                                version = pkgVersion,
                                estimatedBytes = installedSizeKb * 1024L
                            )
                        )
                    }
                    pkgName = ""
                    pkgStatus = ""
                    pkgVersion = ""
                    installedSizeKb = 0L
                }

                statusFile.bufferedReader().use { reader ->
                    var line: String? = reader.readLine()
                    while (line != null) {
                        val trimmed = line
                        if (trimmed.isEmpty()) {
                            flushEntry()
                        } else when {
                            trimmed.startsWith("Package:") -> pkgName = trimmed.substring(8).trim()
                            trimmed.startsWith("Status:") -> pkgStatus = trimmed.substring(7).trim()
                            trimmed.startsWith("Version:") -> pkgVersion = trimmed.substring(8).trim()
                            trimmed.startsWith("Installed-Size:") -> {
                                installedSizeKb = trimmed.substring(15).trim().toLongOrNull() ?: 0L
                            }
                        }
                        line = reader.readLine()
                    }
                    flushEntry()
                }
            }
        }
        return result.sortedByDescending { it.estimatedBytes }
    }

    private fun scanExternalBackups(context: Context): ExternalStorageReport {
        val downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val antigemDir = File(downloads, "Antigem")
        val hasPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            downloads.canRead()
        }
        val items = mutableListOf<ExternalBackupItem>()

        fun addFile(file: File, category: String) {
            if (!file.exists() || !file.isFile) return
            val st = lstatOrNull(file)
            val bytes = st?.let { it.st_blocks * 512L } ?: file.length()
            items.add(
                ExternalBackupItem(
                    name = file.name,
                    path = file.absolutePath,
                    categoryLabel = category,
                    bytes = bytes,
                    lastModifiedMs = file.lastModified()
                )
            )
        }

        if (antigemDir.exists() && antigemDir.canRead()) {
            val stack = ArrayDeque<File>()
            stack.addLast(antigemDir)
            while (stack.isNotEmpty()) {
                val cur = stack.removeLast()
                cur.listFiles()?.forEach { child ->
                    val st = lstatOrNull(child) ?: return@forEach
                    if (OsConstants.S_ISLNK(st.st_mode)) return@forEach
                    if (OsConstants.S_ISDIR(st.st_mode)) {
                        stack.addLast(child)
                    } else {
                        val cat = when {
                            child.name.contains("bootrapz") || child.name.contains("bootstrap") -> "Rootfs Backup"
                            child.name.contains("ubuntu") -> "Ubuntu Backup"
                            child.name.contains("chats") -> "Chats Backup"
                            child.name.contains("project") -> "Project Backup"
                            else -> "Backup File"
                        }
                        addFile(child, cat)
                    }
                }
            }
        }

        val exportedBridge = File(downloads, "agy_ide_bridge")
        if (exportedBridge.exists() && exportedBridge.canRead()) {
            addFile(exportedBridge, "Exported Binary")
        }

        val sorted = items.sortedByDescending { it.bytes }
        return ExternalStorageReport(
            rootPath = antigemDir.absolutePath,
            hasPermission = hasPerm,
            totalBytes = sorted.sumOf { it.bytes },
            items = sorted
        )
    }

    private fun lstatOrNull(file: File): android.system.StructStat? {
        return try {
            Os.lstat(file.absolutePath)
        } catch (_: Exception) {
            null
        }
    }
}
