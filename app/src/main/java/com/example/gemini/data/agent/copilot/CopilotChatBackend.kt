package com.example.gemini.data.agent.copilot

import android.app.Application
import android.util.Log
import com.example.gemini.data.agent.AgentChatBackend
import com.example.gemini.data.agent.ChatSessionStore
import com.example.gemini.domain.model.AgentKind
import com.example.gemini.domain.model.AiModel
import com.example.gemini.domain.model.ChatMessage
import com.example.gemini.domain.model.Conversation
import com.example.gemini.domain.model.MessageRole
import com.example.gemini.domain.model.ModelFamily
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.UUID
import java.util.concurrent.TimeUnit

class CopilotChatBackend(
    private val application: Application,
    private val backendScope: CoroutineScope,
    private val store: ChatSessionStore,
    val accountManager: CopilotAccountManager,
    val prefs: CopilotPreferences
) : AgentChatBackend {

    override val kind: AgentKind = AgentKind.COPILOT

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    private val storageDir = File(application.filesDir, "copilot_chats").apply { mkdirs() }

    private val _conversations = MutableStateFlow<List<Conversation>>(emptyList())
    override val conversations: StateFlow<List<Conversation>> = _conversations.asStateFlow()

    override val currentConversation: StateFlow<Conversation?> = store.currentConversation.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    override val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isStreaming = MutableStateFlow(false)
    override val isStreaming: StateFlow<Boolean> = _isStreaming.asStateFlow()

    private val _isTurnActive = MutableStateFlow(false)
    val isCurrentChatActivelyRunning: StateFlow<Boolean> = _isTurnActive.asStateFlow()

    private val _isLoadingConversation = MutableStateFlow(false)
    override val isLoadingConversation: StateFlow<Boolean> = _isLoadingConversation.asStateFlow()

    private val _conversationError = MutableStateFlow<String?>(null)
    override val conversationError: StateFlow<String?> = _conversationError.asStateFlow()

    private val _selectedModelId = MutableStateFlow(prefs.selectedModel.value)
    val selectedModelId: StateFlow<String> = _selectedModelId.asStateFlow()

    private val _availableModels = MutableStateFlow<List<AiModel>>(
        accountManager.availableModels.value.mapIndexed { idx, m -> m.toAiModel(idx == 0 || m.id == "gpt-4o") }
    )
    val availableModels: StateFlow<List<AiModel>> = _availableModels.asStateFlow()

    val isRefreshingModels = MutableStateFlow(false)

    private var currentStreamJob: Job? = null

    init {
        loadConversations()
        backendScope.launch {
            accountManager.availableModels.collect { models ->
                if (models.isNotEmpty()) {
                    val mapped = models.mapIndexed { idx, m ->
                        m.toAiModel(isDefault = idx == 0 || m.id == "gpt-4o")
                    }
                    _availableModels.value = mapped
                    val cur = _selectedModelId.value
                    if (models.none { it.id == cur }) {
                        val fallback = models.firstOrNull { it.id == "gpt-4o" }?.id ?: models.first().id
                        selectModel(fallback)
                    }
                }
            }
        }
    }

    fun start() {
        backendScope.launch {
            if (!prefs.githubToken.value.isNullOrBlank()) {
                accountManager.fetchAvailableModels()
            }
        }
    }

    fun refreshModels() {
        backendScope.launch {
            isRefreshingModels.value = true
            try {
                accountManager.fetchAvailableModels()
            } finally {
                isRefreshingModels.value = false
            }
        }
    }

    override fun startNewChat() {
        val id = UUID.randomUUID().toString()
        val defaultProjPath = com.example.gemini.data.daemon.TermuxDaemonManager.activeProject.value?.path ?: ""
        val defaultWorkspaceUri = if (defaultProjPath.isNotBlank()) {
            if (defaultProjPath.startsWith("file://")) defaultProjPath else "file://$defaultProjPath"
        } else ""
        val conv = Conversation(
            id = id,
            title = "New Chat",
            sessionId = id,
            workspaceUri = defaultWorkspaceUri,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            agent = AgentKind.COPILOT
        )
        // Keep as draft in currentConversation only. Do NOT save to disk or add to _conversations
        // until the user actually sends their first message.
        _messages.value = emptyList()
        _conversationError.value = null
        store.currentConversation.value = conv
    }

    override fun selectConversation(id: String) {
        val conv = _conversations.value.find { it.id == id } ?: return
        store.currentConversation.value = conv
        _conversationError.value = null
        loadMessages(id)
    }

    override fun sendMessage(content: String) {
        val trimmed = content.trim()
        if (trimmed.isEmpty() || _isStreaming.value) return

        val conv = store.currentConversation.value ?: run {
            startNewChat()
            store.currentConversation.value!!
        }

        val userMsg = ChatMessage(
            conversationId = conv.id,
            role = MessageRole.USER,
            content = trimmed
        )
        val updatedMsgs = _messages.value + userMsg
        _messages.value = updatedMsgs

        // Update conversation title if first message
        val updatedTitle = if (_messages.value.size <= 2 && (conv.title == "New Chat" || conv.title == "Conversation")) {
            if (trimmed.length > 36) trimmed.take(36) + "…" else trimmed
        } else conv.title
        val updatedConv = conv.copy(title = updatedTitle, updatedAt = System.currentTimeMillis())
        store.currentConversation.value = updatedConv

        // Materialize conversation into saved list upon first user message
        val exists = _conversations.value.any { it.id == conv.id }
        if (!exists) {
            _conversations.value = listOf(updatedConv) + _conversations.value
        } else {
            _conversations.value = _conversations.value.map { if (it.id == conv.id) updatedConv else it }
        }
        saveConversations()
        saveMessages(conv.id)
        executeCompletions(updatedConv, updatedMsgs)
    }

    private fun executeCompletions(conv: Conversation, history: List<ChatMessage>) {
        currentStreamJob?.cancel()
        _isStreaming.value = true
        _isTurnActive.value = true
        _conversationError.value = null

        val modelId = _selectedModelId.value
        val assistantMsgId = UUID.randomUUID().toString()
        val initialAssistantMsg = ChatMessage(
            id = assistantMsgId,
            conversationId = conv.id,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            modelId = modelId
        )
        _messages.value = history + initialAssistantMsg

        currentStreamJob = backendScope.launch(Dispatchers.IO) {
            try {
                val copilotToken = accountManager.ensureValidCopilotToken()
                if (copilotToken == null) {
                    throw IllegalStateException("GitHub Copilot token not available. Please sign in via Settings → GitHub Copilot.")
                }

                val systemPrompt = """
                    You are an AI programming assistant running inside Gravent (an advanced Android agentic IDE and developer environment).
                    You are executing on Android (Linux kernel) within Termux and PRoot Linux.
                    The user interacts with you via the Gravent mobile interface.
                    You know everything about the Gravent application, Termux tools, git, node, pnpm, python, and the local workspace.
                    If the user asks which model you are, who created you, or what environment you are in, you must clearly state that you are running inside Gravent on Android.
                    Provide clean, production-grade code, explanations, and shell commands.
                    When executing or monitoring long-running processes (such as builds, tests, git clone, or CI workflows), execute them as single-shot blocking or watching commands (e.g. 'gh run watch <run_id> --exit-status') rather than repeated tight polling loops, ensuring optimal token economy and smart performance.
                """.trimIndent()

                val isReasoningModel = modelId.contains("o1", ignoreCase = true) || modelId.contains("o3", ignoreCase = true)
                val systemRole = if (isReasoningModel) "developer" else "system"

                val apiMessages = mutableListOf<CopilotChatMessagePayload>()
                apiMessages.add(CopilotChatMessagePayload(role = systemRole, content = systemPrompt))

                // Build dialogue history
                for (msg in history.takeLast(16)) {
                    val role = if (msg.role == MessageRole.USER) "user" else "assistant"
                    if (msg.content.isNotBlank()) {
                        apiMessages.add(CopilotChatMessagePayload(role = role, content = msg.content))
                    }
                }

                val requestBodyObj = CopilotChatRequest(
                    model = modelId,
                    stream = true,
                    messages = apiMessages,
                    temperature = if (isReasoningModel) null else 0.2
                )

                val requestJson = json.encodeToString(requestBodyObj)
                val body = requestJson.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.githubcopilot.com/chat/completions")
                    .header("Authorization", "Bearer $copilotToken")
                    .header("X-Request-Id", UUID.randomUUID().toString())
                    .header("VScode-SessionId", conv.id)
                    .header("User-Agent", "GitHubCopilotChat/0.24.1")
                    .header("Editor-Version", "vscode/1.96.0")
                    .header("Editor-Plugin-Version", "copilot-chat/0.24.1")
                    .header("Copilot-Integration-Id", "vscode-chat")
                    .header("Openai-Organization", "github-copilot")
                    .header("Openai-Intent", "conversation-panel")
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    throw IllegalStateException("Copilot API error ${response.code}: $errBody")
                }

                val contentType = response.header("Content-Type").orEmpty().lowercase()
                val accumulated = StringBuilder()

                if (contentType.contains("application/json") && !contentType.contains("text/event-stream")) {
                    // Full buffered JSON response
                    val bodyString = response.body?.string().orEmpty()
                    var extractedContent: String? = null
                    try {
                        val parsed = json.decodeFromString<CopilotChatChunk>(bodyString)
                        val choice = parsed.choices.firstOrNull()
                        extractedContent = choice?.message?.content
                            ?: choice?.delta?.content
                            ?: choice?.text
                    } catch (_: Exception) {}

                    if (extractedContent.isNullEmpty()) {
                        val match = CONTENT_JSON_REGEX.find(bodyString) ?: TEXT_JSON_REGEX.find(bodyString)
                        if (match != null) {
                            val unescaped = unescapeJsonString(match.groupValues[1])
                            if (unescaped.isNotEmpty()) extractedContent = unescaped
                        }
                    }

                    val contentText = extractedContent.orEmpty()
                    if (contentText.isNotEmpty()) {
                        accumulated.append(contentText)
                        withContext(Dispatchers.Main) {
                            _messages.value = _messages.value.map { msg ->
                                if (msg.id == assistantMsgId) msg.copy(content = contentText) else msg
                            }
                        }
                    }
                } else {
                    // Event stream (SSE)
                    val responseStream = response.body?.byteStream() ?: throw IllegalStateException("Empty response body")
                    val reader = BufferedReader(InputStreamReader(responseStream))

                    while (isActive) {
                        val currentLine = reader.readLine() ?: break
                        val raw = currentLine.trim()
                        if (raw.isEmpty()) continue

                        val jsonPayload = if (raw.startsWith("data:")) {
                            val d = raw.removePrefix("data:").trim()
                            if (d == "[DONE]") break
                            d
                        } else if (raw.startsWith("{") && raw.endsWith("}")) {
                            raw
                        } else null

                        if (jsonPayload != null && jsonPayload.isNotBlank()) {
                            var deltaContent: String? = null
                            try {
                                val chunk = json.decodeFromString<CopilotChatChunk>(jsonPayload)
                                val choice = chunk.choices.firstOrNull()
                                deltaContent = choice?.delta?.content
                                    ?: choice?.delta?.text
                                    ?: choice?.message?.content
                                    ?: choice?.text
                            } catch (_: Exception) {
                                // R8 obfuscation or schema divergence fallback
                            }

                            // Robust fallback: if deserialization did not yield content, extract via regex
                            if (deltaContent.isNullEmpty()) {
                                val match = CONTENT_JSON_REGEX.find(jsonPayload) ?: TEXT_JSON_REGEX.find(jsonPayload)
                                if (match != null) {
                                    val unescaped = unescapeJsonString(match.groupValues[1])
                                    if (unescaped.isNotEmpty()) {
                                        deltaContent = unescaped
                                    }
                                }
                            }

                            if (!deltaContent.isNullEmpty()) {
                                accumulated.append(deltaContent)
                                val currentText = accumulated.toString()
                                withContext(Dispatchers.Main) {
                                    _messages.value = _messages.value.map { msg ->
                                        if (msg.id == assistantMsgId) msg.copy(content = currentText) else msg
                                    }
                                }
                            }
                        }
                    }
                }

                // Finalize assistant message
                val finalText = accumulated.toString().trim()
                withContext(Dispatchers.Main) {
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantMsgId) {
                            val contentToSet = if (finalText.isBlank()) {
                                "❌ No response was returned by model ($modelId). The model may not be available on your plan. Try selecting another model (such as GPT-4o)."
                            } else {
                                finalText
                            }
                            msg.copy(content = contentToSet, isStreaming = false)
                        } else msg
                    }
                }
                saveMessages(conv.id)

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Copilot streaming was cancelled")
                } else {
                    Log.e(TAG, "Copilot completion error", e)
                    val errorText = e.message ?: "An unexpected error occurred while communicating with Copilot."
                    val userFriendlyError = if (errorText.contains("modelnotsupported") || errorText.contains("model is not supported")) {
                        "❌ Model '$modelId' is not supported by your GitHub Copilot subscription plan. Please select an available model from the picker (e.g. GPT-4o or Claude 3.5 Sonnet)."
                    } else if (errorText.contains("401") || errorText.contains("token")) {
                        "❌ Copilot authentication expired. Please re-authenticate via Settings → GitHub Copilot."
                    } else {
                        "❌ $errorText"
                    }
                    _conversationError.value = userFriendlyError
                    withContext(Dispatchers.Main) {
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == assistantMsgId) {
                                val current = msg.content
                                val withErr = if (current.isBlank()) userFriendlyError else "$current\n\n[Generation stopped: $userFriendlyError]"
                                msg.copy(content = withErr, isStreaming = false)
                            } else msg
                        }
                    }
                    saveMessages(conv.id)
                    if (errorText.contains("modelnotsupported")) {
                        refreshModels()
                    }
                }
            } finally {
                _isStreaming.value = false
                _isTurnActive.value = false
            }
        }
    }

    override fun stopStreaming() {
        currentStreamJob?.cancel()
        _isStreaming.value = false
        _isTurnActive.value = false
        _messages.value = _messages.value.map {
            if (it.isStreaming) it.copy(isStreaming = false) else it
        }
    }

    override fun deleteConversation(id: String) {
        val updated = _conversations.value.filter { it.id != id }
        _conversations.value = updated
        val file = File(storageDir, "conv_$id.json")
        if (file.exists()) file.delete()
        saveConversations()
        if (store.currentConversation.value?.id == id) {
            if (updated.isNotEmpty()) {
                selectConversation(updated.first().id)
            } else {
                startNewChat()
            }
        }
    }

    override fun forkConversation(id: String) {
        val original = _conversations.value.find { it.id == id } ?: return
        val newId = UUID.randomUUID().toString()
        val forked = original.copy(
            id = newId,
            title = "${original.title} (Fork)",
            sessionId = newId,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        _conversations.value = listOf(forked) + _conversations.value
        saveConversations()

        // Copy messages
        val originalFile = File(storageDir, "conv_$id.json")
        if (originalFile.exists()) {
            val targetFile = File(storageDir, "conv_$newId.json")
            originalFile.copyTo(targetFile, overwrite = true)
        }
        selectConversation(newId)
    }

    override fun retryConnections() {
        accountManager.refreshStatus()
    }

    override fun onAppForegrounded() {
        accountManager.refreshStatus()
    }

    fun selectModel(modelId: String) {
        _selectedModelId.value = modelId
        prefs.setSelectedModel(modelId)
    }

    fun renameConversation(id: String, title: String) {
        val updated = _conversations.value.map {
            if (it.id == id) it.copy(title = title, updatedAt = System.currentTimeMillis()) else it
        }
        _conversations.value = updated
        val cur = store.currentConversation.value
        if (cur?.id == id) {
            store.currentConversation.value = cur.copy(title = title)
        }
        saveConversations()
    }

    private fun loadConversations() {
        val file = File(storageDir, "conversations_index.json")
        if (!file.exists()) return
        try {
            val text = file.readText()
            val list = json.decodeFromString<List<ConversationRecord>>(text)
            // Filter out empty ghost chats that never had messages sent
            val valid = list.map { it.toConversation() }.filter { conv ->
                val msgFile = File(storageDir, "conv_${conv.id}.json")
                msgFile.exists() && msgFile.length() > 5
            }
            _conversations.value = valid
            if (valid.size != list.size) {
                saveConversations()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load conversations", e)
        }
    }

    private fun saveConversations() {
        backendScope.launch(Dispatchers.IO) {
            try {
                val records = _conversations.value.map { ConversationRecord.from(it) }
                val file = File(storageDir, "conversations_index.json")
                file.writeText(json.encodeToString(records))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save conversations", e)
            }
        }
    }

    private fun loadMessages(convId: String) {
        val file = File(storageDir, "conv_$convId.json")
        if (!file.exists()) {
            _messages.value = emptyList()
            return
        }
        try {
            val text = file.readText()
            val list = json.decodeFromString<List<MessageRecord>>(text)
            _messages.value = list.map { it.toChatMessage() }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load messages", e)
            _messages.value = emptyList()
        }
    }

    private fun saveMessages(convId: String) {
        val msgs = _messages.value
        backendScope.launch(Dispatchers.IO) {
            try {
                val records = msgs.map { MessageRecord.from(it) }
                val file = File(storageDir, "conv_$convId.json")
                file.writeText(json.encodeToString(records))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save messages", e)
            }
        }
    }

    private fun String?.isNullEmpty(): Boolean = this == null || this.isEmpty()

    companion object {
        private const val TAG = "CopilotChatBackend"
        private val CONTENT_JSON_REGEX = Regex("\"content\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
        private val TEXT_JSON_REGEX = Regex("\"text\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")

        private fun unescapeJsonString(input: String): String {
            if (!input.contains('\\')) return input
            val sb = StringBuilder(input.length)
            var i = 0
            while (i < input.length) {
                val c = input[i]
                if (c == '\\' && i + 1 < input.length) {
                    when (val next = input[i + 1]) {
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        '\\' -> sb.append('\\')
                        '"' -> sb.append('"')
                        '/' -> sb.append('/')
                        'u' -> {
                            if (i + 5 < input.length) {
                                try {
                                    val code = input.substring(i + 2, i + 6).toInt(16)
                                    sb.append(code.toChar())
                                    i += 5
                                } catch (_: Exception) {
                                    sb.append(c)
                                }
                            } else {
                                sb.append(c)
                            }
                        }
                        else -> sb.append(next)
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }

        fun CopilotModelInfo.toAiModel(isDefault: Boolean = false): AiModel {
            val family = when {
                id.contains("claude", ignoreCase = true) -> ModelFamily.CLAUDE
                id.contains("gemini", ignoreCase = true) -> ModelFamily.GEMINI
                else -> ModelFamily.OTHER
            }
            val nameStr = displayName()
            val includedTag = if (isIncludedFree()) "Included in plan (No extra charge)" else "Standard / AI Credits"
            val desc = buildString {
                append(includedTag)
                if (!vendor.isNullOrBlank()) append(" • $vendor")
            }
            val supportsThinking = id.contains("o1", ignoreCase = true) || id.contains("o3", ignoreCase = true) || id.contains("thinking", ignoreCase = true)
            return AiModel(
                id = id,
                displayName = "$nameStr (Copilot)",
                family = family,
                supportsThinking = supportsThinking,
                description = desc,
                isDefault = isDefault
            )
        }
    }
}

@kotlinx.serialization.Serializable
private data class ConversationRecord(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    fun toConversation() = Conversation(
        id = id,
        title = title,
        sessionId = id,
        createdAt = createdAt,
        updatedAt = updatedAt,
        agent = AgentKind.COPILOT
    )

    companion object {
        fun from(conv: Conversation) = ConversationRecord(
            id = conv.id,
            title = conv.title,
            createdAt = conv.createdAt,
            updatedAt = conv.updatedAt
        )
    }
}

@kotlinx.serialization.Serializable
private data class MessageRecord(
    val id: String,
    val conversationId: String,
    val role: String,
    val content: String,
    val createdAt: Long,
    val modelId: String? = null
) {
    fun toChatMessage() = ChatMessage(
        id = id,
        conversationId = conversationId,
        role = if (role == "USER") MessageRole.USER else MessageRole.ASSISTANT,
        content = content,
        createdAt = createdAt,
        modelId = modelId
    )

    companion object {
        fun from(msg: ChatMessage) = MessageRecord(
            id = msg.id,
            conversationId = msg.conversationId,
            role = msg.role.name,
            content = msg.content,
            createdAt = msg.createdAt,
            modelId = msg.modelId
        )
    }
}
