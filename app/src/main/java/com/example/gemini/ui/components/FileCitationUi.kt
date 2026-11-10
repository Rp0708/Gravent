package com.example.gemini.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.theme.isAppInDarkTheme
import java.io.File

internal const val FILE_CHIP_TAG = "FILE_CHIP"
private const val FILE_CHIP_END_PAD_ID = "file_chip_end_pad"

private enum class FileVisualKind(
    val id: String,
    val icon: ImageVector,
    val accentColor: Color
) {
    KOTLIN("kt", Icons.Outlined.Code, Color(0xFFA78BFA)),
    JAVA("java", Icons.Outlined.Code, Color(0xFFF97316)),
    PYTHON("py", Icons.Outlined.Code, Color(0xFFFACC15)),
    TYPESCRIPT("ts", Icons.Outlined.Code, Color(0xFF38BDF8)),
    JAVASCRIPT("js", Icons.Outlined.Code, Color(0xFFFBBF24)),
    NATIVE_CODE("native", Icons.Outlined.Code, Color(0xFF38BDF8)),
    MARKUP("html", Icons.Outlined.Code, Color(0xFFFB7185)),
    DATA("data", Icons.Outlined.DataObject, Color(0xFF2DD4BF)),
    SHELL("sh", Icons.Outlined.Terminal, Color(0xFF4ADE80)),
    IMAGE("img", Icons.Outlined.Image, Color(0xFFC084FC)),
    DOC("doc", Icons.Outlined.Description, ClaudeTerracotta),
    GENERIC("file", Icons.Outlined.Description, Color(0xFF93C5FD));

    companion object {
        private val byId = entries.associateBy { it.id }

        fun fromId(id: String): FileVisualKind = byId[id] ?: GENERIC

        fun resolve(fileName: String): FileVisualKind {
            val lower = fileName.lowercase()
            if (lower == "dockerfile" || lower == "makefile") return SHELL
            val ext = lower.substringAfterLast('.', "")
            return when (ext) {
                "kt", "kts" -> KOTLIN
                "java" -> JAVA
                "py" -> PYTHON
                "ts", "tsx" -> TYPESCRIPT
                "js", "jsx", "mjs", "cjs" -> JAVASCRIPT
                "go", "rs", "c", "cpp", "cc", "h", "hpp", "swift", "dart" -> NATIVE_CODE
                "html", "htm", "xml", "css", "scss", "sass", "svg" -> MARKUP
                "json", "yaml", "yml", "toml", "proto" -> DATA
                "sh", "bash", "zsh", "fish" -> SHELL
                "png", "jpg", "jpeg", "webp", "gif", "bmp" -> IMAGE
                "md", "markdown", "txt", "rst" -> DOC
                else -> GENERIC
            }
        }
    }
}

private val SHARED_END_PAD_CONTENT = InlineTextContent(
    placeholder = Placeholder(
        width = 4.5.sp,
        height = 14.sp,
        placeholderVerticalAlign = PlaceholderVerticalAlign.Center
    )
) {
    Box(modifier = Modifier.fillMaxSize())
}

