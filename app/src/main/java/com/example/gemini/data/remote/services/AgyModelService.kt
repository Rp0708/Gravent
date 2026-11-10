package com.example.gemini.data.remote.services

import android.util.Log
import com.example.gemini.data.preferences.AuthPreferences

import com.example.gemini.domain.model.AiModel
import com.example.gemini.domain.model.ModelFamily
import com.example.gemini.domain.model.ModelQuotaGroup
import com.example.gemini.domain.model.QuotaSummaryResponse
import com.example.gemini.domain.model.QuotaWindowInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

import exa.language_server_pb.GetAvailableModelsRequest
import exa.language_server_pb.Model
import exa.language_server_pb.QuotaSummaryBucket
import exa.language_server_pb.RetrieveUserQuotaSummaryRequest

data class PerModelApiQuota(
    val remainingFraction: Float,
    val resetTime: String? = null
)

/**
 * Dedicated RPC service for AI models and user quota telemetry.
 */
class AgyModelService {
    companion object {
        private const val TAG = "AgyModelService"
        val instance by lazy { AgyModelService() }

        private val ISO_FORMAT = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private val keyToModelEnum = ConcurrentHashMap<String, String>()
        private val nameToModelEnum = ConcurrentHashMap<String, String>()
        private val allValidEnums = CopyOnWriteArraySet<String>()
        private val perModelApiQuotas = ConcurrentHashMap<String, PerModelApiQuota>()

        fun getCachedPerModelQuota(modelId: String, modelKey: String = ""): PerModelApiQuota? {
            perModelApiQuotas[modelId]?.let { return it }
            if (modelKey.isNotBlank()) perModelApiQuotas[modelKey]?.let { return it }
            return null
        }

        @Volatile
        private var defaultModelEnum: String = ""

        fun updateModelRegistry(models: List<AiModel>) {
            if (models.isEmpty()) return
            models.forEach { m ->
                allValidEnums.add(m.id)
                if (m.key.isNotBlank()) {
                    keyToModelEnum[m.key.lowercase()] = m.id
                }
                keyToModelEnum[m.displayName.lowercase()] = m.id
                if (m.baseName.isNotBlank()) {
                    nameToModelEnum[m.baseName.lowercase()] = m.id
                }
            }
            if (defaultModelEnum.isBlank() || !allValidEnums.contains(defaultModelEnum)) {
                defaultModelEnum = models.firstOrNull {
                    it.displayName.contains("flash", ignoreCase = true) || it.key.contains("flash", ignoreCase = true)
                }?.id ?: models.first().id
            }
        }

        fun resolveModelEnum(rawInput: String?): String {
            if (rawInput.isNullOrBlank()) return defaultModelEnum.ifBlank { "MODEL_PLACEHOLDER_M319" }
            if (allValidEnums.contains(rawInput)) return rawInput

            val lower = rawInput.lowercase().trim()
            keyToModelEnum[lower]?.let { return it }
            nameToModelEnum[lower]?.let { return it }

            for ((k, v) in keyToModelEnum) {
                if (lower.contains(k) || k.contains(lower)) return v
            }

            if (rawInput.startsWith("MODEL_")) return rawInput
            return defaultModelEnum.ifBlank { rawInput }
        }

        fun formatQuotaResetCountdown(isoString: String?): String {
            if (isoString.isNullOrBlank()) return ""
            return try {
                val cleanIso = if (isoString.length > 19) isoString.substring(0, 19) else isoString
                val targetTime = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(cleanIso)?.time ?: return ""

                val now = System.currentTimeMillis()
                val diffMs = targetTime - now
                if (diffMs <= 0) return "Resetting soon"

                val hours = (diffMs / (1000 * 60 * 60)).toInt()
                val mins = ((diffMs % (1000 * 60 * 60)) / (1000 * 60)).toInt()
                val parts = mutableListOf<String>()
                if (hours > 0) parts.add("${hours}h")
                if (mins > 0 || hours == 0) parts.add("${mins}m")
                "Resets in " + parts.joinToString(" ")
            } catch (e: Exception) {
                ""
            }
        }
    }

