package com.example.gemini.ui.chat

import androidx.compose.runtime.Immutable
import com.example.gemini.domain.model.ChatMessage
import com.example.gemini.domain.model.MessageRole
import com.example.gemini.ui.components.MarkdownBlock
import com.example.gemini.ui.components.parseMarkdownBlocks
import com.example.gemini.ui.components.CodeBlockCache
import com.example.gemini.ui.components.MarkdownTextCache
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Immutable
sealed class ChatFeedItem(val key: String, val contentType: String) {
    @Immutable
    data class Summary(val message: ChatMessage) : ChatFeedItem("summary_${message.id}", "SUMMARY")
    @Immutable
    data class User(val message: ChatMessage) : ChatFeedItem("user_${message.id}", "USER")
    @Immutable
    data class AssistantThinking(val messageId: String, val thoughtText: String, val durationMs: Long?, val isStreaming: Boolean) : ChatFeedItem("thought_$messageId", "THOUGHT")
    @Immutable
    data class AssistantBlock(val messageId: String, val blockIndex: Int, val block: MarkdownBlock, val isFirst: Boolean, val isLast: Boolean) : ChatFeedItem("block_${messageId}_$blockIndex", block.javaClass.simpleName)
    @Immutable
    data class AssistantTyping(val messageId: String, val modelId: String) : ChatFeedItem("active_assistant_typing", "TYPING")
    @Immutable
    data class AssistantFooter(val message: ChatMessage) : ChatFeedItem("footer_${message.id}", "FOOTER")
    @Immutable
    data class StreamingMessage(val message: ChatMessage) : ChatFeedItem("streaming_${message.id}", "STREAMING")
    /** A wavy-line divider with a label: a new day, or a model switch. */
    @Immutable
    data class Divider(val id: String, val label: String) : ChatFeedItem("divider_$id", "DIVIDER")
}

object ChatFeedCache {
    private val cache = android.util.LruCache<String, List<ChatFeedItem>>(400)
    private val calendarThreadLocal = ThreadLocal.withInitial { Calendar.getInstance() }
    @Volatile
    private var cachedDayLabelLocale: Locale? = null
    @Volatile
    private var cachedSameYearPattern: String? = null
    @Volatile
    private var cachedOtherYearPattern: String? = null
    private val dayLabelSameYearFormat = ThreadLocal<Pair<String, SimpleDateFormat>>()
    private val dayLabelOtherYearFormat = ThreadLocal<Pair<String, SimpleDateFormat>>()

    fun getOrParse(msg: ChatMessage): List<ChatFeedItem> {
        val cacheKey = "${msg.id}_${msg.content.hashCode()}_${msg.toolCalls.hashCode()}_${msg.thoughtText?.hashCode() ?: 0}_${msg.tokenUsage?.hashCode() ?: 0}_${msg.diffStats?.hashCode() ?: 0}"
        val cached = cache.get(cacheKey)
        if (cached != null) return cached

        val msgItems = mutableListOf<ChatFeedItem>()
        when (msg.role) {
            MessageRole.SUMMARY -> {
                msgItems.add(ChatFeedItem.Summary(msg))
            }
            MessageRole.USER -> {
                // Show the slash command the user typed, not the instructions it expanded to
                val shown = com.example.gemini.data.remote.PromptExpansion.collapse(msg.content)
                msgItems.add(ChatFeedItem.User(if (shown === msg.content) msg else msg.copy(content = shown)))
            }
            MessageRole.ASSISTANT -> {
                val durTag = if (msg.thoughtDurationMs != null && msg.thoughtDurationMs > 0) ":${msg.thoughtDurationMs}" else ""
                val contentToParse = if (!msg.thoughtText.isNullOrEmpty() && !msg.content.contains("<!-- thought") && !msg.content.contains("<thought")) {
                    "<!-- thought$durTag -->\n${msg.thoughtText}\n<!-- /thought -->\n${msg.content}"
                } else {
                    msg.content
                }
                if (contentToParse.isNotEmpty() || msg.toolCalls.isNotEmpty()) {
                    val rawBlocks = parseMarkdownBlocks(contentToParse, msg.toolCalls)
                    val blocks = groupToolBlocks(rawBlocks, isStreaming = false)
                    for (i in blocks.indices) {
                        msgItems.add(ChatFeedItem.AssistantBlock(
                            messageId = msg.id,
                            blockIndex = i,
                            block = blocks[i],
                            isFirst = i == 0,
                            isLast = i == blocks.lastIndex
                        ))
                    }
                }
                if (msg.content.isNotEmpty()) {
                    msgItems.add(ChatFeedItem.AssistantFooter(msg))
                }
            }
            else -> {}
        }
        cache.put(cacheKey, msgItems)
        return msgItems
    }