private val FILE_ICON_INLINE_CONTENT_MAP: Map<String, InlineTextContent> = buildMap {
    put(FILE_CHIP_END_PAD_ID, SHARED_END_PAD_CONTENT)
    FileVisualKind.entries.forEach { kind ->
        put(
            "file_icon_${kind.id}",
            InlineTextContent(
                placeholder = Placeholder(
                    width = 17.sp,
                    height = 14.sp,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 2.5.dp, end = 2.5.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = kind.icon,
                        contentDescription = null,
                        tint = kind.accentColor,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        )
    }
}

private data class ParsedFileCitation(
    val fileName: String,
    val displayLabel: String,
    val lineSuffix: String?
)

private fun cleanCitationTitle(raw: String): String {
    var s = raw.trim()
    while (true) {
        val next = s
            .removeSurrounding("**")
            .removeSurrounding("__")
            .removeSurrounding("`")
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .trim()
        if (next == s) break
        s = next
    }
    return s
}

private val LINE_FRAGMENT_REGEX = Regex("^L?(\\d+)(?:-L?(\\d+))?$", RegexOption.IGNORE_CASE)

private fun parseFileCitation(linkUrl: String, linkTitle: String?): ParsedFileCitation {
    val cleanPath = linkUrl.removePrefix("file://").substringBefore("#").trim()
    val fileName = File(cleanPath).name.ifBlank { cleanPath }

    val fragment = linkUrl.substringAfter("#", "").trim()
    val lineMatch = if (fragment.isNotEmpty()) LINE_FRAGMENT_REGEX.find(fragment) else null
    val parsedLine = if (lineMatch != null) {
        val startLine = lineMatch.groupValues[1]
        val endLine = lineMatch.groupValues[2]
        if (endLine.isNotEmpty() && endLine != startLine) "$startLine-$endLine" else startLine
    } else {
        null
    }

    val cleanedTitle = linkTitle?.let { cleanCitationTitle(it) }.orEmpty()
    val useFileName = cleanedTitle.isBlank() ||
        cleanedTitle.startsWith("file://", ignoreCase = true) ||
        cleanedTitle.startsWith("/") ||
        cleanedTitle.contains("/data/user/") ||
        cleanedTitle.contains("/data/data/")

    val label = if (useFileName) fileName else cleanedTitle
    val alreadyHasLine = parsedLine != null &&
        (label.endsWith(":$parsedLine") || label.contains("#L$parsedLine", ignoreCase = true))

    return ParsedFileCitation(
        fileName = fileName,
        displayLabel = label,
        lineSuffix = if (alreadyHasLine) null else parsedLine
    )
}

internal fun appendFileCitationChip(
    builder: AnnotatedString.Builder,
    linkUrl: String,
    linkTitle: String?,
    isDark: Boolean,
    fileLinkHandler: FileLinkHandler?,
    inlineContentMap: MutableMap<String, InlineTextContent>
) {
    val citation = parseFileCitation(linkUrl, linkTitle)
    val visual = FileVisualKind.resolve(citation.fileName)
    val iconKey = "file_icon_${visual.id}"

    inlineContentMap.putIfAbsent(iconKey, FILE_ICON_INLINE_CONTENT_MAP.getValue(iconKey))
    inlineContentMap.putIfAbsent(FILE_CHIP_END_PAD_ID, SHARED_END_PAD_CONTENT)

    builder.pushStringAnnotation(tag = FILE_CHIP_TAG, annotation = visual.id)
    builder.pushLink(
        LinkAnnotation.Clickable(
            tag = linkUrl,
            linkInteractionListener = {
                val handler = fileLinkHandler ?: ActiveFileLinkHandlerHolder.current
                handler?.onOpenFile?.invoke(linkUrl)
            }
        )
    )

    builder.appendInlineContent(iconKey, alternateText = " ")
    builder.pushStyle(
        SpanStyle(
            color = if (isDark) Color(0xFFF4F4F5) else Color(0xFF18181B),
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.SansSerif,
            fontSize = 13.sp,
            letterSpacing = (-0.1).sp
        )
    )
    builder.append(citation.displayLabel)
    builder.pop()

    if (citation.lineSuffix != null) {
        builder.pushStyle(
            SpanStyle(
                color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.5.sp
            )
        )
        builder.append(":${citation.lineSuffix}")
        builder.pop()
    }

    builder.appendInlineContent(FILE_CHIP_END_PAD_ID, alternateText = " ")
    builder.pop() // LinkAnnotation
    builder.pop() // FILE_CHIP_TAG
}

private data class FileChipDrawRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val accentColor: Color
)

private fun computeFileChipDrawRects(
    layout: TextLayoutResult,
    ranges: List<AnnotatedString.Range<String>>,
    verticalInsetPx: Float,
    minHeightPx: Float
): List<FileChipDrawRect> {
    if (ranges.isEmpty()) return emptyList()
    val textLen = layout.layoutInput.text.length
    if (textLen == 0) return emptyList()

    val rects = ArrayList<FileChipDrawRect>(ranges.size)
    for (range in ranges) {
        val start = range.start.coerceIn(0, textLen)
        val end = range.end.coerceIn(start, textLen)
        if (start >= end) continue

        val kind = FileVisualKind.fromId(range.item)
        val startLine = layout.getLineForOffset(start)
        val endLine = layout.getLineForOffset(end - 1)

        for (line in startLine..endLine) {
            val lineStart = layout.getLineStart(line)
            val lineVisibleEnd = layout.getLineEnd(line, visibleEnd = true)
            val segStart = maxOf(start, lineStart)
            val segEnd = minOf(end, lineVisibleEnd)
            if (segStart >= segEnd) continue

            if (segEnd - segStart <= 1 && layout.layoutInput.text[segStart] == '\uFFFD') continue
            val bounds = layout.getPathForRange(segStart, segEnd).getBounds()
            val left = bounds.left
            val right = bounds.right
            if (right <= left) continue

            val rawTop = layout.getLineTop(line)
            val rawBottom = layout.getLineBottom(line)
            var top = rawTop + verticalInsetPx
            var bottom = rawBottom - verticalInsetPx
            if (bottom - top < minHeightPx) {
                val mid = (rawTop + rawBottom) * 0.5f
                top = mid - minHeightPx * 0.5f
                bottom = mid + minHeightPx * 0.5f
            }

            rects.add(
                FileChipDrawRect(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    accentColor = kind.accentColor
                )
            )
        }
    }
    return rects
}

@Composable
internal fun RichMarkdownText(
    annotatedString: AnnotatedString,
    inlineContent: Map<String, InlineTextContent>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE
) {
    val chipAnnotations = remember(annotatedString) {
        annotatedString.getStringAnnotations(FILE_CHIP_TAG, 0, annotatedString.length)
    }

    if (chipAnnotations.isEmpty()) {
        Text(
            text = annotatedString,
            inlineContent = inlineContent,
            style = style,
            modifier = modifier,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines
        )
        return
    }

    val isDark = isAppInDarkTheme()
    val density = LocalDensity.current
    val verticalInsetPx = with(density) { 1.5.dp.toPx() }
    val minHeightPx = with(density) { 17.dp.toPx() }
    val cornerRadius = remember(density) {
        val r = with(density) { 5.5.dp.toPx() }
        CornerRadius(r, r)
    }
    val strokeStyle = remember(density) {
        Stroke(width = with(density) { 0.85.dp.toPx() })
    }
    val pillBaseFill = if (isDark) Color(0xFF1D1D23) else Color(0xFFF3F4F6)
    val pillBorderColor = if (isDark) Color(0xFF33333D) else Color(0xFFD4D6DF)

    var chipRects by remember(annotatedString) { mutableStateOf<List<FileChipDrawRect>>(emptyList()) }

    Text(
        text = annotatedString,
        inlineContent = inlineContent,
        style = style,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        onTextLayout = { layoutResult ->
            chipRects = computeFileChipDrawRects(
                layout = layoutResult,
                ranges = chipAnnotations,
                verticalInsetPx = verticalInsetPx,
                minHeightPx = minHeightPx
            )
        },
        modifier = modifier.drawBehind {
            for (i in chipRects.indices) {
                val rect = chipRects[i]
                val topLeft = Offset(rect.left, rect.top)
                val rectSize = Size(rect.right - rect.left, rect.bottom - rect.top)
                drawRoundRect(
                    color = pillBaseFill,
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = cornerRadius
                )
                drawRoundRect(
                    color = rect.accentColor.copy(alpha = if (isDark) 0.06f else 0.05f),
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = cornerRadius
                )
                drawRoundRect(
                    color = pillBorderColor,
                    topLeft = topLeft,
                    size = rectSize,
                    cornerRadius = cornerRadius,
                    style = strokeStyle
                )
            }
        }
    )
}
