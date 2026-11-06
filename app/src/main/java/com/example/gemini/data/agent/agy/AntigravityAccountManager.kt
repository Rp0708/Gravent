package com.example.gemini.data.agent.agy

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import com.example.gemini.data.daemon.IdeApiClient
import com.example.gemini.data.local.LocalEnvironmentManager
import com.example.gemini.data.remote.AgyHubClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedAgyAccount(
    val id: String,
    val email: String,
    val displayName: String,
    val profilePictureUrl: String? = null,
    val userTier: String = "",
    val availablePromptCredits: Long? = null,
    val availableFlowCredits: Long? = null,
    val tokenJson: String,
    val lastUsedTimestamp: Long = System.currentTimeMillis()
)

object AntigravityAccountManager {
    private const val TAG = "AgyAccountManager"
    private const val PREFS_NAME = "gravent_agy_accounts"
    private const val KEY_ACCOUNTS = "saved_accounts_v1"
    private const val TOKEN_FILE_REL = "~/.gemini/jetski-standalone-oauth-token"

    private val _savedAccounts = MutableStateFlow<List<SavedAgyAccount>>(emptyList())
    val savedAccounts: StateFlow<List<SavedAgyAccount>> = _savedAccounts.asStateFlow()

    private val _activeAccountId = MutableStateFlow<String?>(null)
    val activeAccountId: StateFlow<String?> = _activeAccountId.asStateFlow()

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            loadAccounts()
        }
    }

    private fun loadAccounts() {
        val p = prefs ?: return
        val raw = p.getString(KEY_ACCOUNTS, null) ?: return
        try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SavedAgyAccount>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedAgyAccount(
                        id = obj.getString("id"),
                        email = obj.optString("email", ""),
                        displayName = obj.optString("displayName", ""),
                        profilePictureUrl = obj.optString("profilePictureUrl").takeIf { it.isNotBlank() },
                        userTier = obj.optString("userTier", ""),
                        availablePromptCredits = if (obj.has("availablePromptCredits")) obj.getLong("availablePromptCredits") else null,
                        availableFlowCredits = if (obj.has("availableFlowCredits")) obj.getLong("availableFlowCredits") else null,
                        tokenJson = obj.getString("tokenJson"),
                        lastUsedTimestamp = obj.optLong("lastUsedTimestamp", System.currentTimeMillis())
                    )
                )
            }
            _savedAccounts.value = list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading saved accounts", e)
        }
    }

    private fun persistAccounts() {
        val p = prefs ?: return
        try {
            val arr = JSONArray()
            for (acc in _savedAccounts.value) {
                val obj = JSONObject().apply {
                    put("id", acc.id)
                    put("email", acc.email)
                    put("displayName", acc.displayName)
                    acc.profilePictureUrl?.let { put("profilePictureUrl", it) }
                    put("userTier", acc.userTier)
                    acc.availablePromptCredits?.let { put("availablePromptCredits", it) }
                    acc.availableFlowCredits?.let { put("availableFlowCredits", it) }
                    put("tokenJson", acc.tokenJson)
                    put("lastUsedTimestamp", acc.lastUsedTimestamp)
                }
                arr.put(obj)
            }
            p.edit().putString(KEY_ACCOUNTS, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error persisting accounts", e)
        }
    }

    suspend fun readTokenFileFromDisk(context: Context): String? = withContext(Dispatchers.IO) {
        try {
            val ideContent = IdeApiClient.readFile(TOKEN_FILE_REL)
            if (!ideContent.isNullOrBlank()) return@withContext ideContent
        } catch (_: Exception) {}

        val candidatePaths = listOf(
            File(LocalEnvironmentManager.getHomeDir(context), ".gemini/jetski-standalone-oauth-token"),
            File("/data/data/com.termux/files/home/.gemini/jetski-standalone-oauth-token"),
            File("/data/user/0/com.termux/files/home/.gemini/jetski-standalone-oauth-token"),
            File(System.getProperty("user.home") ?: "", ".gemini/jetski-standalone-oauth-token")
        )

        for (f in candidatePaths) {
            try {
                if (f.exists() && f.isFile && f.length() > 0) {
                    val content = f.readText()
                    if (content.isNotBlank()) return@withContext content
                }
            } catch (_: Exception) {}
        }
        null
    }

    suspend fun writeTokenFileToDisk(context: Context, tokenJson: String): Boolean = withContext(Dispatchers.IO) {
        var written = false
        val candidateDirs = listOf(
            File(LocalEnvironmentManager.getHomeDir(context), ".gemini"),
            File("/data/data/com.termux/files/home/.gemini"),
            File("/data/user/0/com.termux/files/home/.gemini")
        )

        for (dir in candidateDirs) {
            try {
                if (dir.exists() || dir.mkdirs()) {
                    val file = File(dir, "jetski-standalone-oauth-token")
                    file.writeText(tokenJson)
                    file.setReadable(true, false)
                    file.setWritable(true, false)
                    written = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed writing to ${dir.absolutePath}: ${e.message}")
            }
        }

        try {
            val savedViaIde = IdeApiClient.saveFile(TOKEN_FILE_REL, tokenJson)
            written = written || savedViaIde
        } catch (e: Exception) {
            Log.w(TAG, "IdeApiClient saveFile error: ${e.message}")
        }

        written
    }

    suspend fun syncCurrentAccount(context: Context, authInfo: AgyHubClient.AgyAuthInfo) = withContext(Dispatchers.IO) {
        if (!authInfo.isLoggedIn || authInfo.email.isBlank()) return@withContext
        val tokenContent = readTokenFileFromDisk(context) ?: return@withContext

        val accountId = authInfo.email.trim().lowercase()
        val currentList = _savedAccounts.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.id == accountId || it.email.equals(authInfo.email, ignoreCase = true) }

        val updatedAccount = SavedAgyAccount(
            id = accountId,
            email = authInfo.email,
            displayName = authInfo.displayName.ifBlank { authInfo.email.substringBefore('@') },
            profilePictureUrl = authInfo.profilePictureUrl,
            userTier = authInfo.userTier.ifBlank { authInfo.planName },
            availablePromptCredits = null,
            availableFlowCredits = null,
            tokenJson = tokenContent,
            lastUsedTimestamp = System.currentTimeMillis()
        )

        if (existingIndex >= 0) {
            currentList[existingIndex] = updatedAccount
        } else {
            currentList.add(updatedAccount)
        }

        _savedAccounts.value = currentList
        _activeAccountId.value = accountId
        persistAccounts()
        Log.d(TAG, "Synced account: ${authInfo.email} (total accounts: ${currentList.size})")
    }

    suspend fun switchAccount(
        context: Context,
        targetAccount: SavedAgyAccount,
        onComplete: suspend () -> Unit
    ) = withContext(Dispatchers.IO) {
        val success = writeTokenFileToDisk(context, targetAccount.tokenJson)
        if (success) {
            _activeAccountId.value = targetAccount.id
            val currentList = _savedAccounts.value.toMutableList()
            val idx = currentList.indexOfFirst { it.id == targetAccount.id }
            if (idx >= 0) {
                currentList[idx] = targetAccount.copy(lastUsedTimestamp = System.currentTimeMillis())
                _savedAccounts.value = currentList
                persistAccounts()
            }
            onComplete()
        }
    }

    fun removeAccount(accountId: String) {
        val currentList = _savedAccounts.value.filter { it.id != accountId }
        _savedAccounts.value = currentList
        if (_activeAccountId.value == accountId) {
            _activeAccountId.value = currentList.firstOrNull()?.id
        }
        persistAccounts()
    }

    suspend fun restoreLastActiveAccount(context: Context): Boolean = withContext(Dispatchers.IO) {
        val activeId = _activeAccountId.value
        val account = _savedAccounts.value.firstOrNull { it.id == activeId }
            ?: _savedAccounts.value.maxByOrNull { it.lastUsedTimestamp }
        if (account != null) {
            writeTokenFileToDisk(context, account.tokenJson)
        } else {
            false
        }
    }
}
