package com.example.gemini.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.remote.PromptExpansion
import com.example.gemini.theme.ClaudeTerracotta
import com.example.gemini.ui.components.formatTelemetryShortTime
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

@Immutable
data class PromptAnchor(
    val feedIndex: Int,
    val messageId: String,
    val ordinal: Int,
    val preview: String,
    val timestampMs: Long
)

fun extractPromptAnchors(feedItems: List<ChatFeedItem>): List<PromptAnchor> {
    if (feedItems.isEmpty()) return emptyList()
    val anchors = ArrayList<PromptAnchor>(16)
    var ordinal = 1
    feedItems.forEachIndexed { idx, item ->
        if (item is ChatFeedItem.User) {
            val raw = PromptExpansion.collapse(item.message.content)
                .replace(Regex("\\s+"), " ")
                .trim()
            val preview = when {
                raw.isNotEmpty() -> raw.take(96)
                item.message.attachments.isNotEmpty() -> {
                    val count = item.message.attachments.size
                    if (count == 1) "Attachment: ${item.message.attachments.first().name}"
                    else "$count attachments"
                }
                else -> "Prompt #$ordinal"
            }
            anchors.add(
                PromptAnchor(
                    feedIndex = idx,
                    messageId = item.message.id,
                    ordinal = ordinal,
                    preview = preview,
                    timestampMs = item.message.createdAt
                )
            )
            ordinal++
        }
    }
    return anchors
}

private const val MAX_VISIBLE_DOTS = 18

/**
 * Codex-inspired vertical prompt navigation dot-slider.
 * Sits unobtrusively along the viewport edge; tap any dot or drag vertically along the track
 * to glide through every user prompt with tactile feedback and a live prompt preview tooltip.
 */
