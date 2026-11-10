package com.example.gemini.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import kotlinx.coroutines.launch
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.DataObject
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.offset
import java.io.File
import com.example.gemini.domain.model.ArtifactSnapshot
import com.example.gemini.domain.model.Conversation
import com.example.gemini.theme.GeminiBlue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.theme.graventTokens

/**
 * React Bits ease curve: cubic-bezier(0.23, 1, 0.32, 1).
 */
val BranchedMenuEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Child item within a [BranchedMenuItem].
 */
data class BranchedMenuChild(
    val value: String,
    val label: String,
    val icon: (@Composable () -> Unit)? = null,
    val badge: String? = null
) {
    constructor(
        value: String,
        label: String,
        iconVector: ImageVector?,
        badge: String? = null
    ) : this(
        value = value,
        label = label,
        icon = iconVector?.let { vec ->
            {
                Icon(
                    imageVector = vec,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp)
                )
            }
        },
        badge = badge
    )
}

/**
 * Section or root item in [BranchedMenu].
 * If [children] is null or empty, this item behaves as a direct leaf action.
 */
data class BranchedMenuItem(
    val label: String,
    val value: String = label,
    val icon: (@Composable () -> Unit)? = null,
    val children: List<BranchedMenuChild>? = null,
    val badge: String? = null
) {
    constructor(
        label: String,
        value: String = label,
        iconVector: ImageVector?,
        children: List<BranchedMenuChild>? = null,
        badge: String? = null
    ) : this(
        label = label,
        value = value,
        icon = iconVector?.let { vec ->
            {
                Icon(
                    imageVector = vec,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        },
        children = children,
        badge = badge
    )
}

/**
 * Native Jetpack Compose implementation of React Bits BranchedMenu (reactbits.dev / DavidHDev/react-bits).
 *
 * Visual & Motion Architecture:
 * - Outer Vertical Trunk: Backbone line running down the left with a gradient fade.
 * - Gliding Trunk Marker: A luminous indicator bar ([accentColor]) that smoothly slides vertically
 *   along the trunk rail to align with the active section header.
 * - Dynamic Branch Lines: High-precision Canvas bezier curves branching outward from the trunk
 *   into each child row item.
 * - Animated "Reach" Stroke: When a child item is active, an illuminated neon stroke traces dynamically
 *   from the trunk down through the bezier curve directly into your chosen item using [PathMeasure].
 * - Collapsible Sections: Smooth fold/unfold animation matching web cubic-bezier spring physics.
 */
@Composable
fun BranchedMenu(
    items: List<BranchedMenuItem>,
    modifier: Modifier = Modifier,
    defaultOpen: Set<Int> = setOf(0),
    defaultActive: String = "",
    activeValue: String? = null,
    onSelect: ((value: String, item: Any) -> Unit)? = null,
    onToggle: ((index: Int, isOpen: Boolean) -> Unit)? = null,
    color: Color = graventTokens().textPrimary,
    accentColor: Color = graventTokens().accent,
    lineColor: Color = graventTokens().border,
    width: Dp = 260.dp,
    rowHeight: Dp = 36.dp,
    indent: Dp = 40.dp,
    trunk: Dp = 14.dp,
    radius: Dp = 10.dp,
    lineWidth: Dp = 1.5.dp,
    fontSize: TextUnit = 13.5.sp,
    drawDurationMs: Int = 400,
    foldDurationMs: Int = 300,
    showTrunkMarker: Boolean = true
) {
    var openSections by remember { mutableStateOf(defaultOpen) }
    var internalActive by remember {
        mutableStateOf(
            if (defaultActive.isNotBlank()) defaultActive
            else {
                val firstWithKids = items.indexOfFirst {
                    it.children?.isNotEmpty() == true && defaultOpen.contains(items.indexOf(it))
                }
                if (firstWithKids >= 0) {
                    items[firstWithKids].children?.firstOrNull()?.value ?: ""
                } else {
                    items.firstOrNull()?.children?.firstOrNull()?.value ?: items.firstOrNull()?.value ?: ""
                }
            }
        )
    }

    val active = activeValue ?: internalActive
    val density = LocalDensity.current

    // Store measured Y centers of section headers for the gliding trunk marker
    val headerCenterYs = remember { mutableStateMapOf<Int, Float>() }

    // Identify active section
    val activeSectionIndex = remember(items, active) {
        items.indexOfFirst { item ->
            item.children?.any { it.value == active } == true ||
                    (item.children.isNullOrEmpty() && item.value == active)
        }
    }

    val markerShown = activeSectionIndex >= 0 && (
            openSections.contains(activeSectionIndex) ||
                    items[activeSectionIndex].children.isNullOrEmpty()
            )

    val targetMarkerY = if (activeSectionIndex >= 0) {
        val centerY = headerCenterYs[activeSectionIndex] ?: with(density) { 18.dp.toPx() }
        (centerY - with(density) { 8.dp.toPx() }).coerceAtLeast(0f)
    } else 0f

    val animatedMarkerY by animateFloatAsState(
        targetValue = targetMarkerY,
        animationSpec = tween(durationMillis = 220, easing = BranchedMenuEaseOut),
        label = "bm_marker_y"
    )

    val animatedMarkerAlpha by animateFloatAsState(
        targetValue = if (markerShown) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "bm_marker_alpha"
    )

    Box(
        modifier = modifier
            .width(width)
            .drawBehind {
                val totalH = size.height
                // 1. Draw outer vertical trunk line with gradient fade
                val trunkBrush = Brush.verticalGradient(
                    0.0f to lineColor,
                    0.55f to lineColor,
                    1.0f to Color.Transparent
                )
                drawLine(
                    brush = trunkBrush,
                    start = Offset(x = 1.dp.toPx(), y = 8.dp.toPx()),
                    end = Offset(x = 1.dp.toPx(), y = totalH),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 2. Draw gliding active marker on the trunk rail
                if (showTrunkMarker && animatedMarkerAlpha > 0.01f) {
                    val markerLen = 16.dp.toPx()
                    // Luminous ambient glow
                    drawLine(
                        color = accentColor.copy(alpha = 0.35f * animatedMarkerAlpha),
                        start = Offset(x = 1.dp.toPx(), y = animatedMarkerY),
                        end = Offset(x = 1.dp.toPx(), y = animatedMarkerY + markerLen),
                        strokeWidth = 4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    // High-visibility core marker
                    drawLine(
                        color = accentColor.copy(alpha = animatedMarkerAlpha),
                        start = Offset(x = 1.dp.toPx(), y = animatedMarkerY),
                        end = Offset(x = 1.dp.toPx(), y = animatedMarkerY + markerLen),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
            .padding(start = trunk)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            items.forEachIndexed { index, item ->
                val kids = item.children
                val isOpen = kids != null && openSections.contains(index)
                val isLeaf = kids.isNullOrEmpty()
                val isLeafActive = isLeaf && item.value == active

                val headerColor by animateColorAsState(
                    targetValue = if (isOpen || isLeafActive) color else color.copy(alpha = 0.55f),
                    animationSpec = tween(200),
                    label = "bm_head_color_$index"
                )

                val chevronRotation by animateFloatAsState(
                    targetValue = if (isOpen) 90f else 0f,
                    animationSpec = tween(durationMillis = 200, easing = BranchedMenuEaseOut),
                    label = "bm_chevron_$index"
                )

                // Section Header Button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords ->
                            val posInParent = coords.positionInParent()
                            headerCenterYs[index] = posInParent.y + (coords.size.height / 2f)
                        }
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            if (isLeaf) {
                                internalActive = item.value
                                onSelect?.invoke(item.value, item)
                            } else {
                                val nextOpen = if (isOpen) openSections - index else openSections + index
                                openSections = nextOpen
                                onToggle?.invoke(index, !isOpen)
                            }
                        }
                        .padding(vertical = 9.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        if (item.icon != null) {
                            CompositionLocalProvider(LocalContentColor provides headerColor) {
                                Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                    item.icon.invoke()
                                }
                            }
                        }
                        Text(
                            text = item.label,
                            color = headerColor,
                            fontSize = (fontSize.value + 1).sp,
                            fontWeight = if (isLeafActive) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.badge != null) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isLeafActive) accentColor.copy(alpha = 0.18f) else color.copy(alpha = 0.10f)
                            ) {
                                Text(
                                    text = item.badge,
                                    color = if (isLeafActive) accentColor else color.copy(alpha = 0.85f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }

                    if (!isLeaf) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (isOpen) "Collapse" else "Expand",
                            tint = headerColor.copy(alpha = 0.45f),
                            modifier = Modifier
                                .size(16.dp)
                                .graphicsLayer { rotationZ = chevronRotation }
                        )
                    }
                }

                // Collapsible Section Body
                if (kids != null) {
                    AnimatedVisibility(
                        visible = isOpen,
                        enter = expandVertically(
                            animationSpec = tween(durationMillis = foldDurationMs, easing = BranchedMenuEaseOut)
                        ) + fadeIn(animationSpec = tween(200)),
                        exit = shrinkVertically(
                            animationSpec = tween(durationMillis = foldDurationMs, easing = BranchedMenuEaseOut)
                        ) + fadeOut(animationSpec = tween(150))
                    ) {
                        BranchedMenuSectionTree(
                            kids = kids,
                            active = active,
                            onSelect = { kidValue, kid ->
                                internalActive = kidValue
                                onSelect?.invoke(kidValue, kid)
                            },
                            color = color,
                            accentColor = accentColor,
                            lineColor = lineColor,
                            rowHeight = rowHeight,
                            indent = indent,
                            trunk = trunk,
                            radius = radius,
                            lineWidth = lineWidth,
                            fontSize = fontSize,
                            drawDurationMs = drawDurationMs
                        )
                    }
                }
            }
        }
    }
}

/**
 * Inner tree renderer for children of an open section.
 * Renders the canvas branch curves, animated reach stroke, and interactive child item buttons.
 */
@Composable
fun BranchedMenuSectionTree(
    kids: List<BranchedMenuChild>,
    active: String,
    onSelect: (String, BranchedMenuChild) -> Unit,
    color: Color,
    accentColor: Color,
    lineColor: Color,
    rowHeight: Dp,
    indent: Dp,
    trunk: Dp,
    radius: Dp,
    lineWidth: Dp,
    fontSize: TextUnit,
    drawDurationMs: Int
) {
    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val trunkPx = with(density) { trunk.toPx() }
    val indentPx = with(density) { indent.toPx() }
    val radiusPx = with(density) { radius.toPx() }
    val lineWidthPx = with(density) { lineWidth.toPx() }
    val padPx = with(density) { 6.dp.toPx() }

    // Corner radius for the branch curve: r = min(radius, rowHeight / 2 - 2)
    val rPx = remember(radiusPx, rowHeightPx) {
        minOf(radiusPx, (rowHeightPx / 2f) - 2f)
    }
    val endXPx = remember(indentPx) {
        indentPx - with(density) { 8.dp.toPx() }
    }

    // Precalculate row vertical centers
    val rowYPositions = remember(kids.size, rowHeightPx, padPx) {
        FloatArray(kids.size) { k ->
            padPx + k * rowHeightPx + (rowHeightPx / 2f)
        }
    }

    // Reusable path measure and destination segment to avoid allocations on every frame
    val pathMeasure = remember { PathMeasure() }
    val segmentPath = remember { Path() }

    // Reusable branch & reach paths
    val branchPaths = remember(kids, trunkPx, rowYPositions, rPx, endXPx) {
        kids.indices.map { k ->
            val rowY = rowYPositions[k]
            Path().apply {
                moveTo(trunkPx, (rowY - rPx).coerceAtLeast(0f))
                quadraticBezierTo(trunkPx, rowY, trunkPx + rPx, rowY)
                lineTo(endXPx, rowY)
            }
        }
    }
    val reachPaths = remember(kids, trunkPx, rowYPositions, rPx, endXPx) {
        kids.indices.map { k ->
            val rowY = rowYPositions[k]
            Path().apply {
                moveTo(trunkPx, 0f)
                lineTo(trunkPx, (rowY - rPx).coerceAtLeast(0f))
                quadraticBezierTo(trunkPx, rowY, trunkPx + rPx, rowY)
                lineTo(endXPx, rowY)
            }
        }
    }

    // Reach progress animatables
    val reachAnimatables = remember(kids) {
        kids.associate { it.value to Animatable(if (it.value == active) 1f else 0f) }
    }

    LaunchedEffect(active, kids) {
        kids.forEach { kid ->
            val anim = reachAnimatables[kid.value] ?: return@forEach
            val target = if (kid.value == active) 1f else 0f
            if (anim.targetValue != target) {
                launch {
                    anim.animateTo(
                        targetValue = target,
                        animationSpec = tween(
                            durationMillis = drawDurationMs,
                            easing = BranchedMenuEaseOut
                        )
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        // 1. Vector lines Canvas: base vertical stem, curved branch paths, and animated reach strokes
        Canvas(
            modifier = Modifier.matchParentSize()
        ) {
            if (kids.isEmpty()) return@Canvas

            // Base vertical stem: from (trunk, 0) down to (trunk, rowY(last) - r)
            val lastRowY = rowYPositions.lastOrNull() ?: 0f
            val stemEndY = (lastRowY - rPx).coerceAtLeast(0f)
            drawLine(
                color = lineColor,
                start = Offset(x = trunkPx, y = 0f),
                end = Offset(x = trunkPx, y = stemEndY),
                strokeWidth = lineWidthPx,
                cap = StrokeCap.Round
            )

            // Draw curved branch paths for all children
            branchPaths.forEach { branchPath ->
                drawPath(
                    path = branchPath,
                    color = lineColor,
                    style = Stroke(
                        width = lineWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Draw animated illuminated "reach" stroke for active picks
            kids.indices.forEach { k ->
                val kid = kids[k]
                val progress = reachAnimatables[kid.value]?.value ?: 0f
                if (progress > 0.005f) {
                    val reachPath = reachPaths[k]
                    segmentPath.reset()
                    pathMeasure.setPath(reachPath, false)
                    val totalLen = pathMeasure.length
                    if (totalLen > 0f) {
                        pathMeasure.getSegment(0f, totalLen * progress, segmentPath, true)

                        // Ambient glow halo
                        drawPath(
                            path = segmentPath,
                            color = accentColor.copy(alpha = 0.35f * progress),
                            style = Stroke(
                                width = lineWidthPx * 3.2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        // Crisp laser core
                        drawPath(
                            path = segmentPath,
                            color = accentColor.copy(alpha = progress),
                            style = Stroke(
                                width = lineWidthPx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }

        // 2. Child row buttons aligned at [indent]
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            kids.forEachIndexed { index, kid ->
                val isKidActive = kid.value == active
                val kidTextColor by animateColorAsState(
                    targetValue = if (isKidActive) accentColor else color.copy(alpha = 0.55f),
                    animationSpec = tween(200),
                    label = "bm_kid_text_color_${kid.value}"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(start = indent)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onSelect(kid.value, kid) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (kid.icon != null) {
                        CompositionLocalProvider(LocalContentColor provides kidTextColor) {
                            Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                                kid.icon.invoke()
                            }
                        }
                    }

                    Text(
                        text = kid.label,
                        color = kidTextColor,
                        fontSize = fontSize,
                        fontWeight = if (isKidActive) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (kid.badge != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isKidActive) accentColor.copy(alpha = 0.20f) else color.copy(alpha = 0.10f)
                        ) {
                            Text(
                                text = kid.badge,
                                color = if (isKidActive) accentColor else color.copy(alpha = 0.75f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Standard preset items matching React Bits official demo.
 */
val DEFAULT_BRANCHED_MENU_ITEMS: List<BranchedMenuItem> = listOf(
    BranchedMenuItem(
        label = "Getting started",
        children = listOf(
            BranchedMenuChild("install", "Installation", Icons.Outlined.Download),
            BranchedMenuChild("quick", "Quick start", Icons.Outlined.RocketLaunch),
            BranchedMenuChild("config", "Configuration", Icons.Outlined.Settings),
            BranchedMenuChild("theming", "Theming", Icons.Outlined.Palette)
        )
    ),
    BranchedMenuItem(
        label = "Components",
        children = listOf(
            BranchedMenuChild("buttons", "Buttons", Icons.Outlined.SmartButton),
            BranchedMenuChild("typography", "Typography", Icons.Outlined.TextFields),
            BranchedMenuChild("overlays", "Overlays", Icons.Outlined.Layers),
            BranchedMenuChild("toasts", "Toasts", Icons.Outlined.Notifications)
        )
    )
)

/**
 * Gravent native system navigation presets for sidebar / hub navigation.
 */
val GRAVENT_HUB_MENU_ITEMS: List<BranchedMenuItem> = listOf(
    BranchedMenuItem(
        label = "Agents & Engine",
        iconVector = Icons.Outlined.AutoAwesome,
        children = listOf(
            BranchedMenuChild("agent_agy", "Antigravity Core", Icons.Outlined.AutoAwesome, "SUPERVISED"),
            BranchedMenuChild("agent_claude", "Claude Code", Icons.Outlined.Code),
            BranchedMenuChild("agent_models", "Model Switcher", Icons.Outlined.Tune)
        )
    ),
    BranchedMenuItem(
        label = "Workspaces & Code",
        iconVector = Icons.Outlined.FolderOpen,
        children = listOf(
            BranchedMenuChild("ws_browse", "File Explorer", Icons.Outlined.FolderOpen),
            BranchedMenuChild("ws_terminal", "Local Terminal", Icons.Outlined.Terminal),
            BranchedMenuChild("ws_mcp", "MCP Servers", Icons.Outlined.Extension)
        )
    ),
    BranchedMenuItem(
        label = "Settings & Accounts",
        iconVector = Icons.Outlined.Settings,
        children = listOf(
            BranchedMenuChild("act_accounts", "Multi-Account Switch", Icons.Outlined.AccountCircle),
            BranchedMenuChild("act_settings", "Preferences", Icons.Outlined.Settings)
        )
    )
)

/**
 * Standard ignored directory names in workspace file tree.
 */
val IGNORED_DIRECTORY_NAMES = setOf(
    ".git", "node_modules", "build", ".gradle", ".idea", ".dart_tool",
    "__pycache__", ".next", ".nuxt", "dist", ".cache", ".turbo", ".gemini", ".git_hooks"
)

/**
 * File or directory node discovered under a conversation or workspace.
 */
data class DrawerFileNode(
    val id: String,
    val name: String,
    val path: String,
    val extension: String,
    val isDirectory: Boolean = false,
    val isProjectRoot: Boolean = false,
    val isArtifact: Boolean = false,
    val summary: String? = null,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L,
    val children: List<DrawerFileNode> = emptyList()
)

/**
 * Recursively scans directories up to [maxDepth] to build the project directory tree.
 */
fun scanDirectoryRecursive(
    dir: File,
    currentDepth: Int = 1,
    maxDepth: Int = 8
): List<DrawerFileNode> {
    if (!dir.exists() || !dir.isDirectory || currentDepth > maxDepth) return emptyList()
    val entries = dir.listFiles() ?: return emptyList()
    val dirs = mutableListOf<DrawerFileNode>()
    val files = mutableListOf<DrawerFileNode>()

    entries.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() })).forEach { entry ->
        if (entry.name.startsWith(".") && entry.name != ".gitignore" && entry.name != ".env") return@forEach
        if (entry.isDirectory) {
            if (entry.name !in IGNORED_DIRECTORY_NAMES) {
                val subChildren = if (currentDepth < maxDepth) {
                    scanDirectoryRecursive(entry, currentDepth + 1, maxDepth)
                } else emptyList()
                dirs.add(
                    DrawerFileNode(
                        id = "dir_${entry.absolutePath}",
                        name = entry.name,
                        path = entry.absolutePath,
                        extension = "",
                        isDirectory = true,
                        isProjectRoot = false,
                        children = subChildren,
                        lastModified = entry.lastModified()
                    )
                )
            }
        } else if (entry.isFile) {
            val ext = if (entry.name.contains(".")) entry.name.substringAfterLast(".").lowercase() else ""
            files.add(
                DrawerFileNode(
                    id = entry.absolutePath,
                    name = entry.name,
                    path = entry.absolutePath,
                    extension = ext,
                    isDirectory = false,
                    isProjectRoot = false,
                    sizeBytes = entry.length(),
                    lastModified = entry.lastModified()
                )
            )
        }
    }
    return dirs.take(24) + files.take(60)
}

/**
 * Discovers files associated with a conversation:
 * 1. Project / Workspace root directory container and its nested folders & files
 * 2. Live memory artifacts emitted for the active conversation
 * 3. Brain disk directory files (~/.gemini/antigravity-cli/brain/<convId>/)
 */
fun discoverConversationFiles(
    conv: Conversation,
    currentConvId: String?,
    currentChatArtifacts: List<ArtifactSnapshot> = emptyList()
): List<DrawerFileNode> {
    val results = mutableListOf<DrawerFileNode>()
    val seenPaths = mutableSetOf<String>()

    // 1. Workspace / Project root directory representation
    try {
        val candidateDir: File? = if (conv.workspaceUri.isNotBlank()) {
            val wsPath = conv.workspaceUri.removePrefix("file://").trimEnd('/')
            File(wsPath).takeIf { it.exists() && it.isDirectory }
        } else {
            // If workspace is not explicitly set, auto-discover project inside scratch (e.g. gravent)
            val scratchDir = File("/data/user/0/com.termux/files/home/.gemini/antigravity-cli/scratch")
            val graventDir = File(scratchDir, "gravent")
            if (graventDir.exists() && graventDir.isDirectory) {
                graventDir
            } else {
                scratchDir.listFiles()?.firstOrNull { it.isDirectory && !it.name.startsWith(".") }
            }
        }

        if (candidateDir != null) {
            val projectName = candidateDir.name.ifBlank { "Project" }
            val projectChildren = scanDirectoryRecursive(candidateDir, currentDepth = 1, maxDepth = 8)
            results.add(
                DrawerFileNode(
                    id = "proj_root_${candidateDir.absolutePath}",
                    name = projectName,
                    path = candidateDir.absolutePath,
                    extension = "",
                    isDirectory = true,
                    isProjectRoot = true,
                    children = projectChildren,
                    lastModified = candidateDir.lastModified()
                )
            )
        }
    } catch (_: Exception) {}

    // 2. Chat brain artifacts and live notes (excluding raw media attachments)
    val brainFiles = mutableListOf<DrawerFileNode>()
    val ignoredMediaExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "ico", "svg", "mp4", "mp3")

    // Live memory artifacts if this conversation is active
    if (conv.id == currentConvId && currentChatArtifacts.isNotEmpty()) {
        currentChatArtifacts.forEach { snap ->
            val uriOrPath = snap.absoluteUri.ifBlank { snap.name }
            val cleanPath = uriOrPath.removePrefix("file://")
            val f = File(cleanPath)
            if (!f.exists() || !f.isFile) return@forEach
            val name = f.name.ifBlank { snap.name.substringAfterLast("/") }
            val ext = if (name.contains(".")) name.substringAfterLast(".").lowercase() else ""
            if (ext in ignoredMediaExtensions || cleanPath.contains(".user_uploaded")) return@forEach
            if (seenPaths.add(cleanPath)) {
                brainFiles.add(
                    DrawerFileNode(
                        id = cleanPath,
                        name = name,
                        path = cleanPath,
                        extension = ext,
                        isArtifact = true,
                        summary = snap.summary.ifBlank { null },
                        sizeBytes = f.length(),
                        lastModified = f.lastModified()
                    )
                )
            }
        }
    }

    // Disk brain files (~/.gemini/antigravity-cli/brain/<convId>/)
    try {
        val brainDir = File("/data/user/0/com.termux/files/home/.gemini/antigravity-cli/brain/${conv.id}")
        if (brainDir.exists() && brainDir.isDirectory) {
            // Root brain files (artifacts, notes, plans) - ignore media and internal metadata json
            brainDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") && !it.name.endsWith(".metadata.json") }?.forEach { f ->
                val ext = if (f.name.contains(".")) f.name.substringAfterLast(".").lowercase() else ""
                if (ext !in ignoredMediaExtensions && seenPaths.add(f.absolutePath)) {
                    brainFiles.add(
                        DrawerFileNode(
                            id = f.absolutePath,
                            name = f.name,
                            path = f.absolutePath,
                            extension = ext,
                            isArtifact = true,
                            sizeBytes = f.length(),
                            lastModified = f.lastModified()
                        )
                    )
                }
            }

            // Scratch directory in brain
            val scratchDir = File(brainDir, "scratch")
            if (scratchDir.exists() && scratchDir.isDirectory) {
                scratchDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEach { f ->
                    val ext = if (f.name.contains(".")) f.name.substringAfterLast(".").lowercase() else ""
                    if (ext !in ignoredMediaExtensions && seenPaths.add(f.absolutePath)) {
                        brainFiles.add(
                            DrawerFileNode(
                                id = f.absolutePath,
                                name = f.name,
                                path = f.absolutePath,
                                extension = ext,
                                isArtifact = false,
                                sizeBytes = f.length(),
                                lastModified = f.lastModified()
                            )
                        )
                    }
                }
            }
        }
    } catch (_: Exception) {}

    // Append brain artifacts alongside project files
    results.addAll(brainFiles)

    return results
}

/**
 * Creates a new file / note for a conversation directly on disk.
 */
fun createFileForConversation(
    conv: Conversation,
    fileName: String,
    content: String = ""
): File? {
    return try {
        val cleanName = fileName.trim().ifBlank { "notes.md" }
        val targetDir = if (conv.workspaceUri.isNotBlank()) {
            File(conv.workspaceUri.removePrefix("file://"))
        } else {
            val brainDir = File("/data/user/0/com.termux/files/home/.gemini/antigravity-cli/brain/${conv.id}/scratch")
            if (!brainDir.exists()) brainDir.mkdirs()
            brainDir
        }
        if (!targetDir.exists()) targetDir.mkdirs()
        val targetFile = File(targetDir, cleanName)
        if (!targetFile.exists()) {
            val defaultContent = if (content.isNotBlank()) content else {
                if (cleanName.endsWith(".md", ignoreCase = true)) {
                    "# ${cleanName.substringBeforeLast(".")}\n\n*Created for ${conv.title.ifBlank { "Conversation" }}*\n\n"
                } else {
                    "// ${cleanName}\n"
                }
            }
            targetFile.writeText(defaultContent)
        }
        targetFile
    } catch (e: Exception) {
        null
    }
}

/**
 * Unified entry in the [BranchedChildrenTree].
 */
sealed class BranchedChildEntry {
    abstract val key: String
    abstract val label: String
    abstract val badge: String?
    abstract val badgeColor: Color
    abstract val isActive: Boolean
    abstract val depth: Int
    abstract val onClick: () -> Unit
    @Composable abstract fun IconContent()

    data class FileEntry(
        val file: DrawerFileNode,
        override val depth: Int = 0,
        override val isActive: Boolean,
        override val onClick: () -> Unit
    ) : BranchedChildEntry() {
        override val key: String = "file_${file.path}"
        override val label: String = file.name
        override val badge: String = when (file.extension) {
            "md", "markdown" -> "MD"
            "kt", "kts" -> "KT"
            "java" -> "JAVA"
            "py" -> "PY"
            "json" -> "JSON"
            "yaml", "yml" -> "YAML"
            "png", "jpg", "jpeg", "webp" -> "IMG"
            "sh", "bash" -> "SH"
            "ts" -> "TS"
            "js" -> "JS"
            "html", "htm" -> "HTML"
            "css", "scss", "sass" -> "CSS"
            "" -> "FILE"
            else -> file.extension.uppercase().take(4)
        }
        override val badgeColor: Color = when (file.extension) {
            "md", "markdown" -> Color(0xFF9E9E9E)
            "kt", "kts", "ts", "js", "java" -> GeminiBlue
            "html", "htm" -> Color(0xFFE44D26)
            "css", "scss", "sass" -> Color(0xFF264DE4)
            "py" -> Color(0xFFFFB300)
            "json", "yaml", "yml" -> Color(0xFF26A69A)
            "png", "jpg", "jpeg", "webp" -> Color(0xFF66BB6A)
            "sh", "bash" -> Color(0xFF4CAF50)
            else -> Color(0xFF9E9E9E)
        }

        @Composable
        override fun IconContent() {
            val tokens = graventTokens()
            val vec = when (file.extension) {
                "md", "markdown" -> Icons.Outlined.Description
                "kt", "kts", "ts", "js", "java", "py", "html", "htm", "css", "scss" -> Icons.Outlined.Code
                "json", "yaml", "yml" -> Icons.Outlined.DataObject
                "png", "jpg", "jpeg", "webp" -> Icons.Outlined.Image
                "sh", "bash" -> Icons.Outlined.Terminal
                else -> Icons.Outlined.Description
            }
            Icon(
                imageVector = vec,
                contentDescription = null,
                tint = if (isActive) tokens.accent else resolveUnifiedFileAccent(file.name, tokens.isDark),
                modifier = Modifier.size(15.dp)
            )
        }
    }

    data class DirectoryEntry(
        val folder: DrawerFileNode,
        val isExpanded: Boolean,
        override val depth: Int = 0,
        override val isActive: Boolean = false,
        override val onClick: () -> Unit
    ) : BranchedChildEntry() {
        override val key: String = "dir_${folder.path}"
        override val label: String = folder.name
        override val badge: String = if (folder.isProjectRoot) "PROJECT" else "DIR"
        override val badgeColor: Color = GeminiBlue

        @Composable
        override fun IconContent() {
            val tokens = graventTokens()
            Icon(
                imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
                contentDescription = null,
                tint = if (folder.isProjectRoot) tokens.accent else GeminiBlue,
                modifier = Modifier.size(15.dp)
            )
        }
    }

    data class SubagentEntry(
        val subagent: Conversation,
        override val depth: Int = 0,
        override val isActive: Boolean,
        override val onClick: () -> Unit
    ) : BranchedChildEntry() {
        override val key: String = "sub_${subagent.id}"
        override val label: String = subagent.subagentRole?.takeIf { it.isNotBlank() } ?: subagent.title
        override val badge: String = "SUB"
        override val badgeColor: Color = Color(0xFFFF9800)

        @Composable
        override fun IconContent() {
            val tokens = graventTokens()
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CallSplit,
                    contentDescription = null,
                    tint = if (isActive) tokens.accent else Color(0xFFFF9800),
                    modifier = Modifier.size(15.dp)
                )
                if (subagent.isRunning) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 1.dp, y = (-1).dp)
                            .background(Color(0xFF4CAF50), CircleShape)
                    )
                }
            }
        }
    }

    data class AddActionEntry(
        override val depth: Int = 0,
        override val onClick: () -> Unit
    ) : BranchedChildEntry() {
        override val key: String = "action_add_file"
        override val label: String = "New Note / File..."
        override val badge: String = "+"
        override val badgeColor: Color = Color(0xFF888888)
        override val isActive: Boolean = false

        @Composable
        override fun IconContent() {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = Color(0xFF888888),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/**
 * Renders the React Bits branched tree for children of a conversation (files + subagents).
 * Displays bezier branch connectors from the trunk rail into each item, with animated reach strokes.
 */
@Composable
fun BranchedChildrenTree(
    files: List<DrawerFileNode>,
    subagents: List<Conversation>,
    activeFileId: String? = null,
    selectedConversationId: String? = null,
    onOpenFile: (String) -> Unit,
    onSelectSubagent: (String) -> Unit,
    onAddFileClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = graventTokens().accent,
    lineColor: Color = graventTokens().border,
    rowHeight: Dp = 31.dp,
    indent: Dp = 34.dp,
    trunk: Dp = 14.dp,
    radius: Dp = 8.dp,
    lineWidth: Dp = 1.3.dp,
    drawDurationMs: Int = 350
) {
    // Project root nodes are expanded by default to display project files immediately
    var expandedFolders by remember(files) {
        val rootPaths = files.filter { it.isProjectRoot }.map { it.path }.toSet()
        mutableStateOf(rootPaths)
    }

    // Dynamic on-demand folder children cache for deep sub-branches
    val dynamicFolders = remember { mutableStateMapOf<String, List<DrawerFileNode>>() }

    fun flattenNodes(nodes: List<DrawerFileNode>, depth: Int): List<BranchedChildEntry> {
        val list = mutableListOf<BranchedChildEntry>()
        for (node in nodes) {
            if (node.isDirectory) {
                val isExp = expandedFolders.contains(node.path)
                val effectiveChildren = if (node.children.isNotEmpty()) {
                    node.children
                } else {
                    dynamicFolders[node.path] ?: emptyList()
                }
                list.add(
                    BranchedChildEntry.DirectoryEntry(
                        folder = node,
                        isExpanded = isExp,
                        depth = depth,
                        onClick = {
                            if (isExp) {
                                expandedFolders = expandedFolders - node.path
                            } else {
                                if (node.children.isEmpty() && !dynamicFolders.containsKey(node.path)) {
                                    val diskDir = File(node.path)
                                    if (diskDir.exists() && diskDir.isDirectory) {
                                        dynamicFolders[node.path] = scanDirectoryRecursive(diskDir, currentDepth = 1, maxDepth = 6)
                                    }
                                }
                                expandedFolders = expandedFolders + node.path
                            }
                        }
                    )
                )
                if (isExp && effectiveChildren.isNotEmpty()) {
                    list.addAll(flattenNodes(effectiveChildren, depth + 1))
                }
            } else {
                val isAct = activeFileId != null && (
                    activeFileId == node.path ||
                    activeFileId.removePrefix("file://") == node.path.removePrefix("file://")
                )
                list.add(
                    BranchedChildEntry.FileEntry(
                        file = node,
                        depth = depth,
                        isActive = isAct,
                        onClick = { onOpenFile(node.path) }
                    )
                )
            }
        }
        return list
    }

    val entries = remember(files, subagents, activeFileId, selectedConversationId, expandedFolders, dynamicFolders.size) {
        val list = mutableListOf<BranchedChildEntry>()
        list.addAll(flattenNodes(files, depth = 0))
        subagents.forEach { s ->
            val isAct = selectedConversationId == s.id
            list.add(BranchedChildEntry.SubagentEntry(s, depth = 0, isAct) { onSelectSubagent(s.id) })
        }
        list.add(BranchedChildEntry.AddActionEntry(depth = 0, onAddFileClick))
        list
    }

    val density = LocalDensity.current
    val rowHeightPx = with(density) { rowHeight.toPx() }
    val trunkPx = with(density) { trunk.toPx() }
    val indentPx = with(density) { indent.toPx() }
    val radiusPx = with(density) { radius.toPx() }
    val lineWidthPx = with(density) { lineWidth.toPx() }
    val padPx = with(density) { 4.dp.toPx() }

    val rPx = remember(radiusPx, rowHeightPx) {
        minOf(radiusPx, (rowHeightPx / 2f) - 2f)
    }

    val rowYPositions = remember(entries.size, rowHeightPx, padPx) {
        FloatArray(entries.size) { k ->
            padPx + k * rowHeightPx + (rowHeightPx / 2f)
        }
    }

    val pathMeasure = remember { PathMeasure() }
    val segmentPath = remember { Path() }

    val branchPaths = remember(entries, trunkPx, rowYPositions, rPx, indentPx) {
        entries.indices.map { k ->
            val rowY = rowYPositions[k]
            val depthIndentPx = with(density) { minOf((entries[k].depth * 8).dp, 24.dp).toPx() }
            val entryIndent = indentPx + depthIndentPx
            val itemEndXPx = entryIndent - with(density) { 5.dp.toPx() }
            Path().apply {
                moveTo(trunkPx, (rowY - rPx).coerceAtLeast(0f))
                quadraticBezierTo(trunkPx, rowY, trunkPx + rPx, rowY)
                lineTo(itemEndXPx, rowY)
            }
        }
    }

    val reachPaths = remember(entries, trunkPx, rowYPositions, rPx, indentPx) {
        entries.indices.map { k ->
            val rowY = rowYPositions[k]
            val depthIndentPx = with(density) { minOf((entries[k].depth * 8).dp, 24.dp).toPx() }
            val entryIndent = indentPx + depthIndentPx
            val itemEndXPx = entryIndent - with(density) { 5.dp.toPx() }
            Path().apply {
                moveTo(trunkPx, 0f)
                lineTo(trunkPx, (rowY - rPx).coerceAtLeast(0f))
                quadraticBezierTo(trunkPx, rowY, trunkPx + rPx, rowY)
                lineTo(itemEndXPx, rowY)
            }
        }
    }

    val reachAnimatables = remember(entries) {
        entries.associate { it.key to Animatable(if (it.isActive) 1f else 0f) }
    }

    LaunchedEffect(entries) {
        entries.forEach { entry ->
            val anim = reachAnimatables[entry.key] ?: return@forEach
            val target = if (entry.isActive) 1f else 0f
            if (anim.targetValue != target) {
                launch {
                    anim.animateTo(
                        targetValue = target,
                        animationSpec = tween(
                            durationMillis = drawDurationMs,
                            easing = BranchedMenuEaseOut
                        )
                    )
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            if (entries.isEmpty()) return@Canvas
            val lastRowY = rowYPositions.lastOrNull() ?: 0f
            val stemEndY = (lastRowY - rPx).coerceAtLeast(0f)

            // Vertical stem line from trunk top down to last branch curve
            drawLine(
                color = lineColor,
                start = Offset(x = trunkPx, y = 0f),
                end = Offset(x = trunkPx, y = stemEndY),
                strokeWidth = lineWidthPx,
                cap = StrokeCap.Round
            )

            // Curved branch connectors
            branchPaths.forEach { bPath ->
                drawPath(
                    path = bPath,
                    color = lineColor,
                    style = Stroke(
                        width = lineWidthPx,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            // Neon reach laser stroke for active item
            entries.indices.forEach { k ->
                val entry = entries[k]
                val progress = reachAnimatables[entry.key]?.value ?: 0f
                if (progress > 0.005f) {
                    val reachPath = reachPaths[k]
                    segmentPath.reset()
                    pathMeasure.setPath(reachPath, false)
                    val totalLen = pathMeasure.length
                    if (totalLen > 0f) {
                        pathMeasure.getSegment(0f, totalLen * progress, segmentPath, true)
                        // Ambient glow halo
                        drawPath(
                            path = segmentPath,
                            color = accentColor.copy(alpha = 0.35f * progress),
                            style = Stroke(
                                width = lineWidthPx * 3.2f,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                        // Core laser
                        drawPath(
                            path = segmentPath,
                            color = accentColor.copy(alpha = progress),
                            style = Stroke(
                                width = lineWidthPx,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            val tokens = graventTokens()
            entries.forEach { entry ->
                val isAct = entry.isActive
                val effectiveBadgeColor = when (entry) {
                    is BranchedChildEntry.FileEntry -> resolveUnifiedFileAccent(entry.file.name, tokens.isDark)
                    is BranchedChildEntry.DirectoryEntry -> if (entry.folder.isProjectRoot) accentColor else GeminiBlue
                    else -> entry.badgeColor
                }
                val depthIndent = minOf((entry.depth * 8).dp, 24.dp)
                val startPadding = indent + depthIndent
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(start = startPadding, end = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isAct) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { entry.onClick() }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        // Column 1: Expand/collapse chevron or fixed column spacer
                        if (entry is BranchedChildEntry.DirectoryEntry) {
                            Icon(
                                imageVector = if (entry.isExpanded) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                modifier = Modifier.size(13.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.width(13.dp))
                        }

                        // Column 2: Node Icon
                        entry.IconContent()

                        // Column 3: Label Text
                        Text(
                            text = entry.label,
                            fontSize = 12.sp,
                            fontWeight = if (isAct || (entry is BranchedChildEntry.DirectoryEntry && entry.folder.isProjectRoot)) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isAct) accentColor else if (entry is BranchedChildEntry.DirectoryEntry && entry.folder.isProjectRoot) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Column 4: Badge Tag
                    val badgeText = entry.badge
                    if (badgeText != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isAct) accentColor.copy(alpha = 0.20f) else effectiveBadgeColor.copy(alpha = 0.12f),
                            border = BorderStroke(0.6.dp, if (isAct) accentColor.copy(alpha = 0.5f) else effectiveBadgeColor.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = badgeText,
                                color = if (isAct) accentColor else effectiveBadgeColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

