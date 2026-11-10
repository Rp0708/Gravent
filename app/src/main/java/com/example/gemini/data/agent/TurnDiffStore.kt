package com.example.gemini.data.agent

import android.content.Context
import android.content.SharedPreferences
import com.example.gemini.data.daemon.GitStatusResponse
import com.example.gemini.domain.model.TurnDiffStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class PersistedTurnDiffCache(
    val perMessage: Map<String, TurnDiffStats> = emptyMap(),
    val perConversation: Map<String, TurnDiffStats> = emptyMap()
)

object TurnDiffStore {
    private const val PREFS_NAME = "gravent_turn_diff_stats"
    private const val KEY_JSON = "turn_diffs_v1"
    private const val MAX_MESSAGES = 300
    private const val MAX_CONVERSATIONS = 120

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    private var prefs: SharedPreferences? = null

    private val _perMessage = MutableStateFlow<Map<String, TurnDiffStats>>(emptyMap())
    val perMessage: StateFlow<Map<String, TurnDiffStats>> = _perMessage.asStateFlow()

    private val _perConversation = MutableStateFlow<Map<String, TurnDiffStats>>(emptyMap())
    val perConversation: StateFlow<Map<String, TurnDiffStats>> = _perConversation.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        val raw = sp.getString(KEY_JSON, null)
        if (!raw.isNullOrBlank()) {
            runCatching {
                val decoded = json.decodeFromString<PersistedTurnDiffCache>(raw)
                _perMessage.value = decoded.perMessage
                _perConversation.value = decoded.perConversation
            }
        }
    }

    fun recordTurnDiff(conversationId: String, messageId: String?, stats: TurnDiffStats) {
        if (stats.additions <= 0 && stats.deletions <= 0 && stats.filesChanged <= 0) return

        if (!messageId.isNullOrBlank()) {
            val nextMsgs = LinkedHashMap(_perMessage.value)
            nextMsgs[messageId] = stats
            while (nextMsgs.size > MAX_MESSAGES) {
                val oldestKey = nextMsgs.keys.firstOrNull() ?: break
                nextMsgs.remove(oldestKey)
            }
            _perMessage.value = nextMsgs
        }

        if (conversationId.isNotBlank()) {
            val nextConvs = LinkedHashMap(_perConversation.value)
            val prev = nextConvs[conversationId]
            val mergedFiles = if (prev != null) {
                (prev.files + stats.files).distinct().takeLast(40)
            } else {
                stats.files.distinct().takeLast(40)
            }
            val merged = TurnDiffStats(
                additions = (prev?.additions ?: 0) + stats.additions,
                deletions = (prev?.deletions ?: 0) + stats.deletions,
                filesChanged = mergedFiles.size.coerceAtLeast(maxOf(prev?.filesChanged ?: 0, stats.filesChanged)),
                files = mergedFiles
            )
            nextConvs.remove(conversationId)
            nextConvs[conversationId] = merged
            while (nextConvs.size > MAX_CONVERSATIONS) {
                val oldestKey = nextConvs.keys.firstOrNull() ?: break
                nextConvs.remove(oldestKey)
            }
            _perConversation.value = nextConvs
        }

        persistAsync()
    }

    fun computeTurnDelta(pre: GitStatusResponse?, post: GitStatusResponse?): TurnDiffStats? {
        if (post == null || !post.isGitRepo) return null

        fun aggregateByPath(status: GitStatusResponse): Map<String, Pair<Int, Int>> {
            val map = LinkedHashMap<String, Pair<Int, Int>>()
            fun addAll(list: List<com.example.gemini.data.daemon.GitFileStatus>) {
                for (f in list) {
                    val cur = map[f.path] ?: (0 to 0)
                    map[f.path] = (cur.first + f.additions) to (cur.second + f.deletions)
                }
            }
            addAll(status.stagedFiles)
            addAll(status.unstagedFiles)
            addAll(status.untrackedFiles)
            return map
        }

        val postMap = aggregateByPath(post)
        val preMap = if (pre != null && pre.isGitRepo) aggregateByPath(pre) else emptyMap()

        var addDelta = 0
        var delDelta = 0
        val changedFiles = mutableListOf<String>()

        for ((path, postPair) in postMap) {
            val prePair = preMap[path] ?: (0 to 0)
            val dA = (postPair.first - prePair.first).coerceAtLeast(0)
            val dD = (postPair.second - prePair.second).coerceAtLeast(0)
            if (dA > 0 || dD > 0 || (path !in preMap)) {
                addDelta += dA
                delDelta += dD
                changedFiles.add(path)
            }
        }

        if (addDelta == 0 && delDelta == 0 && changedFiles.isEmpty()) return null
        return TurnDiffStats(
            additions = addDelta,
            deletions = delDelta,
            filesChanged = changedFiles.size,
            files = changedFiles.take(30)
        )
    }

    private fun persistAsync() {
        val sp = prefs ?: return
        runCatching {
            val payload = json.encodeToString(
                PersistedTurnDiffCache(
                    perMessage = _perMessage.value,
                    perConversation = _perConversation.value
                )
            )
            sp.edit().putString(KEY_JSON, payload).apply()
        }
    }
}