@Composable
fun ChatPromptSlider(
    anchors: List<PromptAnchor>,
    listState: LazyListState,
    isLeftSide: Boolean = true,
    onJumpToFeedIndex: (feedIndex: Int, animate: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    if (anchors.size < 2) return

    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    val activeViewportIdx by remember(listState, anchors) {
        derivedStateOf {
            if (anchors.isEmpty()) 0
            else {
                val firstIdx = listState.firstVisibleItemIndex
                val visibleIndices = listState.layoutInfo.visibleItemsInfo.map { it.index }
                val onScreenTop = anchors.indexOfFirst { a ->
                    a.feedIndex in visibleIndices && a.feedIndex <= firstIdx + 2
                }
                if (onScreenTop >= 0) {
                    onScreenTop
                } else {
                    val lastAbove = anchors.indexOfLast { it.feedIndex <= firstIdx + 1 }
                    if (lastAbove >= 0) lastAbove else 0
                }
            }
        }
    }

    var isDragging by remember { mutableStateOf(false) }
    var showPreviewTooltip by remember { mutableStateOf(false) }
    var scrubIndex by remember { mutableIntStateOf(activeViewportIdx) }
    var trackHeightPx by remember { mutableIntStateOf(1) }

    // Dismiss preview tooltip shortly after tap/drag ends
    LaunchedEffect(showPreviewTooltip, isDragging, scrubIndex) {
        if (showPreviewTooltip && !isDragging) {
            delay(750)
            showPreviewTooltip = false
        }
    }

    val displayedActiveIdx = if (isDragging || showPreviewTooltip) {
        scrubIndex.coerceIn(0, anchors.lastIndex)
    } else {
        activeViewportIdx.coerceIn(0, anchors.lastIndex)
    }

    // Evenly sample visual dots when very long chats exceed MAX_VISIBLE_DOTS
    val visibleSlots = remember(anchors.size) {
        if (anchors.size <= MAX_VISIBLE_DOTS) {
            anchors.indices.toList()
        } else {
            List(MAX_VISIBLE_DOTS) { slot ->
                ((slot.toFloat() / (MAX_VISIBLE_DOTS - 1).toFloat()) * anchors.lastIndex).roundToInt()
            }
        }
    }
    val activeSlotIdx = remember(visibleSlots, displayedActiveIdx) {
        if (visibleSlots.isEmpty()) 0
        else visibleSlots.indices.minByOrNull { abs(visibleSlots[it] - displayedActiveIdx) } ?: 0
    }

    val topBottomPadPx = with(density) { 10.dp.toPx() }
    fun yToAnchorIndex(yPx: Float): Int {
        val usable = (trackHeightPx.toFloat() - topBottomPadPx * 2f).coerceAtLeast(1f)
        val fraction = ((yPx - topBottomPadPx) / usable).coerceIn(0f, 1f)
        return (fraction * anchors.lastIndex).roundToInt().coerceIn(0, anchors.lastIndex)
    }

    val capsuleBg = when {
        isDragging || showPreviewTooltip -> if (isDark) Color(0xFF18181F).copy(alpha = 0.94f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        else -> if (isDark) Color(0xFF121216).copy(alpha = 0.58f) else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
    }
    val capsuleBorder = when {
        isDragging || showPreviewTooltip -> ClaudeTerracotta.copy(alpha = 0.45f)
        else -> if (isDark) Color(0xFF282830).copy(alpha = 0.65f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
    }

    val dotSpacing = if (visibleSlots.size <= 4) 8.dp else 5.dp
    val isEngaged = isDragging || showPreviewTooltip

    val railComposable: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .width(28.dp)
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(anchors) {
                    detectTapGestures { offset ->
                        val targetIdx = yToAnchorIndex(offset.y)
                        scrubIndex = targetIdx
                        showPreviewTooltip = true
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onJumpToFeedIndex(anchors[targetIdx].feedIndex, true)
                    }
                }
                .pointerInput(anchors) {
                    var lastEmittedIdx = -1
                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            isDragging = true
                            showPreviewTooltip = true
                            val targetIdx = yToAnchorIndex(startOffset.y)
                            lastEmittedIdx = targetIdx
                            scrubIndex = targetIdx
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onJumpToFeedIndex(anchors[targetIdx].feedIndex, false)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            val targetIdx = yToAnchorIndex(change.position.y)
                            if (targetIdx != lastEmittedIdx) {
                                lastEmittedIdx = targetIdx
                                scrubIndex = targetIdx
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onJumpToFeedIndex(anchors[targetIdx].feedIndex, false)
                            }
                        },
                        onDragEnd = {
                            isDragging = false
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            val capsuleWidth by animateDpAsState(
                targetValue = if (isEngaged) 14.dp else 10.dp,
                animationSpec = tween(140),
                label = "rail_width"
            )
            Surface(
                modifier = Modifier.width(capsuleWidth),
                shape = CircleShape,
                color = capsuleBg,
                border = BorderStroke(0.7.dp, capsuleBorder)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dotSpacing)
                ) {
                    visibleSlots.forEachIndexed { slotIdx, _ ->
                        val isActive = slotIdx == activeSlotIdx
                        val dotHeight by animateDpAsState(
                            targetValue = if (isActive) 13.dp else 4.dp,
                            animationSpec = tween(130),
                            label = "dot_h_$slotIdx"
                        )
                        val dotWidth = if (isActive && isEngaged) 5.dp else 4.dp
                        val dotColor = when {
                            isActive && isEngaged -> ClaudeTerracotta
                            isActive -> if (isDark) Color(0xFFE8E5DF) else MaterialTheme.colorScheme.primary
                            isEngaged -> if (isDark) Color(0xFF6B6B76) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                            else -> if (isDark) Color(0xFF45454F) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
                        }
                        Box(
                            modifier = Modifier
                                .width(dotWidth)
                                .height(dotHeight)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                    }
                }
            }
        }
    }

    val tooltipComposable: @Composable () -> Unit = {
        val currentAnchor = anchors[displayedActiveIdx]
        AnimatedVisibility(
            visible = isEngaged,
            enter = fadeIn(tween(120)) + scaleIn(initialScale = 0.92f, animationSpec = tween(120)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.94f, animationSpec = tween(150))
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isDark) Color(0xFF15151B) else MaterialTheme.colorScheme.surface,
                border = BorderStroke(0.9.dp, if (isDark) Color(0xFF2D2D37) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)),
                shadowElevation = 8.dp,
                modifier = Modifier.widthIn(min = 148.dp, max = 236.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 9.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Prompt ${currentAnchor.ordinal}/${anchors.size}",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = ClaudeTerracotta
                        )
                        val timeText = formatTelemetryShortTime(currentAnchor.timestampMs)
                        if (timeText.isNotEmpty()) {
                            Text(
                                text = "· $timeText",
                                fontSize = 10.5.sp,
                                color = if (isDark) Color(0xFF8E8E98) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = currentAnchor.preview,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color(0xFFF4F4F5) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isLeftSide) {
            railComposable()
            tooltipComposable()
        } else {
            tooltipComposable()
            railComposable()
        }
    }
}
