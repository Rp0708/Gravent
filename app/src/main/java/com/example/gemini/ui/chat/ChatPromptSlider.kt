package com.example.gemini.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.gemini.data.remote.PromptExpansion
import com.example.gemini.theme.graventTokens
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
 * Blends whisper-quiet into the chat margin while reading, wakes gently on scroll,
 * and reveals a frosted pill + thumb-tracking prompt preview tooltip when tapped or dragged.
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
    var thumbRelativeOffsetY by remember { mutableFloatStateOf(0f) }
    var isRecentlyActive by remember { mutableStateOf(false) }

    val isScrolling = listState.isScrollInProgress
    val isEngaged = isDragging || showPreviewTooltip

    LaunchedEffect(isScrolling, activeViewportIdx, isEngaged) {
        if (isScrolling || isEngaged) {
            isRecentlyActive = true
        } else {
            delay(1350)
            isRecentlyActive = false
        }
    }

    LaunchedEffect(showPreviewTooltip, isDragging, scrubIndex) {
        if (showPreviewTooltip && !isDragging) {
            delay(750)
            showPreviewTooltip = false
        }
    }

    val displayedActiveIdx = if (isEngaged) {
        scrubIndex.coerceIn(0, anchors.lastIndex)
    } else {
        activeViewportIdx.coerceIn(0, anchors.lastIndex)
    }

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

    fun updateThumbY(yPx: Float) {
        val half = (trackHeightPx.toFloat() / 2f).coerceAtLeast(1f)
        val maxOffset = (half - topBottomPadPx).coerceAtLeast(0f)
        thumbRelativeOffsetY = (yPx - half).coerceIn(-maxOffset, maxOffset)
    }

    val railAlpha by animateFloatAsState(
        targetValue = when {
            isEngaged -> 1.0f
            isRecentlyActive -> 0.88f
            else -> 0.48f
        },
        animationSpec = tween(260),
        label = "rail_fade"
    )
    val capsuleBackdropAlpha by animateFloatAsState(
        targetValue = if (isEngaged) 1f else 0f,
        animationSpec = tween(180),
        label = "capsule_fade"
    )
    val capsuleWidth by animateDpAsState(
        targetValue = if (isEngaged) 15.dp else 10.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "rail_width"
    )
    val smoothTooltipOffsetY by animateFloatAsState(
        targetValue = thumbRelativeOffsetY,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "tooltip_y"
    )

    val tokens = graventTokens()
    val isDark = tokens.isDark
    val accentColor = tokens.accent
    val capsuleBg = tokens.cardBg.copy(alpha = (if (isDark) 0.92f else 0.96f) * capsuleBackdropAlpha)
    val capsuleBorder = if (capsuleBackdropAlpha > 0.05f) {
        BorderStroke(0.7.dp, accentColor.copy(alpha = 0.42f * capsuleBackdropAlpha))
    } else null

    val dotSpacing = if (visibleSlots.size <= 4) 8.dp else 5.dp

    val railComposable: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .width(26.dp)
                .alpha(railAlpha)
                .onSizeChanged { trackHeightPx = it.height }
                .pointerInput(anchors) {
                    detectTapGestures { offset ->
                        val targetIdx = yToAnchorIndex(offset.y)
                        updateThumbY(offset.y)
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
                            updateThumbY(startOffset.y)
                            val targetIdx = yToAnchorIndex(startOffset.y)
                            lastEmittedIdx = targetIdx
                            scrubIndex = targetIdx
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onJumpToFeedIndex(anchors[targetIdx].feedIndex, false)
                        },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            updateThumbY(change.position.y)
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
                            val settleIdx = scrubIndex.coerceIn(0, anchors.lastIndex)
                            onJumpToFeedIndex(anchors[settleIdx].feedIndex, true)
                        },
                        onDragCancel = {
                            isDragging = false
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.width(capsuleWidth),
                shape = CircleShape,
                color = capsuleBg,
                border = capsuleBorder
            ) {
                Column(
                    modifier = Modifier.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(dotSpacing)
                ) {
                    visibleSlots.forEachIndexed { slotIdx, _ ->
                        val isActive = slotIdx == activeSlotIdx
                        val dotHeight by animateDpAsState(
                            targetValue = when {
                                isActive && isEngaged -> 14.dp
                                isActive -> 10.dp
                                else -> 3.5.dp
                            },
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "dot_h_$slotIdx"
                        )
                        val dotWidth = if (isActive && isEngaged) 4.5.dp else 3.2.dp
                        val dotColor = when {
                            isActive && isEngaged -> accentColor
                            isActive -> accentColor
                            isEngaged -> tokens.textSecondary.copy(alpha = 0.78f)
                            else -> tokens.textMuted.copy(alpha = if (isDark) 0.75f else 0.52f)
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
            enter = fadeIn(tween(110)) + scaleIn(initialScale = 0.93f, animationSpec = tween(120)),
            exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.95f, animationSpec = tween(160)),
            modifier = Modifier.offset { IntOffset(0, smoothTooltipOffsetY.roundToInt()) }
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = tokens.cardBg,
                border = BorderStroke(0.8.dp, tokens.border),
                shadowElevation = 6.dp,
                modifier = Modifier.widthIn(min = 148.dp, max = 232.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Prompt ${currentAnchor.ordinal}/${anchors.size}",
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                        val timeText = formatTelemetryShortTime(currentAnchor.timestampMs)
                        if (timeText.isNotEmpty()) {
                            Text(
                                text = "· $timeText",
                                fontSize = 10.5.sp,
                                color = tokens.textSecondary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(2.5.dp))
                    Text(
                        text = currentAnchor.preview,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = tokens.textPrimary,
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
        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
