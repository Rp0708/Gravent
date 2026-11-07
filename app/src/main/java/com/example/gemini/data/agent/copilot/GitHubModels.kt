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
    val endpoints: Map<String, String>? = null
)

data class CopilotAuthState(
    val isLoggedIn: Boolean = false,
    val user: GitHubUser? = null,
    val hasCopilotAccess: Boolean = false,
    val isChecking: Boolean = false,
    val error: String? = null
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
    val temperature: Double = 0.2
)

@Serializable
data class CopilotChatDelta(
    val role: String? = null,
    val content: String? = null
)

@Serializable
data class CopilotChatChoice(
    val index: Int = 0,
    val delta: CopilotChatDelta? = null,
    @SerialName("finish_reason") val finishReason: String? = null
)

@Serializable
data class CopilotChatChunk(
    val id: String? = null,
    val choices: List<CopilotChatChoice> = emptyList()
)
