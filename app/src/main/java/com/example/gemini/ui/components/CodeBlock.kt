package com.example.gemini.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.gemini.theme.*
import kotlinx.coroutines.delay
import java.util.regex.Pattern

// Syntax theme colors (One Dark for dark theme, crisp slate/ink for light theme)
private val SynKeywordDark = Color(0xFFC678DD)
private val SynTypeDark = Color(0xFFE5C07B)
private val SynStringDark = Color(0xFF98C379)
private val SynNumberDark = Color(0xFFD19A66)
private val SynCommentDark = Color(0xFF7F848E)
private val SynAnnotationDark = Color(0xFFE5C07B)
private val SynFunctionDark = Color(0xFF61AFEF)

private val SynKeywordLight = Color(0xFF9333EA)
private val SynTypeLight = Color(0xFF9A6700)
private val SynStringLight = Color(0xFF15803D)
private val SynNumberLight = Color(0xFFB45309)
private val SynCommentLight = Color(0xFF6B7280)
private val SynAnnotationLight = Color(0xFFB45309)
private val SynFunctionLight = Color(0xFF0369A1)

/**
 * Checks whether a code block is runnable / previewable as an interactive Web/HTML/SVG Artifact.
 */
fun isPreviewableCode(code: String, language: String): Boolean {
    val lang = language.lowercase().trim()
    if (lang in listOf("html", "htm", "svg", "xhtml", "web", "webapp", "xml")) return true

    val trimmed = code.trim()
    return trimmed.startsWith("<!DOCTYPE html", ignoreCase = true) ||
            trimmed.startsWith("<html", ignoreCase = true) ||
            trimmed.startsWith("<svg", ignoreCase = true) ||
            (trimmed.contains("<script", ignoreCase = true) && trimmed.contains("</script>", ignoreCase = true)) ||
            (trimmed.contains("<style", ignoreCase = true) && trimmed.contains("</style>", ignoreCase = true))
}

/**
 * Builds standard responsive HTML wrapper for previewing code artifacts.
 */
