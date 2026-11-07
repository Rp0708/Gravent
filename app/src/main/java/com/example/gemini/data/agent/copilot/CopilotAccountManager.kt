package com.example.gemini.data.agent.copilot

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class CopilotAccountManager(
    private val context: Context,
    private val scope: CoroutineScope,
    val prefs: CopilotPreferences,
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val _authState = MutableStateFlow(
        CopilotAuthState(
            isLoggedIn = !prefs.githubToken.value.isNullOrBlank(),
            user = if (!prefs.userLogin.value.isNullOrBlank()) {
                GitHubUser(
                    login = prefs.userLogin.value!!,
                    name = prefs.userName.value,
                    avatarUrl = prefs.userAvatarUrl.value
                )
            } else null
        )
    )
    val authState: StateFlow<CopilotAuthState> = _authState.asStateFlow()

    private val _deviceCodeFlow = MutableStateFlow<DeviceCodeResponse?>(null)
    val deviceCodeFlow: StateFlow<DeviceCodeResponse?> = _deviceCodeFlow.asStateFlow()

    private val _userRepos = MutableStateFlow<List<GitHubRepo>>(emptyList())
    val userRepos: StateFlow<List<GitHubRepo>> = _userRepos.asStateFlow()

    private val _isLoadingRepos = MutableStateFlow(false)
    val isLoadingRepos: StateFlow<Boolean> = _isLoadingRepos.asStateFlow()

    private var pollingJob: Job? = null

    init {
        if (!prefs.githubToken.value.isNullOrBlank()) {
            refreshStatus()
        }
    }

    fun refreshStatus() {
        val token = prefs.githubToken.value
        if (token.isNullOrBlank()) {
            _authState.value = CopilotAuthState(isLoggedIn = false)
            return
        }
        scope.launch(Dispatchers.IO) {
            _authState.value = _authState.value.copy(isChecking = true, error = null)
            try {
                val user = fetchUserDirect(token)
                if (user != null) {
                    prefs.setUserProfile(user.login, user.name, user.avatarUrl)
                }
                val copilotToken = ensureValidCopilotTokenInternal(token)
                _authState.value = CopilotAuthState(
                    isLoggedIn = true,
                    user = user ?: _authState.value.user,
                    hasCopilotAccess = copilotToken != null,
                    isChecking = false,
                    error = if (copilotToken == null) "No active GitHub Copilot subscription found" else null
                )
                if (copilotToken != null) {
                    fetchUserRepos()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh Copilot status", e)
                _authState.value = _authState.value.copy(
                    isChecking = false,
                    error = e.message ?: "Failed to verify GitHub Copilot status"
                )
            }
        }
    }

    suspend fun startDeviceFlow(): Result<DeviceCodeResponse> = withContext(Dispatchers.IO) {
        try {
            val body = "client_id=$CLIENT_ID&scope=read:user,copilot,repo"
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url("https://github.com/login/device/code")
                .header("Accept", "application/json")
                .post(body)
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("Failed to request device code: ${response.code} $respBody"))
            }

            val deviceCodeResp = json.decodeFromString<DeviceCodeResponse>(respBody)
            _deviceCodeFlow.value = deviceCodeResp
            Result.success(deviceCodeResp)
        } catch (e: Exception) {
            Log.e(TAG, "Device flow error", e)
            Result.failure(e)
        }
    }

    fun startPollingForToken(
        deviceCode: String,
        intervalSeconds: Int = 5,
        onComplete: (Boolean) -> Unit = {}
    ) {
        pollingJob?.cancel()
        pollingJob = scope.launch(Dispatchers.IO) {
            val intervalMs = (intervalSeconds.coerceAtLeast(5)) * 1000L
            val startTime = System.currentTimeMillis()
            val maxDurationMs = 15 * 60 * 1000L // 15 mins

            while (isActive && System.currentTimeMillis() - startTime < maxDurationMs) {
                delay(intervalMs)
                try {
                    val body = "client_id=$CLIENT_ID&device_code=$deviceCode&grant_type=urn:ietf:params:oauth:grant-type:device_code"
                        .toRequestBody("application/x-www-form-urlencoded".toMediaType())
                    val request = Request.Builder()
                        .url("https://github.com/login/oauth/access_token")
                        .header("Accept", "application/json")
                        .post(body)
                        .build()

                    val response = httpClient.newCall(request).execute()
                    val respBody = response.body?.string().orEmpty()
                    val tokenResp = json.decodeFromString<OAuthTokenResponse>(respBody)

                    if (!tokenResp.accessToken.isNullOrBlank()) {
                        val token = tokenResp.accessToken
                        prefs.setGithubToken(token)
                        _deviceCodeFlow.value = null
                        refreshStatus()
                        withContext(Dispatchers.Main) { onComplete(true) }
                        return@launch
                    }

                    if (tokenResp.error == "slow_down") {
                        delay(5000)
                    } else if (tokenResp.error != null && tokenResp.error != "authorization_pending") {
                        Log.e(TAG, "Device polling error: ${tokenResp.errorDescription ?: tokenResp.error}")
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error polling access token", e)
                }
            }
            withContext(Dispatchers.Main) { onComplete(false) }
        }
    }

    fun cancelDeviceFlow() {
        pollingJob?.cancel()
        _deviceCodeFlow.value = null
    }

    fun loginWithDirectToken(token: String, onComplete: (Boolean, String?) -> Unit) {
        val cleanToken = token.trim()
        if (cleanToken.isBlank()) {
            onComplete(false, "Token cannot be empty")
            return
        }
        scope.launch(Dispatchers.IO) {
            _authState.value = _authState.value.copy(isChecking = true, error = null)
            try {
                val user = fetchUserDirect(cleanToken)
                if (user == null) {
                    _authState.value = _authState.value.copy(isChecking = false, error = "Invalid GitHub token")
                    withContext(Dispatchers.Main) { onComplete(false, "Invalid GitHub token") }
                    return@launch
                }
                prefs.setGithubToken(cleanToken)
                prefs.setUserProfile(user.login, user.name, user.avatarUrl)

                val copilotToken = ensureValidCopilotTokenInternal(cleanToken)
                _authState.value = CopilotAuthState(
                    isLoggedIn = true,
                    user = user,
                    hasCopilotAccess = copilotToken != null,
                    isChecking = false,
                    error = if (copilotToken == null) "No active GitHub Copilot subscription found" else null
                )
                if (copilotToken != null) {
                    fetchUserRepos()
                }
                withContext(Dispatchers.Main) {
                    onComplete(true, if (copilotToken == null) "Signed in, but no Copilot subscription detected." else null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Direct login error", e)
                _authState.value = _authState.value.copy(isChecking = false, error = e.message)
                withContext(Dispatchers.Main) { onComplete(false, e.message) }
            }
        }
    }

    suspend fun ensureValidCopilotToken(): String? = withContext(Dispatchers.IO) {
        val ghToken = prefs.githubToken.value ?: return@withContext null
        ensureValidCopilotTokenInternal(ghToken)
    }

    private suspend fun ensureValidCopilotTokenInternal(ghToken: String): String? {
        val cachedToken = prefs.copilotToken.value
        val expiresAt = prefs.copilotExpiresAt.value
        val nowSeconds = System.currentTimeMillis() / 1000L

        // Reuse if valid for at least another 2 minutes
        if (!cachedToken.isNullOrBlank() && expiresAt > nowSeconds + 120) {
            return cachedToken
        }

        return try {
            val request = Request.Builder()
                .url("https://api.github.com/copilot_internal/v2/token")
                .header("Authorization", "token $ghToken")
                .header("User-Agent", "GithubCopilot/1.155.0")
                .header("Editor-Version", "vscode/1.96.0")
                .header("Editor-Plugin-Version", "copilot-chat/0.24.1")
                .header("Accept", "application/json")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val respBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.w(TAG, "Copilot token exchange failed: ${response.code} $respBody")
                return null
            }

            val tokenObj = json.decodeFromString<CopilotTokenResponse>(respBody)
            prefs.setCopilotToken(tokenObj.token, tokenObj.expiresAt)
            tokenObj.token
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Copilot token", e)
            null
        }
    }

    private fun fetchUserDirect(token: String): GitHubUser? {
        return try {
            val request = Request.Builder()
                .url("https://api.github.com/user")
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/vnd.github+json")
                .get()
                .build()
            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) return null
            val respBody = response.body?.string().orEmpty()
            json.decodeFromString<GitHubUser>(respBody)
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching user info", e)
            null
        }
    }

    fun fetchUserRepos() {
        val token = prefs.githubToken.value ?: return
        scope.launch(Dispatchers.IO) {
            _isLoadingRepos.value = true
            try {
                val request = Request.Builder()
                    .url("https://api.github.com/user/repos?sort=updated&per_page=60")
                    .header("Authorization", "Bearer $token")
                    .header("Accept", "application/vnd.github+json")
                    .get()
                    .build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val respBody = response.body?.string().orEmpty()
                    val repos = json.decodeFromString<List<GitHubRepo>>(respBody)
                    _userRepos.value = repos
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching repos", e)
            } finally {
                _isLoadingRepos.value = false
            }
        }
    }

    fun logout() {
        pollingJob?.cancel()
        _deviceCodeFlow.value = null
        prefs.clearAuth()
        _userRepos.value = emptyList()
        _authState.value = CopilotAuthState(isLoggedIn = false)
    }

    companion object {
        private const val TAG = "CopilotAccountManager"
        // Standard GitHub CLI / Copilot Client ID widely used for device flow
        const val CLIENT_ID = "0170a3f47e3845ac4244"
    }
}
