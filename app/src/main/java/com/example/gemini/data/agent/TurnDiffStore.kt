package com.example.gemini.data.agent

import android.content.Context
import android.content.SharedPreferences
import com.example.gemini.data.daemon.GitFileStatus
import com.example.gemini.data.daemon.GitStatusResponse
import com.example.gemini.domain.model.TurnDiffStats
import com.example.gemini.domain.model.TurnFileDelta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var prefs: SharedPreferences? = null
    private var persistJob: Job? = null

    private val _perMessage = MutableStateFlow<Map<String, TurnDiffStats>>(emptyMap())
    val perMessage: StateFlow<Map<String, TurnDiffStats>> = _perMessage.asStateFlow()

    private val _perConversation = MutableStateFlow<Map<String, TurnDiffStats>>(emptyMap())
    val perConversation: StateFlow<Map<String, TurnDiffStats>> = _perConversation.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs = sp
        ioScope.launch {
            val raw = sp.getString(KEY_JSON, null)
            if (!raw.isNullOrBlank()) {
                runCatching {
                    val decoded = json.decodeFromString<PersistedTurnDiffCache>(raw)
                    synchronized(this@TurnDiffStore) {
                        if (_perMessage.value.isEmpty() && decoded.perMessage.isNotEmpty()) {
                            _perMessage.value = decoded.perMessage
                        }
                        if (_perConversation.value.isEmpty() && decoded.perConversation.isNotEmpty()) {
                            _perConversation.value = decoded.perConversation
                        }
                    }
                }
            }
        }
    }

    @Synchronized
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
            val mergedDeltas = mergeFileDeltas(prev?.fileDeltas.orEmpty(), stats.fileDeltas)
            val mergedFiles = if (mergedDeltas.isNotEmpty()) {
                mergedDeltas.map { it.path }.take(40)
            } else {
                (prev?.files.orEmpty() + stats.files).distinct().takeLast(40)
            }
            val merged = TurnDiffStats(
                additions = (prev?.additions ?: 0) + stats.additions,
                deletions = (prev?.deletions ?: 0) + stats.deletions,
                filesChanged = mergedFiles.size.coerceAtLeast(maxOf(prev?.filesChanged ?: 0, stats.filesChanged)),
                files = mergedFiles,
                fileDeltas = mergedDeltas.take(30),
                branch = stats.branch ?: prev?.branch,
                repoSlug = stats.repoSlug ?: prev?.repoSlug,
                projectPath = stats.projectPath ?: prev?.projectPath,
                timestampMs = maxOf(stats.timestampMs, prev?.timestampMs ?: 0L)
            )
            nextConvs.remove(conversationId)
            nextConvs[conversationId] = merged
            while (nextConvs.size > MAX_CONVERSATIONS) {
                val oldestKey = nextConvs.keys.firstOrNull() ?: break
                nextConvs.remove(oldestKey)
            }
            _perConversation.value = nextConvs
        }

        schedulePersist()
    }

    private fun mergeFileDeltas(existing: List<TurnFileDelta>, added: List<TurnFileDelta>): List<TurnFileDelta> {
        if (existing.isEmpty()) return added
        if (added.isEmpty()) return existing
        val map = LinkedHashMap<String, TurnFileDelta>(existing.size + added.size)
        for (d in existing) {
            map[d.path] = d
        }
        for (d in added) {
            val prev = map[d.path]
            map[d.path] = if (prev == null) d else d.copy(
                additions = prev.additions + d.additions,
                deletions = prev.deletions + d.deletions
            )
        }
        return map.values.sortedByDescending { it.additions + it.deletions }
    }

    fun computeTurnDelta(
        pre: GitStatusResponse?,
        post: GitStatusResponse?,
        projectPath: String? = null
    ): TurnDiffStats? {
        if (post == null || !post.isGitRepo) return null

        data class FileAcc(
            val additions: Int,
            val deletions: Int,
            val status: String,
            val staged: Boolean
        )

        fun aggregateByPath(status: GitStatusResponse): Map<String, FileAcc> {
            val map = LinkedHashMap<String, FileAcc>()
            fun addAll(list: List<GitFileStatus>) {
                for (f in list) {
                    val cur = map[f.path]
                    map[f.path] = if (cur == null) {
                        FileAcc(f.additions, f.deletions, f.status, f.staged)
                    } else {
                        FileAcc(
                            additions = cur.additions + f.additions,
                            deletions = cur.deletions + f.deletions,
                            status = if (cur.status == "U") f.status else cur.status,
                            staged = cur.staged || f.staged
                        )
                    }
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
        val fileDeltas = mutableListOf<TurnFileDelta>()

        for ((path, postAcc) in postMap) {
            val preAcc = preMap[path]
            val dA = (postAcc.additions - (preAcc?.additions ?: 0)).coerceAtLeast(0)
            val dD = (postAcc.deletions - (preAcc?.deletions ?: 0)).coerceAtLeast(0)
            val isNew = preAcc == null
            if (dA > 0 || dD > 0 || isNew) {
                addDelta += dA
                delDelta += dD
                fileDeltas.add(
                    TurnFileDelta(
                        path = path,
                        status = if (isNew && postAcc.status == "U") "A" else postAcc.status,
                        additions = dA,
                        deletions = dD,
                        staged = postAcc.staged
                    )
                )
            }
        }

        if (addDelta == 0 && delDelta == 0 && fileDeltas.isEmpty()) return null
        val sortedDeltas = fileDeltas.sortedByDescending { it.additions + it.deletions }.take(30)
        return TurnDiffStats(
            additions = addDelta,
            deletions = delDelta,
            filesChanged = sortedDeltas.size,
            files = sortedDeltas.map { it.path },
            fileDeltas = sortedDeltas,
            branch = post.branch.takeIf { it.isNotBlank() },
            repoSlug = post.repoSlug,
            projectPath = projectPath?.takeIf { it.isNotBlank() },
            timestampMs = System.currentTimeMillis()
        )
    }

    private fun schedulePersist() {
        val sp = prefs ?: return
        persistJob?.cancel()
        persistJob = ioScope.launch {
            delay(250)
            val snapshot = synchronized(this@TurnDiffStore) {
                PersistedTurnDiffCache(
                    perMessage = _perMessage.value,
                    perConversation = _perConversation.value
                )
            }
            runCatching {
                val payload = json.encodeToString(snapshot)
                sp.edit().putString(KEY_JSON, payload).apply()
            }
        }
    }
}
