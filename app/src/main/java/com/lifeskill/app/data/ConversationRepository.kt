package com.lifeskill.app.data

import androidx.room.withTransaction
import com.lifeskill.app.ai.AiGateway
import com.lifeskill.app.ai.AiProtocol
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.SettingsStore
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.model.TutorRules
import com.lifeskill.app.model.RuleViolation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class ConversationRepository(
    private val database: LifeSkillDatabase,
    private val library: LibraryRepository,
    private val settings: SettingsStore,
    private val gateway: AiGateway,
    private val secret: SecretBox,
) {
    private val dao = database.skills()

    fun watch(scope: ChatScope) = dao.watchTurns(scope.id)
        .map { turns -> turns.map { it.toTurn() } }
        .flowOn(Dispatchers.IO)

    suspend fun send(scope: ChatScope, text: String, check: Boolean = false) {
        val input = text.trim()
        if (check && scope.mode != ChatMode.TUTOR) throw RuleViolation("请在技能点内检验掌握。")
        if (!check && input.length !in 1..INPUT_LIMIT) throw RuleViolation("请输入 1–${INPUT_LIMIT} 个字符。")
        val id = database.withTransaction {
            val owner = requireOwner(scope)
            if (lastPending(scope) != null) throw RuleViolation("请先处理上一条待回复消息。")
            if (dao.chat(scope.id) == null) dao.insertChat(ChatRow(scope.id, scope.ownerId))
            val turn = Turn(
                newId(), Speaker.USER, if (check) TurnKind.CHECK else TurnKind.MESSAGE,
                if (check) "检验掌握" else input, owner?.contentVersion ?: 0,
            )
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
            val recent = dao.recentTurns(scope.id, CONTEXT_TURNS).asReversed()
                .filter { it.contentVersion == input.contentVersion }
                .map { it.toTurn() }
            val pending = if (scope.mode == ChatMode.TUTOR) {
                val last = dao.latestTutorTurn(scope.id, input.contentVersion)?.toTurn()
                val original = last?.exerciseId?.let { dao.exercise(scope.id, it)?.toTurn() }
                TutorRules.pendingExercise(listOfNotNull(original, last))
            } else null
            val prompt = if (scope.mode == ChatMode.TUTOR) {
                AiProtocol.tutorPrompt(checkNotNull(owner), pending)
            } else {
                val candidates = library.snapshot().childrenOf(owner?.id)
                AiProtocol.generationPrompt(owner, candidates)
            }
            Request(owner, input, pending, listOf(ModelMessage("system", prompt)) + messageWindow(recent))
        } ?: return

        val config = withContext(Dispatchers.IO) { settings.active() }.validated()
        val raw = gateway.complete(config, context.messages, structured = true)

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
        if (kind == TurnKind.PASSED && !library.lightFromAssessment(checkNotNull(context.owner))) {
            kind = TurnKind.MESSAGE
        }
        if (kind == TurnKind.PASSED) text = "已点亮技能点\n$text"
        val exerciseId = when (kind) {
            TurnKind.EXERCISE -> id
            TurnKind.RETRY, TurnKind.PASSED -> context.pending?.id
            else -> null
        }
        return Turn(id, Speaker.ASSISTANT, kind, text, context.input.contentVersion, exerciseId)
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
            val text = if (it.kind == TurnKind.CHECK) "【App 发起检验掌握】请出一道当前技能练习。" else it.text
            ModelMessage(role, text)
        }
    }

    private fun Turn.row(scope: ChatScope) = TurnRow(
        id = id, chatId = scope.id, speaker = speaker.name, kind = kind.name,
        text = secret.seal(text, "$id:turn"), contentVersion = contentVersion,
        exerciseId = exerciseId, resultId = resultId,
    )

    private fun TurnRow.toTurn() = Turn(
        id, Speaker.valueOf(speaker), TurnKind.valueOf(kind), secret.open(text, "$id:turn"),
        contentVersion, exerciseId, resultId,
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
