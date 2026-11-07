package com.example.gemini.data.agent.copilot

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CopilotPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _githubToken = MutableStateFlow(prefs.getString(KEY_GITHUB_TOKEN, null))
    val githubToken: StateFlow<String?> = _githubToken.asStateFlow()

    private val _copilotToken = MutableStateFlow(prefs.getString(KEY_COPILOT_TOKEN, null))
    val copilotToken: StateFlow<String?> = _copilotToken.asStateFlow()

    private val _copilotExpiresAt = MutableStateFlow(prefs.getLong(KEY_COPILOT_EXPIRES_AT, 0L))
    val copilotExpiresAt: StateFlow<Long> = _copilotExpiresAt.asStateFlow()

    private val _userLogin = MutableStateFlow(prefs.getString(KEY_USER_LOGIN, null))
    val userLogin: StateFlow<String?> = _userLogin.asStateFlow()

    private val _userName = MutableStateFlow(prefs.getString(KEY_USER_NAME, null))
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userAvatarUrl = MutableStateFlow(prefs.getString(KEY_USER_AVATAR, null))
    val userAvatarUrl: StateFlow<String?> = _userAvatarUrl.asStateFlow()

    private val _selectedModel = MutableStateFlow(
        prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL).let {
            // Self-heal: if stored model was nonexistent claude-3.7 or unsupported, fallback to DEFAULT_MODEL
            if (it == "claude-3.7-sonnet" || it.isNullOrBlank()) DEFAULT_MODEL else it
        }
    )
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _availableModelsJson = MutableStateFlow(prefs.getString(KEY_AVAILABLE_MODELS, null))
    val availableModelsJson: StateFlow<String?> = _availableModelsJson.asStateFlow()

    fun setAvailableModelsJson(jsonStr: String?) {
        prefs.edit().putString(KEY_AVAILABLE_MODELS, jsonStr).apply()
        _availableModelsJson.value = jsonStr
    }

    fun setGithubToken(token: String?) {
        prefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
        _githubToken.value = token
    }

    fun setCopilotToken(token: String?, expiresAt: Long) {
        prefs.edit()
            .putString(KEY_COPILOT_TOKEN, token)
            .putLong(KEY_COPILOT_EXPIRES_AT, expiresAt)
            .apply()
        _copilotToken.value = token
        _copilotExpiresAt.value = expiresAt
    }

    fun setUserProfile(login: String?, name: String?, avatarUrl: String?) {
        prefs.edit()
            .putString(KEY_USER_LOGIN, login)
            .putString(KEY_USER_NAME, name)
            .putString(KEY_USER_AVATAR, avatarUrl)
            .apply()
        _userLogin.value = login
        _userName.value = name
        _userAvatarUrl.value = avatarUrl
    }

    fun setSelectedModel(modelId: String) {
        prefs.edit().putString(KEY_SELECTED_MODEL, modelId).apply()
        _selectedModel.value = modelId
    }

    fun clearAuth() {
        prefs.edit()
            .remove(KEY_GITHUB_TOKEN)
            .remove(KEY_COPILOT_TOKEN)
            .remove(KEY_COPILOT_EXPIRES_AT)
            .remove(KEY_USER_LOGIN)
            .remove(KEY_USER_NAME)
            .remove(KEY_USER_AVATAR)
            .remove(KEY_AVAILABLE_MODELS)
            .apply()
        _githubToken.value = null
        _copilotToken.value = null
        _copilotExpiresAt.value = 0L
        _userLogin.value = null
        _userName.value = null
        _userAvatarUrl.value = null
        _availableModelsJson.value = null
    }

    companion object {
        private const val PREFS = "copilot_prefs"
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_COPILOT_TOKEN = "copilot_token"
        private const val KEY_COPILOT_EXPIRES_AT = "copilot_expires_at"
        private const val KEY_USER_LOGIN = "user_login"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_USER_AVATAR = "user_avatar"
        private const val KEY_SELECTED_MODEL = "selected_model"
        private const val KEY_AVAILABLE_MODELS = "available_models_json"

        const val DEFAULT_MODEL = "gpt-4o"
    }
}