    /**
     * Prewarm markdown blocks asynchronously on Dispatchers.Default for the most recent
     * window of messages (last 12 messages by default) so the UI thread has zero parsing work
     * during composition, without wasting memory/CPU on the entire chat history.
     */
    fun prewarm(messages: List<ChatMessage>, windowSize: Int = 12, isDark: Boolean = true) {
        if (messages.isEmpty()) return
        val targetMessages = if (messages.size > windowSize) messages.takeLast(windowSize) else messages
        for (msg in targetMessages) {
            if (!msg.isStreaming) {
                val items = getOrParse(msg)
                for (item in items) {
                    if (item is ChatFeedItem.AssistantBlock) {
                        when (val block = item.block) {
                            is MarkdownBlock.Code -> {
                                CodeBlockCache.prewarm(block.code, block.language, isDark)
                            }
                            is MarkdownBlock.Paragraph -> {
                                MarkdownTextCache.prewarm(block.text, isDark)
                            }
                            is MarkdownBlock.Header -> {
                                MarkdownTextCache.prewarm(block.text, isDark)
                            }
                            is MarkdownBlock.Bullet -> {
                                MarkdownTextCache.prewarm(block.text, isDark)
                            }
                            is MarkdownBlock.Numbered -> {
                                MarkdownTextCache.prewarm(block.text, isDark)
                            }
                            is MarkdownBlock.Blockquote -> {
                                MarkdownTextCache.prewarm(block.text, isDark)
                            }
                            is MarkdownBlock.Task -> {
                                MarkdownTextCache.prewarm(block.text, isDark)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    /**
     * Groups contiguous [MarkdownBlock.AgentTool] blocks into a single [MarkdownBlock.AgentToolGroup] (CommandsAccordion).
     * Interactive choice questionnaires are kept distinct so users can answer them without impediment.
     */
    fun groupToolBlocks(blocks: List<MarkdownBlock>, isStreaming: Boolean): List<MarkdownBlock> {
        if (blocks.none { it is MarkdownBlock.AgentTool }) return blocks
        val result = mutableListOf<MarkdownBlock>()
        val currentToolGroup = mutableListOf<com.example.gemini.domain.model.ToolCall>()

        fun flushGroup() {
            if (currentToolGroup.isNotEmpty()) {
                if (currentToolGroup.size == 1 && currentToolGroup[0].toolType == com.example.gemini.domain.model.ToolType.ASK_CHOICE) {
                    result.add(MarkdownBlock.AgentTool(currentToolGroup[0]))
                } else {
                    val bgTasks = currentToolGroup.filter { it.isBackground || (it.status == "RUNNING" && !isStreaming) }
                    val finishedTasks = currentToolGroup.filterNot { it.isBackground || (it.status == "RUNNING" && !isStreaming) }

                    if (bgTasks.isNotEmpty() && !isStreaming) {
                        // Separate dropdown for commands actively running in the background
                        result.add(MarkdownBlock.AgentToolGroup(bgTasks, isStreaming = false, isBackgroundSection = true))
                        // Separate dropdown for finished commands (if any)
                        if (finishedTasks.isNotEmpty()) {
                            result.add(MarkdownBlock.AgentToolGroup(finishedTasks, isStreaming = false, isBackgroundSection = false))
                        }
                    } else {
                        // Standard single dropdown when no background commands or while streaming
                        result.add(MarkdownBlock.AgentToolGroup(currentToolGroup.toList(), isStreaming = isStreaming, isBackgroundSection = false))
                    }
                }
                currentToolGroup.clear()
            }
        }

        for (block in blocks) {
            if (block is MarkdownBlock.AgentTool) {
                if (block.toolCall.toolType == com.example.gemini.domain.model.ToolType.ASK_CHOICE) {
                    flushGroup()
                    result.add(block)
                } else {
                    currentToolGroup.add(block.toolCall)
                }
            } else {
                flushGroup()
                result.add(block)
            }
        }
        flushGroup()
        return result
    }

    /**
     * The chat as list items. With [dividers], a divider goes before the first prompt of each new day ("Sat, Oct 10")
     * and before the prompt of a reply whose model differs from the previous reply's ("Switched to Sonnet 5.5";
     * [modelName] turns a model id into its display name).
     */
    fun buildFeedItems(
        messages: List<ChatMessage>,
        selectedModelId: String,
        dividers: Boolean = false,
        modelName: (String) -> String = { it },
        isActivelyRunning: Boolean = false
    ): List<ChatFeedItem> {
        val result = ArrayList<ChatFeedItem>(messages.size * 3)
        val seenKeys = HashSet<String>()

        fun addItem(item: ChatFeedItem) {
            if (seenKeys.add(item.key)) {
                result.add(item)
            }
        }

        var lastDay: String? = null
        var lastPromptAt = 0L
        var lastModel: String? = null
        // where the latest prompt's items start: a model divider goes right before that prompt
        var promptStart = -1
        val lastMessage = messages.lastOrNull()
        for (msg in messages) {
            if (dividers && msg.role == MessageRole.USER) {
                val day = dayKey(msg.createdAt)
                if (day != lastDay) {
                    addItem(ChatFeedItem.Divider("day_$day", dayLabel(msg.createdAt)))
                    lastDay = day
                } else if (lastPromptAt > 0 && msg.createdAt - lastPromptAt >= GAP_DIVIDER_MS) {
                    addItem(ChatFeedItem.Divider("gap_${msg.id}", dayLabel(msg.createdAt)))
                }
                lastPromptAt = msg.createdAt
                promptStart = result.size
            }
            if (dividers && msg.role == MessageRole.ASSISTANT && msg.modelId != null) {
                val previous = lastModel
                if (previous != null && msg.modelId != previous) {
                    val divider = ChatFeedItem.Divider("model_${msg.id}", "Switched to ${modelName(msg.modelId)}")
                    if (seenKeys.add(divider.key)) result.add(if (promptStart >= 0) promptStart else result.size, divider)
                }
                lastModel = msg.modelId
            }
            // only the first reply after a prompt may put a divider before that prompt
            if (msg.role == MessageRole.ASSISTANT) promptStart = -1

            val isCurrentTurnRunning = msg.isStreaming || (msg == lastMessage && isActivelyRunning)
            if (!isCurrentTurnRunning) {
                val parsed = getOrParse(msg)
                for (item in parsed) {
                    addItem(item)
                }
            } else {
                val hasActiveRunningTool = msg.toolCalls.any {
                    it.status == "RUNNING" || it.status == "PENDING_APPROVAL" || it.status == "AWAITING_CHOICE"
                }
                val durTag = if (msg.thoughtDurationMs != null && msg.thoughtDurationMs > 0) ":${msg.thoughtDurationMs}" else ""
                val contentToParse = if (!msg.thoughtText.isNullOrEmpty() && !msg.content.contains("<!-- thought") && !msg.content.contains("<thought")) {
                    if (msg.content.isBlank()) {
                        "<!-- thought:streaming$durTag -->\n${msg.thoughtText}"
                    } else {
                        "<!-- thought:streaming$durTag -->\n${msg.thoughtText}\n<!-- /thought -->\n${msg.content}"
                    }
                } else {
                    msg.content
                }
                if (contentToParse.isNotEmpty() || msg.toolCalls.isNotEmpty()) {
                    val rawBlocks = parseMarkdownBlocks(contentToParse, msg.toolCalls)
                    val blocks = groupToolBlocks(rawBlocks, isStreaming = true)
                    for (i in blocks.indices) {
                        addItem(ChatFeedItem.AssistantBlock(
                            messageId = msg.id,
                            blockIndex = i,
                            block = blocks[i],
                            isFirst = i == 0,
                            isLast = i == blocks.lastIndex
                        ))
                    }
                    if (!hasActiveRunningTool && msg.content.isBlank()) {
                        // After terminal commands finish in an active turn, show the typing/thinking indicator
                        // so the user clearly sees the agent is formulating the subsequent response!
                        addItem(ChatFeedItem.AssistantTyping(msg.id, selectedModelId))
                    }
                } else if (!hasActiveRunningTool) {
                    // Only show waiting indicator before ANY output or tool call has appeared
                    addItem(ChatFeedItem.AssistantTyping(msg.id, selectedModelId))
                }
            }
        }
        return result
    }

    fun clear() {
        cache.evictAll()
    }

    private fun dayKey(millis: Long): String {
        val c = calendarThreadLocal.get() ?: Calendar.getInstance()
        c.timeInMillis = millis
        return "${c.get(Calendar.YEAR)}-${c.get(Calendar.DAY_OF_YEAR)}"
    }

    /** A prompt sent this long after the previous one on the same day gets a divider with the time. */
    private const val GAP_DIVIDER_MS = 60 * 60 * 1000L

    /** "Sat, Oct 10, 15:45" (24-hour time) in the phone's language; the year is added when it is not this year. */
    private fun dayLabel(millis: Long): String {
        val locale = Locale.getDefault()
        val c = calendarThreadLocal.get() ?: Calendar.getInstance()
        c.timeInMillis = System.currentTimeMillis()
        val thisYear = c.get(Calendar.YEAR)
        c.timeInMillis = millis
        val year = c.get(Calendar.YEAR)

        if (cachedDayLabelLocale != locale) {
            cachedSameYearPattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEMMMdHmm")
            cachedOtherYearPattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "EEEMMMdyyyyHmm")
            cachedDayLabelLocale = locale
        }

        val isSameYear = year == thisYear
        val pattern = (if (isSameYear) cachedSameYearPattern else cachedOtherYearPattern)
            ?: android.text.format.DateFormat.getBestDateTimePattern(locale, (if (isSameYear) "EEEMMMd" else "EEEMMMdyyyy") + "Hmm")
        val holder = if (isSameYear) dayLabelSameYearFormat else dayLabelOtherYearFormat
        val existing = holder.get()
        val formatter = if (existing != null && existing.first == pattern) {
            existing.second
        } else {
            SimpleDateFormat(pattern, locale).also { holder.set(pattern to it) }
        }
        return formatter.format(Date(millis))
    }
}

@Immutable
data class ConversationScrollPosition(
    val index: Int,
    val offset: Int,
    val isNearBottom: Boolean
)

object ConversationScrollCache {
    private val scrollPositions = ConcurrentHashMap<String, ConversationScrollPosition>()

    fun save(convId: String, index: Int, offset: Int, isNearBottom: Boolean) {
        if (convId.isBlank() || convId == "empty") return
        scrollPositions[convId] = ConversationScrollPosition(index, offset, isNearBottom)
    }

    fun get(convId: String?): ConversationScrollPosition? {
        if (convId.isNullOrBlank() || convId == "empty") return null
        return scrollPositions[convId]
    }

    fun clear(convId: String) {
        scrollPositions.remove(convId)
    }

    fun clearAll() {
        scrollPositions.clear()
    }
}
