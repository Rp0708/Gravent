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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import com.example.gemini.theme.ClaudeTerracotta

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
    color: Color = Color(0xFFF5F5F5),
    accentColor: Color = ClaudeTerracotta,
    lineColor: Color = Color(0xFF3F3F46),
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
 * File node discovered under a conversation or workspace.
 */
data class DrawerFileNode(
    val id: String,
    val name: String,
    val path: String,
    val extension: String,
    val isArtifact: Boolean = false,
    val summary: String? = null,
    val sizeBytes: Long = 0L,
    val lastModified: Long = 0L
)

/**
 * Discovers files associated with a conversation:
 * 1. Live artifacts emitted in memory for the active conversation
 * 2. Brain directory on disk (~/.gemini/antigravity-cli/brain/<convId>/) including scratch/ and .user_uploaded/
 * 3. Workspace root files or scratch directory files for general chats
 */
fun discoverConversationFiles(
    conv: Conversation,
    currentConvId: String?,
    currentChatArtifacts: List<ArtifactSnapshot> = emptyList()
): List<DrawerFileNode> {
    val results = mutableListOf<DrawerFileNode>()
    val seenPaths = mutableSetOf<String>()

    // 1. Live artifacts if this conversation is active
    if (conv.id == currentConvId && currentChatArtifacts.isNotEmpty()) {
        currentChatArtifacts.forEach { snap ->
            val uriOrPath = snap.absoluteUri.ifBlank { snap.name }
            val cleanPath = uriOrPath.removePrefix("file://")
            val f = File(cleanPath)
            val name = f.name.ifBlank { snap.name.substringAfterLast("/") }
            val ext = if (name.contains(".")) name.substringAfterLast(".").lowercase() else ""
            if (seenPaths.add(cleanPath)) {
                results.add(
                    DrawerFileNode(
                        id = cleanPath,
                        name = name,
                        path = cleanPath,
                        extension = ext,
                        isArtifact = true,
                        summary = snap.summary.ifBlank { null },
                        sizeBytes = if (f.exists()) f.length() else 0L,
                        lastModified = if (f.exists()) f.lastModified() else 0L
                    )
                )
            }
        }
    }

    // 2. Scan conversation brain directory
    try {
        val brainDir = File("/data/user/0/com.termux/files/home/.gemini/antigravity-cli/brain/${conv.id}")
        if (brainDir.exists() && brainDir.isDirectory) {
            // Root brain files (artifacts, notes, plans)
            brainDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEach { f ->
                if (seenPaths.add(f.absolutePath)) {
                    val ext = if (f.name.contains(".")) f.name.substringAfterLast(".").lowercase() else ""
                    results.add(
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
                    if (seenPaths.add(f.absolutePath)) {
                        val ext = if (f.name.contains(".")) f.name.substringAfterLast(".").lowercase() else ""
                        results.add(
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

            // User uploaded files (.user_uploaded)
            val uploadsDir = File(brainDir, ".user_uploaded")
            if (uploadsDir.exists() && uploadsDir.isDirectory) {
                uploadsDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.forEach { f ->
                    if (seenPaths.add(f.absolutePath)) {
                        val ext = if (f.name.contains(".")) f.name.substringAfterLast(".").lowercase() else ""
                        results.add(
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

    // 3. Workspace root files or scratch directory files
    try {
        if (conv.workspaceUri.isNotBlank()) {
            val wsPath = conv.workspaceUri.removePrefix("file://")
            val wsDir = File(wsPath)
            if (wsDir.exists() && wsDir.isDirectory) {
                wsDir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.take(8)?.forEach { f ->
                    if (seenPaths.add(f.absolutePath)) {
                        val ext = if (f.name.contains(".")) f.name.substringAfterLast(".").lowercase() else ""
                        results.add(
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
        } else {
            // General / Outside workspace: check scratch folder
            val generalScratch = File("/data/user/0/com.termux/files/home/.gemini/antigravity-cli/scratch")
            if (generalScratch.exists() && generalScratch.isDirectory) {
                generalScratch.listFiles()?.filter { it.isFile && !it.name.startsWith(".") }?.take(8)?.forEach { f ->
                    if (seenPaths.add(f.absolutePath)) {
                        val ext = if (f.name.contains(".")) f.name.substringAfterLast(".").lowercase() else ""
                        results.add(
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
    abstract val onClick: () -> Unit
    @Composable abstract fun IconContent()

    data class FileEntry(
        val file: DrawerFileNode,
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
            "" -> "FILE"
            else -> file.extension.uppercase().take(4)
        }
        override val badgeColor: Color = when (file.extension) {
            "md", "markdown" -> ClaudeTerracotta
            "kt", "kts", "ts", "js", "java" -> GeminiBlue
            "py" -> Color(0xFFFFB300)
            "json", "yaml", "yml" -> Color(0xFF26A69A)
            "png", "jpg", "jpeg", "webp" -> Color(0xFF66BB6A)
            "sh", "bash" -> Color(0xFF4CAF50)
            else -> Color(0xFF9E9E9E)
        }

        @Composable
        override fun IconContent() {
            val vec = when (file.extension) {
                "md", "markdown" -> Icons.Outlined.Description
                "kt", "kts", "ts", "js", "java", "py" -> Icons.Outlined.Code
                "json", "yaml", "yml" -> Icons.Outlined.DataObject
                "png", "jpg", "jpeg", "webp" -> Icons.Outlined.Image
                "sh", "bash" -> Icons.Outlined.Terminal
                else -> Icons.Outlined.Description
            }
            Icon(
                imageVector = vec,
                contentDescription = null,
                tint = if (isActive) ClaudeTerracotta else badgeColor,
                modifier = Modifier.size(15.dp)
            )
        }
    }

    data class SubagentEntry(
        val subagent: Conversation,
        override val isActive: Boolean,
        override val onClick: () -> Unit
    ) : BranchedChildEntry() {
        override val key: String = "sub_${subagent.id}"
        override val label: String = subagent.subagentRole?.takeIf { it.isNotBlank() } ?: subagent.title
        override val badge: String = "SUB"
        override val badgeColor: Color = Color(0xFFFF9800)

        @Composable
        override fun IconContent() {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.CallSplit,
                    contentDescription = null,
                    tint = if (isActive) ClaudeTerracotta else Color(0xFFFF9800),
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
    accentColor: Color = ClaudeTerracotta,
    lineColor: Color = Color(0xFF3F3F46),
    rowHeight: Dp = 34.dp,
    indent: Dp = 38.dp,
    trunk: Dp = 14.dp,
    radius: Dp = 9.dp,
    lineWidth: Dp = 1.4.dp,
    drawDurationMs: Int = 350
) {
    val entries = remember(files, subagents, activeFileId, selectedConversationId) {
        val list = mutableListOf<BranchedChildEntry>()
        files.forEach { f ->
            val isAct = activeFileId != null && (
                activeFileId == f.path ||
                activeFileId.removePrefix("file://") == f.path.removePrefix("file://")
            )
            list.add(BranchedChildEntry.FileEntry(f, isAct) { onOpenFile(f.path) })
        }
        subagents.forEach { s ->
            val isAct = selectedConversationId == s.id
            list.add(BranchedChildEntry.SubagentEntry(s, isAct) { onSelectSubagent(s.id) })
        }
        list.add(BranchedChildEntry.AddActionEntry(onAddFileClick))
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
    val endXPx = remember(indentPx) {
        indentPx - with(density) { 8.dp.toPx() }
    }

    val rowYPositions = remember(entries.size, rowHeightPx, padPx) {
        FloatArray(entries.size) { k ->
            padPx + k * rowHeightPx + (rowHeightPx / 2f)
        }
    }

    val pathMeasure = remember { PathMeasure() }
    val segmentPath = remember { Path() }

    val branchPaths = remember(entries, trunkPx, rowYPositions, rPx, endXPx) {
        entries.indices.map { k ->
            val rowY = rowYPositions[k]
            Path().apply {
                moveTo(trunkPx, (rowY - rPx).coerceAtLeast(0f))
                quadraticBezierTo(trunkPx, rowY, trunkPx + rPx, rowY)
                lineTo(endXPx, rowY)
            }
        }
    }

    val reachPaths = remember(entries, trunkPx, rowYPositions, rPx, endXPx) {
        entries.indices.map { k ->
            val rowY = rowYPositions[k]
            Path().apply {
                moveTo(trunkPx, 0f)
                lineTo(trunkPx, (rowY - rPx).coerceAtLeast(0f))
                quadraticBezierTo(trunkPx, rowY, trunkPx + rPx, rowY)
                lineTo(endXPx, rowY)
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
            entries.forEach { entry ->
                val isAct = entry.isActive
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .padding(start = indent, end = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isAct) accentColor.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { entry.onClick() }
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        entry.IconContent()
                        Text(
                            text = entry.label,
                            fontSize = 12.5.sp,
                            fontWeight = if (isAct) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isAct) accentColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (entry.badge != null) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = if (isAct) accentColor.copy(alpha = 0.20f) else entry.badgeColor.copy(alpha = 0.12f),
                            border = BorderStroke(0.6.dp, if (isAct) accentColor.copy(alpha = 0.5f) else entry.badgeColor.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = entry.badge,
                                color = if (isAct) accentColor else entry.badgeColor,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

