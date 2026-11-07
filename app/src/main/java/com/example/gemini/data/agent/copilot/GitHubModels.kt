package com.example.gemini.data.agent.copilot

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubUser(
    val login: String,
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null
)

@Serializable
data class GitHubRepo(
    val name: String,
    @SerialName("full_name") val fullName: String,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("clone_url") val cloneUrl: String,
    @SerialName("private") val isPrivate: Boolean = false,
    val description: String? = null,
    @SerialName("default_branch") val defaultBranch: String? = "main",
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class DeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("expires_in") val expiresIn: Int = 900,
    val interval: Int = 5
)

@Serializable
data class OAuthTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class CopilotTokenResponse(
    val token: String,
    @SerialName("expires_at") val expiresAt: Long = 0L,
    val endpoints: Map<String, String>? = null,
    val sku: String? = null,
    @SerialName("copilot_plan") val copilotPlan: String? = null
)

data class CopilotAuthState(
    val isLoggedIn: Boolean = false,
    val user: GitHubUser? = null,
    val hasCopilotAccess: Boolean = false,
    val isChecking: Boolean = false,
    val planTier: String? = null,
    val error: String? = null
)

@Serializable
data class CopilotPlanDetails(
    val name: String? = null,
    val sku: String? = null,
    val tier: String? = null,
    @SerialName("chat_enabled") val chatEnabled: Boolean? = true
)

@Serializable
data class CopilotInternalUserResponse(
    val login: String? = null,
    val id: Long? = null,
    @SerialName("copilot_plan") val copilotPlan: CopilotPlanDetails? = null,
    val sku: String? = null
)

@Serializable
data class CopilotChatMessagePayload(
    val role: String,
    val content: String
)

@Serializable
data class CopilotChatRequest(
    val model: String,
    val stream: Boolean = true,
    val messages: List<CopilotChatMessagePayload>,
    val temperature: Double? = 0.2
)

@Serializable
data class CopilotChatMessage(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class CopilotChatDelta(
    val role: String? = null,
    val content: String? = null,
    val text: String? = null
)

@Serializable
data class CopilotChatChoice(
    val index: Int = 0,
    val delta: CopilotChatDelta? = null,
    val message: CopilotChatMessage? = null,
    val text: String? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class CopilotChatChunk(
    val id: String? = null,
    val choices: List<CopilotChatChoice> = emptyList()
)

@Serializable
data class CopilotModelPolicy(
    val state: String? = null
)

@Serializable
data class CopilotModelCapabilities(
    val type: String? = null,
    val family: String? = null,
    @SerialName("supports_streaming") val supportsStreaming: Boolean? = null
)

@Serializable
data class CopilotModelInfo(
    val id: String,
    val name: String? = null,
    val version: String? = null,
    val vendor: String? = null,
    val policy: CopilotModelPolicy? = null,
    @SerialName("supported_endpoints") val supportedEndpoints: List<String>? = null,
    val capabilities: CopilotModelCapabilities? = null
) {
    /**
     * Identifies models included with standard Copilot subscription plans without additional per-query AI credits.
     */
    fun isIncludedFree(): Boolean {
        val lower = id.lowercase()
        return lower.contains("gpt-4o") ||
               lower.contains("claude-3.5-sonnet") ||
               lower.contains("claude-3-5-sonnet") ||
               lower.contains("o3-mini") ||
               lower == "gpt-4" ||
               lower.contains("gpt-4o-mini")
    }

    fun isChatSupported(): Boolean {
        if (policy?.state != null && !policy.state.equals("enabled", ignoreCase = true)) {
            return false
        }
        val endpoints = supportedEndpoints ?: return true
        return endpoints.isEmpty() || endpoints.any { it.contains("chat", ignoreCase = true) }
    }

    fun displayName(): String {
        if (!name.isNullOrBlank()) return name
        return when (id) {
            "gpt-4o" -> "GPT-4o"
            "gpt-4o-mini" -> "GPT-4o Mini"
            "claude-3.5-sonnet", "claude-3-5-sonnet" -> "Claude 3.5 Sonnet"
            "o1" -> "o1"
            "o3-mini" -> "o3-mini"
            "claude-3.7-sonnet" -> "Claude 3.7 Sonnet"
            else -> id.split("-").joinToString(" ") { word -> word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } }
        }
    }
}

@Serializable
data class CopilotModelsResponse(
    val data: List<CopilotModelInfo> = emptyList()
)

val DEFAULT_COPILOT_FALLBACK_MODELS = listOf(
    CopilotModelInfo(id = "gpt-4o", name = "GPT-4o", vendor = "Azure OpenAI"),
    CopilotModelInfo(id = "claude-3.5-sonnet", name = "Claude 3.5 Sonnet", vendor = "Anthropic"),
    CopilotModelInfo(id = "o3-mini", name = "o3-mini", vendor = "OpenAI"),
    CopilotModelInfo(id = "gpt-4o-mini", name = "GPT-4o Mini", vendor = "Azure OpenAI"),
    CopilotModelInfo(id = "o1", name = "o1", vendor = "OpenAI")
)
