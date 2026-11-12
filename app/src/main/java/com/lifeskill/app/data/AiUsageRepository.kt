package com.lifeskill.app.data

import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import com.lifeskill.app.ai.AiGateway
import com.lifeskill.app.ai.DeepSeekSearch
import com.lifeskill.app.ai.ModelPricing
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.ModelSettings
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant

private const val DEEPSEEK_SEARCH_COST_NANOS = 5_000_000L

// 用量独立于对话和课程生命周期。清空聊天或删除课程后，历史支出仍计入全局及已领取的证书。
@Entity(tableName = "ai_usage", indices = [Index("draftId")])
internal data class AiUsageRow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val courseId: String?,
    val lessonId: String?,
    val draftId: String?,
    val generation: Boolean,
    val model: String,
    val createdAt: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val cachedInputTokens: Long?,
    val estimatedCostNanos: Long?,
    val reportedTotalTokens: Long?,
)

internal data class UsageGroup(
    val courseId: String?,
    val lessonId: String?,
    val generation: Boolean,
    val tokens: Long,
    val costNanos: Long?,
    val unpriced: Int,
)

@Dao
internal interface AiUsageDao {
    @Insert
    suspend fun insert(row: AiUsageRow)

    @Query("UPDATE ai_usage SET courseId = :courseId, draftId = NULL WHERE draftId = :draftId")
    suspend fun attachDraft(draftId: String, courseId: String)

    @Query("""
        SELECT courseId, lessonId, generation, SUM(COALESCE(reportedTotalTokens, inputTokens + outputTokens)) AS tokens,
            SUM(estimatedCostNanos) AS costNanos, COUNT(*) - COUNT(estimatedCostNanos) AS unpriced
        FROM ai_usage GROUP BY courseId, lessonId, generation
    """)
    fun watchGroups(): Flow<List<UsageGroup>>
}

internal data class UsageTarget(
    val courseId: String? = null,
    val lessonId: String? = null,
    val draftId: String? = null,
    val generation: Boolean = false,
)

internal data class UsageTotal(val tokens: Long, val costNanos: Long?, val unpriced: Int) {
    operator fun plus(other: UsageTotal) = UsageTotal(
        tokens + other.tokens,
        if (costNanos == null && other.costNanos == null) null else (costNanos ?: 0) + (other.costNanos ?: 0),
        unpriced + other.unpriced,
    )
}

internal data class UsageSummary(
    val total: UsageTotal? = null,
    val courses: Map<String, UsageTotal> = emptyMap(),
    val lessons: Map<String, UsageTotal> = emptyMap(),
    val generation: Map<String, UsageTotal> = emptyMap(),
)

internal class AiUsageRepository(private val dao: AiUsageDao, private val gateway: AiGateway) {
    val summary = dao.watchGroups().map { groups ->
        var total: UsageTotal? = null
        val courses = mutableMapOf<String, UsageTotal>()
        val lessons = mutableMapOf<String, UsageTotal>()
        val generation = mutableMapOf<String, UsageTotal>()
        for (group in groups) {
            val usage = UsageTotal(group.tokens, group.costNanos, group.unpriced)
            total = total?.plus(usage) ?: usage
            group.courseId?.let { courses.addUsage(it, usage) }
            group.lessonId?.let { lessons.addUsage(it, usage) }
            if (group.generation) group.courseId?.let { generation.addUsage(it, usage) }
        }
        UsageSummary(total, courses, lessons, generation)
    }

    suspend fun complete(
        settings: ModelSettings, messages: List<ModelMessage>, structured: Boolean,
        target: UsageTarget = UsageTarget(), useJsonMode: Boolean = true,
    ): String = gateway.complete(settings, messages, structured, useJsonMode) { usage ->
        record(AiUsageRow(
            courseId = target.courseId, lessonId = target.lessonId, draftId = target.draftId,
            generation = target.generation, model = usage.model, createdAt = usage.timestamp.epochSecond,
            inputTokens = usage.inputTokens ?: 0, outputTokens = usage.outputTokens ?: 0,
            cachedInputTokens = usage.cachedInputTokens, estimatedCostNanos = ModelPricing.estimateNanos(usage),
            reportedTotalTokens = usage.reportedTotalTokens,
        ))
    }

    suspend fun search(settings: ModelSettings, query: String, target: UsageTarget): DeepSeekSearch {
        val result = gateway.search(settings, query)
        if (result.billable) {
            record(AiUsageRow(
                courseId = target.courseId, lessonId = target.lessonId, draftId = target.draftId,
                generation = target.generation, model = "deepseek-search", createdAt = Instant.now().epochSecond,
                inputTokens = 0, outputTokens = 0, cachedInputTokens = null,
                estimatedCostNanos = DEEPSEEK_SEARCH_COST_NANOS, reportedTotalTokens = null,
            ))
        }
        return result
    }

    // 用量回执到达后即落库，即使页面离开、正文无效或对话结果被丢弃。
    private suspend fun record(row: AiUsageRow) = withContext(NonCancellable) {
        try {
            dao.insert(row)
        } catch (_: SQLiteException) {
            Log.w("AiUsage", "无法保存本次用量")
        }
    }

    suspend fun attachDraft(draftId: String, courseId: String) = dao.attachDraft(draftId, courseId)

    private fun MutableMap<String, UsageTotal>.addUsage(id: String, usage: UsageTotal) {
        this[id] = this[id]?.plus(usage) ?: usage
    }
}