    /**
     * Fetches all available models from daemon via typed AgyLanguageService and maps them to AiModel.
     */
    suspend fun getAvailableModels(
        forceRefresh: Boolean = false,
        hubUrl: String = AuthPreferences.currentHubUrl
    ): Result<List<AiModel>> = withContext(Dispatchers.IO) {
        try {
            val req = GetAvailableModelsRequest(force_refresh = forceRefresh)
            val res = AgyLanguageService.GetAvailableModels().executeSafely(req)
            if (!res.isSuccess) {
                return@withContext Result.failure(res.exceptionOrNull() ?: Exception("Failed to fetch models"))
            }
            val response = res.getOrThrow().response
                ?: return@withContext Result.failure(Exception("Empty models response from server"))

            val modelsMap = response.models
            val sorts = response.agent_model_sorts.ifEmpty { response.battle_mode_model_sorts }
            val sortedIds = mutableListOf<String>()

            for (sort in sorts) {
                for (grp in sort.groups) {
                    for (mid in grp.model_ids) {
                        if (mid.isNotBlank() && !sortedIds.contains(mid)) {
                            sortedIds.add(mid)
                        }
                    }
                }
            }

            val keysToProcess = if (sortedIds.isNotEmpty()) {
                sortedIds
            } else {
                response.models.mapNotNull { (key, details) ->
                    if (!details.is_internal && !details.disabled && details.display_name.isNotBlank()) {
                        key
                    } else null
                }
            }

            val resultList = mutableListOf<AiModel>()
            val nextApiQuotas = HashMap<String, PerModelApiQuota>()
            val nowMs = System.currentTimeMillis()
            for (key in keysToProcess) {
                val details = modelsMap[key] ?: continue
                if (details.disabled) continue
                if (details.is_internal) continue

                val displayName = details.display_name.ifBlank { key }
                val modelEnum = details.model
                val modelEnumName = if (modelEnum != Model.MODEL_UNSPECIFIED) {
                    modelEnum.name
                } else {
                    key
                }
                val supportsThinking = details.supports_thinking

                var baseName = displayName
                var tier: String? = null
                val tierMatch =
                    Regex("^(.*?)\\s*\\((High|Medium|Low|Med|Thinking)\\)$", RegexOption.IGNORE_CASE).find(baseName)
                if (tierMatch != null) {
                    baseName = tierMatch.groupValues[1].trim()
                    tier =
                        tierMatch.groupValues[2].replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }

                val family = when {
                    displayName.contains("claude", ignoreCase = true) || key.contains(
                        "claude",
                        ignoreCase = true
                    ) -> ModelFamily.CLAUDE

                    else -> ModelFamily.GEMINI
                }

                // Extract per-model QuotaInfo when populated by the language server
                details.quota_info?.let { qi ->
                    val resetEpochMs = qi.reset_time?.let { ts -> ts.seconds * 1000L + (ts.nanos / 1_000_000L) }
                    val hasResetTime = qi.reset_time != null && resetEpochMs != null && resetEpochMs > 0L
                    if (qi.remaining_fraction > 0f || hasResetTime) {
                        val isDefinitelyExpired = hasResetTime && resetEpochMs!! + 60_000L < nowMs
                        val frac = if (isDefinitelyExpired) 1.0f else qi.remaining_fraction.coerceIn(0f, 1f)
                        val resetIso = if (hasResetTime && !isDefinitelyExpired) ISO_FORMAT.format(Date(resetEpochMs!!)) else null
                        val entry = PerModelApiQuota(remainingFraction = frac, resetTime = resetIso)
                        nextApiQuotas[modelEnumName] = entry
                        if (key.isNotBlank()) nextApiQuotas[key] = entry
                    }
                }

                resultList.add(
                    AiModel(
                        id = modelEnumName,
                        displayName = displayName,
                        family = family,
                        supportsThinking = supportsThinking,
                        description = details.description,
                        key = key,
                        baseName = baseName,
                        tier = tier
                    )
                )
            }

            if (nextApiQuotas.isNotEmpty()) {
                perModelApiQuotas.clear()
                perModelApiQuotas.putAll(nextApiQuotas)
            }
            updateModelRegistry(resultList)
            Result.success(resultList)
        } catch (e: Exception) {
            Log.e(TAG, "getAvailableModels failed: ${e.message}")
            Result.failure(e)
        }
    }

