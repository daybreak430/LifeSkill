package com.lifeskill.app.data

import androidx.room.withTransaction
import com.lifeskill.app.ai.AiGateway
import com.lifeskill.app.ai.AiProtocol
import com.lifeskill.app.ai.GenerationLimitExceeded
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.SettingsStore
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.model.TutorRules
import com.lifeskill.app.model.RuleViolation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray

internal class ConversationRepository(
    private val database: LifeSkillDatabase,
    private val library: LibraryRepository,
    private val settings: SettingsStore,
    private val gateway: AiGateway,
    private val secret: SecretBox,
) {
    private val dao = database.skills()

    fun watchLatestStudySkillId() = dao.watchLatestStudySkillId()

    fun watch(scope: ChatScope) = dao.watchTurns(scope.id)
        .map { turns -> turns.map { it.toTurn() } }
        .flowOn(Dispatchers.IO)

    suspend fun send(scope: ChatScope, text: String, check: Boolean = false) {
        val input = text.trim()
        if (check && scope.mode != ChatMode.TUTOR) throw RuleViolation("请在技能点内检验掌握。")
        if (!check && input.length !in 1..INPUT_LIMIT) throw RuleViolation("请输入 1–${INPUT_LIMIT} 个字符。")
        val kind = if (check) TurnKind.CHECK else TurnKind.MESSAGE
        queueAndRespond(scope, kind, if (check) "检验掌握" else input)
    }

    suspend fun restartLesson(scope: ChatScope, requirements: String) {
        if (scope.mode != ChatMode.TUTOR) throw RuleViolation("请在技能点内重新学习。")
        val input = requirements.trim()
        if (input.length > INPUT_LIMIT) throw RuleViolation("本次学习要求最多 ${INPUT_LIMIT} 个字符。")
        queueAndRespond(scope, TurnKind.RESTART, input)
    }

    private suspend fun queueAndRespond(scope: ChatScope, kind: TurnKind, input: String) {
        val id = database.withTransaction {
            val owner = requireOwner(scope)
            if (dao.chat(scope.id) == null) dao.insertChat(ChatRow(scope.id, scope.ownerId))
            if (kind == TurnKind.RESTART) {
                // 清空和新请求一起提交，旧请求的标识也随之失效，迟到的回复无法写回。
                dao.clearTurns(scope.id)
            } else {
                val last = dao.recentTurns(scope.id, 1).firstOrNull()
                if (last?.speaker == Speaker.USER.name) throw RuleViolation("请先处理上一条待回复消息。")
                if (kind == TurnKind.CHECK) {
                    checkNotNull(owner)
                    if (library.snapshot().skillState(owner.id) == SkillState.LOCKED) {
                        throw RuleViolation("点亮前置技能后可检验掌握。")
                    }
                    if (last?.kind != TurnKind.CHECK_OFFER.name || last.contentVersion != owner.contentVersion) {
                        throw RuleViolation("等 AI 老师提示后再检验掌握。")
                    }
                }
            }
            val turn = Turn(newId(), Speaker.USER, kind, input, owner?.contentVersion ?: 0)
            dao.insertTurn(turn.row(scope))
            turn.id
        }
        respond(scope, id)
    }

    suspend fun retry(scope: ChatScope) {
        val pending = lastPending(scope) ?: return
        respond(scope, pending.id)
    }

    suspend fun discardPending(scope: ChatScope, id: String) = database.withTransaction {
        if (lastPending(scope)?.id == id) dao.deleteTurn(scope.id, id)
    }

    private suspend fun respond(scope: ChatScope, requestId: String) {
        val replyId = "reply:$requestId"
        val context = database.withTransaction {
            if (dao.turn(scope.id, replyId) != null) return@withTransaction null
            val owner = requireOwner(scope)
            val input = lastPending(scope)?.takeIf { it.id == requestId } ?: return@withTransaction null
            if (owner != null && owner.contentVersion != input.contentVersion) {
                throw RuleViolation("学习内容已编辑，请重新发送这条消息。")
            }
            val recent = TutorRules.currentLesson(
                dao.recentTurns(scope.id, CONTEXT_TURNS).asReversed()
                    .filter { it.contentVersion == input.contentVersion }
                    .map { it.toTurn() },
            )
            val pending = if (scope.mode == ChatMode.TUTOR) {
                val last = dao.latestTutorTurn(scope.id, input.contentVersion)?.toTurn()
                val original = last?.exerciseId?.let { dao.exercise(scope.id, it)?.toTurn() }
                TutorRules.pendingExercise(listOfNotNull(original, last))
            } else null
            val prompt = if (scope.mode == ChatMode.TUTOR) {
                checkNotNull(owner)
                val canInviteCheck = pending == null && library.snapshot().skillState(owner.id) != SkillState.LOCKED
                AiProtocol.tutorPrompt(owner, pending, canInviteCheck)
            } else {
                val current = library.snapshot()
                val candidates = current.childrenOf(owner?.id)
                val remaining = if (owner?.kind == EntryKind.TREE) {
                    candidates.associate { it.id to current.remainingBranches(it.id) }
                } else emptyMap()
                AiProtocol.generationPrompt(owner, candidates, remaining)
            }
            Request(owner, input, pending, listOf(ModelMessage("system", prompt)) + messageWindow(recent))
        } ?: return

        val config = withContext(Dispatchers.IO) { settings.active() }.validated()
        val raw = gateway.complete(config, context.messages, structured = true)
        try {
            persistResponse(scope, requestId, context, raw)
        } catch (error: GenerationLimitExceeded) {
            // 首次回复未落库。仅对数量越界补做一次，保留原始请求与会话意图。
            if (lastPending(scope)?.id != requestId) return
            val system = context.messages.first()
            val instruction = """
                上一次回复的数组数量超过上限：${error.message}
                请重新回答最后一条用户消息。按原请求和当前入口的上限核对每个数组的实际长度，使用连续编号的 ref，达到指定数量后结束。
                若用户要的数量超过上限、要求预览或尚未确认，返回仅含 kind 和 reply 的 message，说明分批办法。
            """.trimIndent()
            val messages = listOf(system.copy(text = "${system.text}\n\n$instruction")) + context.messages.drop(1)
            val corrected = gateway.complete(config, messages, structured = true)
            persistResponse(scope, requestId, context, corrected)
        }
    }

    private suspend fun persistResponse(scope: ChatScope, requestId: String, context: Request, raw: String) {
        val replyId = "reply:$requestId"
        database.withTransaction {
            // 请求、回复标识固定。并发重试与取消都不能二次落库或复活已删除的内容。
            if (dao.turn(scope.id, replyId) != null || lastPending(scope)?.id != requestId) return@withTransaction
            val current = requireOwner(scope)
            if (current?.contentVersion != context.owner?.contentVersion) {
                throw RuleViolation("当前项目已编辑，请重新发送这条消息。")
            }
            val turn = when (scope.mode) {
                ChatMode.GENERATE -> generationTurn(raw, current, replyId, context.input.contentVersion)
                ChatMode.TUTOR -> tutorTurn(raw, context, replyId)
            }
            dao.insertTurn(turn.row(scope))
        }
    }

    private suspend fun generationTurn(raw: String, owner: Entry?, id: String, version: Long): Turn {
        val reply = AiProtocol.generation(raw)
        val result = reply.draft?.let { library.applyDraft(owner, it) }
        return if (result == null) {
            Turn(id, Speaker.ASSISTANT, TurnKind.MESSAGE, reply.reply, version)
        } else {
            Turn(id, Speaker.ASSISTANT, TurnKind.CREATED, result.summary, version, resultId = result.entry.id)
        }
    }

    private suspend fun tutorTurn(raw: String, context: Request, id: String): Turn {
        val reply = AiProtocol.tutor(raw)
        TutorRules.validateVerdict(reply.kind, reply.exerciseId, context.pending, context.input)
        if (context.input.kind == TurnKind.CHECK && reply.kind != TurnKind.EXERCISE) {
            throw RuleViolation("AI 暂未给出检验题，请重试。")
        }
        var text = if (reply.kind == TurnKind.OFF_TOPIC) {
            "我们先回到「${context.owner?.name}」。你想继续哪个知识点，或试试一个小练习？"
        } else reply.reply
        var kind = reply.kind
        if (kind == TurnKind.PASSED) {
            when (library.lightFromAssessment(checkNotNull(context.owner))) {
                AssessmentLight.LIT -> text = "已点亮技能点\n$text"
                AssessmentLight.LOCKED -> {
                    kind = TurnKind.MESSAGE
                    text = "练习通过。点亮前置技能后，可重新检验以点亮此技能。\n\n$text"
                }
                AssessmentLight.UNCHANGED -> kind = TurnKind.MESSAGE
            }
        }
        val exerciseId = when (kind) {
            TurnKind.EXERCISE -> id
            TurnKind.RETRY, TurnKind.PASSED -> context.pending?.id
            else -> null
        }
        return Turn(id, Speaker.ASSISTANT, kind, text, context.input.contentVersion, exerciseId, choices = reply.choices)
    }

    private suspend fun requireOwner(scope: ChatScope): Entry? {
        val id = scope.ownerId ?: return null
        val entry = library.entry(id) ?: throw RuleViolation("当前项目已删除。")
        if ((scope.mode == ChatMode.TUTOR) != (entry.kind == EntryKind.SKILL)) {
            throw RuleViolation("此对话与当前项目层级不匹配。")
        }
        return entry
    }

    private suspend fun lastPending(scope: ChatScope): Turn? =
        dao.recentTurns(scope.id, 1).firstOrNull { it.speaker == Speaker.USER.name }?.toTurn()

    private fun messageWindow(recent: List<Turn>): List<ModelMessage> {
        var remaining = CONTEXT_CHARS
        return recent.asReversed().takeWhile {
            remaining -= it.text.length
            remaining >= 0
        }.asReversed().dropWhile { it.speaker != Speaker.USER }.map {
            val role = if (it.speaker == Speaker.USER) "user" else "assistant"
            val text = when (it.kind) {
                TurnKind.CHECK -> "【App 发起检验掌握】请出一道当前技能练习。"
                TurnKind.RESTART -> "【App 发起重新学习】请按当前技能说明重新组织这节课。" +
                    if (it.text.isNotEmpty()) "\n本次学习要求：${it.text}" else ""
                else -> it.modelText
            }
            ModelMessage(role, text)
        }
    }

    private fun Turn.row(scope: ChatScope) = TurnRow(
        id = id, chatId = scope.id, speaker = speaker.name, kind = kind.name,
        text = secret.seal(text, "$id:turn"), contentVersion = contentVersion,
        exerciseId = exerciseId, resultId = resultId,
        choices = choices.takeIf(List<String>::isNotEmpty)?.let { secret.seal(JSONArray(it).toString(), "$id:choices") },
    )

    private fun TurnRow.toTurn() = Turn(
        id, Speaker.valueOf(speaker), TurnKind.valueOf(kind), secret.open(text, "$id:turn"),
        contentVersion, exerciseId, resultId, choices?.let {
            val values = JSONArray(secret.open(it, "$id:choices"))
            List(values.length()) { index -> values.getString(index) }
        }.orEmpty(),
    )

    private data class Request(
        val owner: Entry?,
        val input: Turn,
        val pending: com.lifeskill.app.model.Exercise?,
        val messages: List<ModelMessage>,
    )

    companion object {
        const val INPUT_LIMIT = 4_000
        private const val CONTEXT_TURNS = 16
        private const val CONTEXT_CHARS = 16_000
    }
}
