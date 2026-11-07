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

    private val _availableModels = MutableStateFlow(COPILOT_MODELS)
    val availableModels: StateFlow<List<AiModel>> = _availableModels.asStateFlow()

    val isRefreshingModels = MutableStateFlow(false)

    private var currentStreamJob: Job? = null

    init {
        loadConversations()
    }

    fun start() {
        if (_conversations.value.isEmpty()) {
            startNewChat()
        }
    }

    override fun startNewChat() {
        val id = UUID.randomUUID().toString()
        val conv = Conversation(
            id = id,
            title = "New Chat",
            sessionId = id,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            agent = AgentKind.COPILOT
        )
        val updated = listOf(conv) + _conversations.value.filter { it.id != id }
        _conversations.value = updated
        _messages.value = emptyList()
        _conversationError.value = null
        store.currentConversation.value = conv
        saveConversations()
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
        if (_messages.value.size <= 2 && (conv.title == "New Chat" || conv.title == "Conversation")) {
            val previewTitle = if (trimmed.length > 36) trimmed.take(36) + "…" else trimmed
            val updatedConv = conv.copy(title = previewTitle, updatedAt = System.currentTimeMillis())
            store.currentConversation.value = updatedConv
            _conversations.value = _conversations.value.map { if (it.id == conv.id) updatedConv else it }
            saveConversations()
        }

        saveMessages(conv.id)
        executeCompletions(conv, updatedMsgs)
    }

    private fun executeCompletions(conv: Conversation, history: List<ChatMessage>) {
        currentStreamJob?.cancel()
        _isStreaming.value = true
        _isTurnActive.value = true
        _conversationError.value = null

        val assistantMsgId = UUID.randomUUID().toString()
        val initialAssistantMsg = ChatMessage(
            id = assistantMsgId,
            conversationId = conv.id,
            role = MessageRole.ASSISTANT,
            content = "",
            isStreaming = true,
            modelId = _selectedModelId.value
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
                """.trimIndent()

                val apiMessages = mutableListOf<CopilotChatMessagePayload>()
                apiMessages.add(CopilotChatMessagePayload(role = "system", content = systemPrompt))

                // Build dialogue history
                for (msg in history.takeLast(16)) {
                    val role = if (msg.role == MessageRole.USER) "user" else "assistant"
                    if (msg.content.isNotBlank()) {
                        apiMessages.add(CopilotChatMessagePayload(role = role, content = msg.content))
                    }
                }

                val modelId = _selectedModelId.value
                val requestBodyObj = CopilotChatRequest(
                    model = modelId,
                    stream = true,
                    messages = apiMessages
                )

                val requestJson = json.encodeToString(requestBodyObj)
                val body = requestJson.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("https://api.githubcopilot.com/chat/completions")
                    .header("Authorization", "Bearer $copilotToken")
                    .header("Editor-Version", "vscode/1.96.0")
                    .header("Copilot-Integration-Id", "vscode-chat")
                    .header("Openai-Organization", "github-copilot")
                    .header("Openai-Intent", "conversation-panel")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()

                val response = httpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    throw IllegalStateException("Copilot API error ${response.code}: $errBody")
                }

                val responseStream = response.body?.byteStream() ?: throw IllegalStateException("Empty response body")
                val reader = BufferedReader(InputStreamReader(responseStream))
                val accumulated = StringBuilder()

                var line: String?
                while (isActive && reader.readLine().also { line = it } != null) {
                    val raw = line?.trim() ?: continue
                    if (raw.isEmpty()) continue
                    if (raw.startsWith("data: ")) {
                        val data = raw.removePrefix("data: ").trim()
                        if (data == "[DONE]") break

                        try {
                            val chunk = json.decodeFromString<CopilotChatChunk>(data)
                            val delta = chunk.choices.firstOrNull()?.delta?.content
                            if (!delta.isNullEmpty()) {
                                accumulated.append(delta)
                                val currentText = accumulated.toString()
                                withContext(Dispatchers.Main) {
                                    _messages.value = _messages.value.map { msg ->
                                        if (msg.id == assistantMsgId) msg.copy(content = currentText) else msg
                                    }
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }

                // Finalize assistant message
                withContext(Dispatchers.Main) {
                    _messages.value = _messages.value.map { msg ->
                        if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                    }
                }
                saveMessages(conv.id)

            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) {
                    Log.d(TAG, "Copilot streaming was cancelled")
                } else {
                    Log.e(TAG, "Copilot completion error", e)
                    val errorText = e.message ?: "An unexpected error occurred while communicating with Copilot."
                    _conversationError.value = errorText
                    withContext(Dispatchers.Main) {
                        _messages.value = _messages.value.map { msg ->
                            if (msg.id == assistantMsgId) {
                                val current = msg.content
                                val withErr = if (current.isBlank()) "❌ $errorText" else "$current\n\n[Generation stopped: $errorText]"
                                msg.copy(content = withErr, isStreaming = false)
                            } else msg
                        }
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
            _conversations.value = list.map { it.toConversation() }
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

        val COPILOT_MODELS = listOf(
            AiModel(
                id = "claude-3.7-sonnet",
                displayName = "Claude 3.7 Sonnet (Copilot)",
                family = ModelFamily.CLAUDE,
                supportsThinking = true,
                description = "Anthropic's flagship hybrid reasoning model on GitHub Copilot",
                isDefault = true
            ),
            AiModel(
                id = "claude-3.5-sonnet",
                displayName = "Claude 3.5 Sonnet (Copilot)",
                family = ModelFamily.CLAUDE,
                supportsThinking = false,
                description = "High intelligence coding model"
            ),
            AiModel(
                id = "gpt-4o",
                displayName = "GPT-4o (Copilot)",
                family = ModelFamily.OTHER,
                supportsThinking = false,
                description = "OpenAI's versatile multimodal model"
            ),
            AiModel(
                id = "o3-mini",
                displayName = "o3-mini (Copilot)",
                family = ModelFamily.OTHER,
                supportsThinking = true,
                description = "OpenAI's high-speed reasoning model for coding and math"
            ),
            AiModel(
                id = "o1",
                displayName = "o1 (Copilot)",
                family = ModelFamily.OTHER,
                supportsThinking = true,
                description = "OpenAI's advanced deep thinking model"
            ),
            AiModel(
                id = "gemini-2.0-flash",
                displayName = "Gemini 2.0 Flash (Copilot)",
                family = ModelFamily.GEMINI,
                supportsThinking = false,
                description = "Google's ultra-fast model via Copilot"
            )
        )
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