    private fun buildGroupFromBuckets(
        buckets: List<QuotaSummaryBucket>,
        dispName: String,
        desc: String,
        nowMs: Long
    ): ModelQuotaGroup? {
        var fiveHourInfo: QuotaWindowInfo? = null
        var weeklyInfo: QuotaWindowInfo? = null
        val fallbackWindows = mutableListOf<QuotaWindowInfo>()

        for (b in buckets) {
            if (b.disabled) continue
            val resetEpochMs = b.reset_time?.let { ts -> ts.seconds * 1000L + (ts.nanos / 1_000_000L) }
            val hasResetTime = b.reset_time != null && resetEpochMs != null && resetEpochMs > 0L
            // Proto3 default 0f without resetTime when remaining_amount > 0 means fraction wasn't populated
            if (b.remaining_fraction <= 0f && !hasResetTime && b.remaining_amount > 0L) continue

            val isDefinitelyExpired = hasResetTime && resetEpochMs!! + 60_000L < nowMs
            val remFraction = if (isDefinitelyExpired) 1.0f else b.remaining_fraction.coerceIn(0f, 1f)
            val remPct = String.format(Locale.US, "%.1f%%", remFraction * 100f)
            val usedPct = String.format(Locale.US, "%.1f%%", (1.0f - remFraction) * 100f)
            val resetTime = if (hasResetTime && !isDefinitelyExpired) ISO_FORMAT.format(Date(resetEpochMs!!)) else null
            val countdown = when {
                isDefinitelyExpired -> "Ready"
                else -> formatQuotaResetCountdown(resetTime)
            }

            val windowInfo = QuotaWindowInfo(
                window = b.window,
                displayName = b.display_name.ifBlank { b.window },
                remainingFraction = remFraction,
                remainingPct = remPct,
                usedPct = usedPct,
                resetTime = resetTime,
                countdown = countdown,
                description = b.description
            )

            val hint = "${b.window} ${b.display_name} ${b.bucket_id}".lowercase(Locale.US)
            val isFiveHour = hint.contains("5h") ||
                hint.contains("5-h") ||
                hint.contains("5 h") ||
                hint.contains("five") ||
                hint.contains("session") ||
                hint.contains("hourly") ||
                hint.contains("short")
            val isWeekly = hint.contains("week") ||
                hint.contains("7d") ||
                hint.contains("7-d") ||
                hint.contains("7 d") ||
                hint.contains("seven")

            when {
                isFiveHour -> {
                    if (fiveHourInfo == null || remFraction < fiveHourInfo.remainingFraction) {
                        fiveHourInfo = windowInfo
                    }
                }
                isWeekly -> {
                    if (weeklyInfo == null || remFraction < weeklyInfo.remainingFraction) {
                        weeklyInfo = windowInfo
                    }
                }
                else -> fallbackWindows.add(windowInfo)
            }
        }

        if (fiveHourInfo == null && fallbackWindows.isNotEmpty()) {
            fiveHourInfo = fallbackWindows.removeAt(0)
        }
        if (weeklyInfo == null && fallbackWindows.isNotEmpty()) {
            weeklyInfo = fallbackWindows.removeAt(0)
        }
        if (fiveHourInfo == null && weeklyInfo == null) return null

        val nameAndBucketsHint = "$dispName ${buckets.joinToString(" ") { "${it.display_name} ${it.bucket_id}" }}".lowercase(Locale.US)
        val gId = when {
            nameAndBucketsHint.contains("gemini") -> "gemini"
            nameAndBucketsHint.contains("claude") || nameAndBucketsHint.contains("gpt") -> "claude_gpt"
            else -> dispName.lowercase(Locale.US).replace(" ", "_")
        }

        return ModelQuotaGroup(
            groupId = gId,
            groupName = dispName,
            description = desc,
            fiveHour = fiveHourInfo,
            weekly = weeklyInfo
        )
    }

    /**
     * Fetches user quota summary (5-hour and weekly buckets) from RetrieveUserQuotaSummary via typed AgyLanguageService
     */
    suspend fun retrieveUserQuotaSummary(
        forceRefresh: Boolean = false,
        hubUrl: String = AuthPreferences.currentHubUrl
    ): Result<QuotaSummaryResponse> = withContext(Dispatchers.IO) {
        try {
            val req = RetrieveUserQuotaSummaryRequest(force_refresh = forceRefresh)
            val res = AgyLanguageService.RetrieveUserQuotaSummary().executeSafely(req)
            if (!res.isSuccess) {
                return@withContext Result.failure(
                    res.exceptionOrNull() ?: Exception("Failed to retrieve quota summary")
                )
            }
            val rawResponse = res.getOrThrow()
            Log.d(TAG, "retrieveUserQuotaSummary response: $rawResponse")
            val quotaResp = rawResponse.response
            val groupsList = mutableListOf<ModelQuotaGroup>()
            val nowMs = System.currentTimeMillis()

            if (quotaResp != null) {
                for (grp in quotaResp.groups) {
                    buildGroupFromBuckets(
                        buckets = grp.buckets,
                        dispName = grp.display_name,
                        desc = grp.description,
                        nowMs = nowMs
                    )?.let { groupsList.add(it) }
                }

                // Fallback to top-level buckets when groups are empty or produced no windows
                if (groupsList.isEmpty() && quotaResp.buckets.isNotEmpty()) {
                    val byGroupKey = quotaResp.buckets.groupBy { b ->
                        val h = "${b.display_name} ${b.bucket_id}".lowercase(Locale.US)
                        when {
                            h.contains("claude") || h.contains("gpt") -> "claude_gpt"
                            h.contains("gemini") -> "gemini"
                            else -> "default"
                        }
                    }
                    byGroupKey.forEach { (key, bList) ->
                        val disp = when (key) {
                            "claude_gpt" -> "Claude / GPT"
                            "gemini" -> "Google Gemini"
                            else -> "All Models"
                        }
                        buildGroupFromBuckets(
                            buckets = bList,
                            dispName = disp,
                            desc = quotaResp.description,
                            nowMs = nowMs
                        )?.let { groupsList.add(it) }
                    }
                }
            }

            Result.success(
                QuotaSummaryResponse(
                    groups = groupsList,
                    lastUpdated = ISO_FORMAT.format(Date())
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "retrieveUserQuotaSummary failed: ${e.message}")
            Result.failure(e)
        }
    }
}