fun prepareHtmlForPreview(rawCode: String, isDark: Boolean): String {
    val trimmed = rawCode.trim()
    val isRawSvg = trimmed.startsWith("<svg", ignoreCase = true)

    if (isRawSvg) {
        val bg = if (isDark) "#1E1E2E" else "#F8FAFC"
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
                <style>
                    * { box-sizing: border-box; }
                    html, body {
                        margin: 0; padding: 12px;
                        background-color: $bg;
                        display: flex; justify-content: center; align-items: center;
                        min-height: 100vh; overflow: auto;
                    }
                    svg { max-width: 100%; height: auto; display: block; }
                </style>
            </head>
            <body>
                $trimmed
            </body>
            </html>
        """.trimIndent()
    }

    // If it's already a full HTML document, ensure responsive viewport
    if (trimmed.contains("<head", ignoreCase = true)) {
        if (!trimmed.contains("viewport", ignoreCase = true)) {
            return trimmed.replace(
                Regex("<head>", RegexOption.IGNORE_CASE),
                "<head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes\">"
            )
        }
        return trimmed
    }

    // Wrap snippet in standard responsive container
    val bg = if (isDark) "#181825" else "#FFFFFF"
    val fg = if (isDark) "#CDD6F4" else "#1E293B"
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=3.0, user-scalable=yes">
            <style>
                * { box-sizing: border-box; }
                body {
                    margin: 0; padding: 12px;
                    background-color: $bg;
                    color: $fg;
                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                    overflow-x: hidden;
                }
            </style>
        </head>
        <body>
            $trimmed
        </body>
        </html>
    """.trimIndent()
}

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CodeBlock(
    code: String,
    language: String = "",
    modifier: Modifier = Modifier
) {
    val t = graventTokens()
    val context = LocalContext.current
    var isCopied by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Code, 1 = Preview
    var reloadKey by remember { mutableIntStateOf(0) }
    var isFullscreen by remember { mutableStateOf(false) }
    var isSoftWrap by remember { mutableStateOf(false) }

    val codeKey = remember(code, language) {
        "${code.hashCode()}_${language}"
    }
    var isExpanded by remember(codeKey) {
        mutableStateOf(CodeBlockExpansionCache.isExpanded(codeKey, default = false))
    }
    val toggleExpand = {
        val next = !isExpanded
        isExpanded = next
        CodeBlockExpansionCache.setExpanded(codeKey, next)
    }

    val cachedCode = remember(code, language, t.isDark) {
        CodeBlockCache.getOrCompute(code, language, isDark = t.isDark)
    }
    val lineCount = cachedCode.lineCount
    val isLongCode = cachedCode.isLongCode

    val isPreviewable = remember(code, language) {
        isPreviewableCode(code, language)
    }
    val isShellCode = remember(language) {
        language.lowercase().trim() in setOf("bash", "sh", "shell", "zsh", "console", "shellsession", "terminal")
    }
    var showRunDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isCopied) {
        if (isCopied) {
            delay(2500)
            isCopied = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(t.codeBg)
            .border(1.dp, t.border, RoundedCornerShape(12.dp))
    ) {
        // Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(t.codeHeaderBg)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Language Badge + Line count
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = language.ifEmpty { "code" }.uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = t.textPrimary,
                    letterSpacing = 0.5.sp
                )
                if (lineCount > 1) {
                    Text(
                        text = "• $lineCount lines",
                        fontSize = 10.5.sp,
                        color = t.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Interactive Switcher: [Code | Preview] when previewable
            if (isPreviewable) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(t.elevatedBg)
                        .border(0.5.dp, t.subtleBorder, RoundedCornerShape(16.dp))
                        .padding(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Code Tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selectedTab == 0) t.accent else Color.Transparent)
                            .clickable { selectedTab = 0 }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.Code,
                                contentDescription = null,
                                tint = if (selectedTab == 0) t.onAccent else t.textMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Code",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTab == 0) t.onAccent else t.textMuted
                            )
                        }
                    }

                    // Preview Tab
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (selectedTab == 1) t.accent else Color.Transparent)
                            .clickable { selectedTab = 1 }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = if (selectedTab == 1) t.onAccent else t.textMuted,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "Preview",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectedTab == 1) t.onAccent else t.textMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // In Preview Mode: Reload button
                if (selectedTab == 1) {
                    IconButton(
                        onClick = { reloadKey++ },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload preview",
                            tint = t.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            // Soft wrap toggle when viewing Code
            if (selectedTab == 0) {
                IconButton(
                    onClick = { isSoftWrap = !isSoftWrap },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.WrapText,
                        contentDescription = if (isSoftWrap) "Disable line wrap" else "Wrap long lines",
                        tint = if (isSoftWrap) t.accent else t.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Fullscreen Button (always present for all code blocks and preview modes)
            IconButton(
                onClick = { isFullscreen = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.OpenInFull,
                    contentDescription = "Fullscreen",
                    tint = t.textMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            // Expand / Collapse toggle for long code
            if (isLongCode && selectedTab == 0) {
                IconButton(
                    onClick = { toggleExpand() },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse code block" else "Expand full height",
                        tint = if (isExpanded) t.accent else t.textMuted,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }

            // Copy Button
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Copied Code", code)
                    clipboard.setPrimaryClip(clip)
                    isCopied = true
                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
            ) {
                if (isCopied) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Copied",
                        tint = t.successAccent,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy code",
                        tint = t.textMuted,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }

            // Run Button (shell code only): edit the command, then run it in a terminal over the chat
            if (isShellCode) {
                IconButton(
                    onClick = { showRunDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Run in terminal",
                        tint = t.accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        HorizontalDivider(thickness = 0.7.dp, color = t.subtleBorder)

        // Body Content
        if (selectedTab == 1 && isPreviewable) {
            // Live Interactive Preview Runner
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 450.dp)
                    .background(Color.White)
            ) {
                key(reloadKey) {
                    val previewHtml = remember(code, t.isDark) {
                        prepareHtmlForPreview(code, isDark = t.isDark)
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.loadWithOverviewMode = true
                                settings.useWideViewPort = true
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                loadDataWithBaseURL("https://localhost", previewHtml, "text/html", "UTF-8", null)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        } else {
            // Ultra-fast windowed syntax-highlighted code rendering
            val displayText = if (isExpanded || !isLongCode) cachedCode.fullText else cachedCode.previewText
            val horizontalScroll = rememberScrollState()

            Box(modifier = Modifier.fillMaxWidth()) {
                // Rebuilt on expand/collapse or wrap toggle so a live selection can't point at swapped-out text
                key(isExpanded, isSoftWrap) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            text = displayText,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 19.sp,
                            softWrap = isSoftWrap,
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isSoftWrap) Modifier else Modifier.horizontalScroll(horizontalScroll))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        )
                    }
                }
            }

            // Subtle Expand/Collapse footer for long code blocks
            if (isLongCode) {
                HorizontalDivider(thickness = 0.5.dp, color = t.subtleBorder)
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { toggleExpand() },
                    color = t.codeHeaderBg
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 7.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val hiddenLines = (lineCount - CODE_PREVIEW_LINES).coerceAtLeast(1)
                        Text(
                            text = if (isExpanded) "Collapse code block" else "Show all $lineCount lines ($hiddenLines more)",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = t.textPrimary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = t.textSecondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    // Fullscreen Code & Interactive Artifact Modal
    if (showRunDialog) {
        ChatCommandRunDialog(
            command = code.trimEnd().lines().joinToString("\n") { it.removePrefix("$ ") },
            onDismiss = { showRunDialog = false }
        )
    }

    if (isFullscreen) {
        Dialog(
            onDismissRequest = { isFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = if (selectedTab == 1 && isPreviewable) "Preview: ${language.uppercase()}" else language.ifEmpty { "Code" }.uppercase(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (selectedTab == 1 && isPreviewable) "Interactive Artifact" else "$lineCount lines",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { isFullscreen = false }) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            if (isPreviewable) {
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (selectedTab == 0) t.accent else Color.Transparent)
                                            .clickable { selectedTab = 0 }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "Code",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selectedTab == 0) t.onAccent else t.textMuted
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(if (selectedTab == 1) t.accent else Color.Transparent)
                                            .clickable { selectedTab = 1 }
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = "Preview",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (selectedTab == 1) t.onAccent else t.textMuted
                                        )
                                    }
                                }

                                if (selectedTab == 1) {
                                    IconButton(onClick = { reloadKey++ }) {
                                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                                    }
                                }
                            }

                            var modalCopied by remember { mutableStateOf(false) }
                            LaunchedEffect(modalCopied) {
                                if (modalCopied) {
                                    delay(2000)
                                    modalCopied = false
                                }
                            }

                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText("Copied Code", code)
                                    clipboard.setPrimaryClip(clip)
                                    modalCopied = true
                                    Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Icon(
                                    imageVector = if (modalCopied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                                    contentDescription = "Copy code",
                                    tint = if (modalCopied) QuotaGreen else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    if (selectedTab == 1 && isPreviewable) {
                        // Fullscreen WebView preview
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White)
                        ) {
                            key(reloadKey) {
                                val previewHtml = remember(code) {
                                    prepareHtmlForPreview(code, isDark = false)
                                }
                                AndroidView(
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT
                                            )
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.loadWithOverviewMode = true
                                            settings.useWideViewPort = true
                                            settings.builtInZoomControls = true
                                            settings.displayZoomControls = false
                                            loadDataWithBaseURL("https://localhost", previewHtml, "text/html", "UTF-8", null)
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    } else {
                        // Fullscreen Code view with line numbers gutter
                        val modalVerticalScroll = rememberScrollState()
                        val modalHorizontalScroll = rememberScrollState()
                        val lineNumbersText = remember(lineCount) {
                            (1..lineCount).joinToString("\n")
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(t.codeBg)
                                .verticalScroll(modalVerticalScroll)
                        ) {
                            // Line numbers gutter
                            Text(
                                text = lineNumbersText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.5.sp,
                                lineHeight = 20.sp,
                                color = t.textMuted,
                                textAlign = TextAlign.End,
                                modifier = Modifier
                                    .padding(start = 12.dp, end = 10.dp, top = 14.dp, bottom = 14.dp)
                                    .widthIn(min = 28.dp)
                            )

                            // Vertical divider line
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .fillMaxHeight()
                                    .background(t.subtleBorder)
                            )

                            // Syntax-highlighted code
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .horizontalScroll(modalHorizontalScroll)
                                    .padding(horizontal = 14.dp, vertical = 14.dp)
                            ) {
                                Text(
                                    text = cachedCode.fullText,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    lineHeight = 20.sp,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

const val CODE_PREVIEW_LINES = 10

data class CachedCodeBlock(
    val fullText: AnnotatedString,
    val previewText: AnnotatedString,
    val lineCount: Int,
    val isLongCode: Boolean
)

private data class CodeBlockCacheKey(
    val code: String,
    val lang: String,
    val isDark: Boolean
)

object CodeBlockCache {
    private val cache = android.util.LruCache<CodeBlockCacheKey, CachedCodeBlock>(400)

    fun prewarm(code: String, language: String, isDark: Boolean = true) {
        getOrCompute(code, language, isDark)
    }

    fun getOrCompute(code: String, language: String, isDark: Boolean = true): CachedCodeBlock {
        val normLang = language.lowercase().trim()
        val key = CodeBlockCacheKey(code, normLang, isDark)
        val hit = cache.get(key)
        if (hit != null) return hit
        val full = if (normLang in setOf("diff", "patch") || code.startsWith("diff --git ")) {
            highlightDiff(code, isDark)
        } else {
            highlightSyntax(code, normLang, isDark)
        }
        val raw = full.text
        var lines = 1
        var endOfPreview = raw.length
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '\n') {
                lines++
                if (lines == CODE_PREVIEW_LINES + 1) {
                    endOfPreview = i
                }
            }
            i++
        }
        val isLong = lines > CODE_PREVIEW_LINES
        val preview = if (isLong) full.subSequence(0, endOfPreview) else full
        val cached = CachedCodeBlock(full, preview, lines, isLong)
        cache.put(key, cached)
        return cached
    }
}

private val COMMENT_PY_SH = Pattern.compile("#.*", Pattern.MULTILINE)
private val COMMENT_SQL = Pattern.compile("(--.*|/\\*[\\s\\S]*?\\*/)", Pattern.MULTILINE)
private val COMMENT_GENERIC = Pattern.compile("(//.*|/\\*[\\s\\S]*?\\*/|#.*)", Pattern.MULTILINE)

private val STRING_PATTERN = Pattern.compile("(\"(\\\\.|[^\"\\\\])*\"|'(\\\\.|[^'\\\\])*'|`(\\\\.|[^`\\\\])*`)")
private val NUMBER_PATTERN = Pattern.compile("\\b(0x[0-9a-fA-F]+|\\d+(\\.\\d+)?([eE][+-]?\\d+)?[fFL]?)\\b")
private val ANNOTATION_PATTERN = Pattern.compile("@[A-Za-z0-9_.]+")

private val KOTLIN_KW_PATTERN = Pattern.compile("\\b(package|import|fun|val|var|class|interface|object|enum|return|if|else|when|for|while|do|try|catch|finally|throw|override|private|public|protected|internal|abstract|data|sealed|open|const|suspend|inline|is|as|in|by|companion|lateinit|typealias|this|super|null|true|false)\\b")
private val PYTHON_KW_PATTERN = Pattern.compile("\\b(def|class|return|if|elif|else|for|while|try|except|finally|raise|import|from|as|with|lambda|async|await|yield|pass|break|continue|global|nonlocal|in|is|not|and|or|None|True|False|self|cls)\\b")
private val JS_KW_PATTERN = Pattern.compile("\\b(function|const|let|var|return|if|else|switch|case|default|for|while|do|try|catch|finally|throw|import|export|from|as|class|extends|interface|type|async|await|yield|new|this|super|null|undefined|true|false|typeof|instanceof|in|of|void)\\b")
private val SQL_KW_PATTERN = Pattern.compile("\\b(SELECT|FROM|WHERE|INSERT|INTO|UPDATE|DELETE|JOIN|LEFT|RIGHT|INNER|OUTER|FULL|ON|GROUP|BY|ORDER|HAVING|LIMIT|OFFSET|CREATE|TABLE|ALTER|DROP|INDEX|VIEW|AS|AND|OR|NOT|IN|IS|NULL|LIKE|BETWEEN|UNION|ALL|DISTINCT|COUNT|SUM|AVG|MIN|MAX|CASE|WHEN|THEN|ELSE|END)\\b", Pattern.CASE_INSENSITIVE)
private val GENERAL_KW_PATTERN = Pattern.compile("\\b(fun|val|var|class|interface|def|function|const|let|return|if|else|for|while|try|catch|finally|throw|import|package|public|private|protected|override|async|await|new|this|super|null|true|false|SELECT|FROM|WHERE)\\b")

private val TYPES_PATTERN = Pattern.compile("\\b(String|Int|Long|Float|Double|Boolean|Char|Byte|Short|List|Map|Set|Array|Any|Unit|Nothing|Throwable|Exception|Result|StateFlow|Flow|MutableStateFlow|Promise|Observable|void|int|float|double|bool|char|number|string|boolean|any|unknown|never|dict|tuple|str)\\b")
private val FUNCTION_PATTERN = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\\s*\\()")

private fun highlightDiff(code: String, isDark: Boolean): AnnotatedString {
    val tokens = if (isDark) DarkGraventTokens else LightGraventTokens
    val builder = AnnotatedString.Builder()
    val lines = code.lines()
    lines.forEachIndexed { idx, line ->
        val start = builder.length
        builder.append(line)
        val end = builder.length
        when {
            line.startsWith("+++") || line.startsWith("---") || line.startsWith("diff --git") || line.startsWith("index ") -> {
                builder.addStyle(SpanStyle(color = tokens.textMuted, fontWeight = FontWeight.SemiBold), start, end)
            }
            line.startsWith("@@") -> {
                builder.addStyle(SpanStyle(color = tokens.infoAccent, background = tokens.infoSoftBg, fontWeight = FontWeight.Medium), start, end)
            }
            line.startsWith("+") -> {
                builder.addStyle(SpanStyle(color = tokens.successAccent, background = tokens.successSoftBg), start, end)
            }
            line.startsWith("-") -> {
                builder.addStyle(SpanStyle(color = tokens.errorAccent, background = tokens.errorSoftBg), start, end)
            }
            else -> {
                builder.addStyle(SpanStyle(color = tokens.inlineCodeText), start, end)
            }
        }
        if (idx < lines.lastIndex) builder.append('\n')
    }
    return builder.toAnnotatedString()
}

/**
 * Fast regex-based lexical tokenizer for syntax highlighting with precompiled patterns and zero allocations.
 */
private fun highlightSyntax(code: String, lang: String, isDark: Boolean = true): AnnotatedString {
    val synKeyword = if (isDark) SynKeywordDark else SynKeywordLight
    val synType = if (isDark) SynTypeDark else SynTypeLight
    val synString = if (isDark) SynStringDark else SynStringLight
    val synNumber = if (isDark) SynNumberDark else SynNumberLight
    val synComment = if (isDark) SynCommentDark else SynCommentLight
    val synAnnotation = if (isDark) SynAnnotationDark else SynAnnotationLight
    val synFunction = if (isDark) SynFunctionDark else SynFunctionLight
    val baseText = if (isDark) TextPrimaryDark else TextPrimaryLight

    val builder = AnnotatedString.Builder(code)

    data class Span(val start: Int, val end: Int, val style: SpanStyle)
    val spans = mutableListOf<Span>()

    fun addMatches(pat: Pattern, style: SpanStyle) {
        try {
            val matcher = pat.matcher(code)
            while (matcher.find()) {
                spans.add(Span(matcher.start(), matcher.end(), style))
            }
        } catch (_: Exception) {}
    }

    // 1. Comments
    val commentPat = when (lang) {
        "python", "py", "bash", "sh", "shell", "yaml", "yml", "dockerfile" -> COMMENT_PY_SH
        "sql" -> COMMENT_SQL
        else -> COMMENT_GENERIC
    }
    addMatches(commentPat, SpanStyle(color = synComment, fontStyle = FontStyle.Italic))

    // 2. Strings
    addMatches(STRING_PATTERN, SpanStyle(color = synString))

    // 3. Numbers
    addMatches(NUMBER_PATTERN, SpanStyle(color = synNumber))

    // 4. Annotations
    addMatches(ANNOTATION_PATTERN, SpanStyle(color = synAnnotation, fontWeight = FontWeight.SemiBold))

    // 5. Keywords
    val kwPat = when (lang) {
        "kotlin", "kt" -> KOTLIN_KW_PATTERN
        "python", "py" -> PYTHON_KW_PATTERN
        "javascript", "js", "typescript", "ts", "jsx", "tsx" -> JS_KW_PATTERN
        "sql" -> SQL_KW_PATTERN
        else -> GENERAL_KW_PATTERN
    }
    addMatches(kwPat, SpanStyle(color = synKeyword, fontWeight = FontWeight.Bold))

    // 6. Types / Classes
    addMatches(TYPES_PATTERN, SpanStyle(color = synType, fontWeight = FontWeight.SemiBold))

    // 7. Function invocations
    try {
        val fnMatcher = FUNCTION_PATTERN.matcher(code)
        while (fnMatcher.find()) {
            spans.add(Span(fnMatcher.start(1), fnMatcher.end(1), SpanStyle(color = synFunction)))
        }
    } catch (_: Exception) {}

    // Apply default text color
    builder.addStyle(SpanStyle(color = baseText), 0, code.length)

    // Apply all non-overlapping / prioritized spans
    spans.sortedWith(compareBy<Span> { it.start }.thenByDescending { it.end }).forEach { span ->
        val s = span.start.coerceIn(0, code.length)
        val e = span.end.coerceIn(0, code.length)
        if (s < e) {
            builder.addStyle(span.style, s, e)
        }
    }

    return builder.toAnnotatedString()
}
