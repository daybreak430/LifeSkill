package com.lifeskill.app.data

import androidx.room.withTransaction
import com.lifeskill.app.ai.AiGateway
import com.lifeskill.app.ai.AiProtocol
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.RecoverableReply
import com.lifeskill.app.ai.SettingsStore
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.GenerationDestination
import com.lifeskill.app.model.GenerationPreview
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
        queueAndRespond(scope, kind, if (check && input != TutorRules.EXAM_REPLY) "检验掌握" else input)
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
            requireUnlockedLesson(scope, owner)
            if (dao.chat(scope.id) == null) dao.insertChat(ChatRow(scope.id, scope.ownerId))
            if (kind == TurnKind.RESTART) {
                // 清空和新请求一起提交，旧请求的标识也随之失效，迟到的回复无法写回。
                dao.clearTurns(scope.id)
            } else {
                val last = dao.recentTurns(scope.id, 1).firstOrNull()
                if (last?.speaker == Speaker.USER.name) throw RuleViolation("请先处理上一条待回复消息。")
                if (kind == TurnKind.CHECK) {
                    checkNotNull(owner)
                    val lastReply = last?.takeIf { it.speaker == Speaker.ASSISTANT.name && it.contentVersion == owner.contentVersion }
                        ?.let { TurnKind.valueOf(it.kind) }
                    if (input == TutorRules.EXAM_REPLY) {
                        val rounds = dao.teachingReplies(scope.id, owner.contentVersion,
                            TutorRules.teachingReplyKinds.map { it.name }, TutorRules.EXAM_TEACHING_ROUNDS)
                        if (!TutorRules.canStartExam(lastReply, rounds)) {
                            throw RuleViolation("请先完成 ${TutorRules.EXAM_TEACHING_ROUNDS} 轮讲解，或答完当前练习后再发起考试。")
                        }
                    } else if (lastReply != TurnKind.CHECK_OFFER) {
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

    suspend fun confirmDraft(scope: ChatScope, id: String, destination: GenerationDestination) = database.withTransaction {
        if (scope.mode != ChatMode.GENERATE) throw RuleViolation("请在 AI 生成对话中确认添加。")
        val row = dao.turn(scope.id, id) ?: throw RuleViolation("这份草稿已不存在。")
        if (row.kind == TurnKind.CREATED.name) return@withTransaction
        if (row.kind != TurnKind.DRAFT.name) throw RuleViolation("这条消息没有可添加的草稿。")
        requireOwner(scope)
        val resolved = checkNotNull(AiProtocol.generation(secret.open(row.text, "$id:turn")).draft).resolveNewTreeReferences()
        val draft = resolved.draft
        val selected = when (destination) {
            is GenerationDestination.Existing -> draft.copy(directionId = destination.id, directionName = null)
            is GenerationDestination.New -> draft.copy(directionId = null, directionName = destination.name)
        }
        // 以用户选中的方向为准，写入内容和更新草稿状态一次提交。
        val result = library.applyDraft(null, selected)
        val created = Turn(id, Speaker.ASSISTANT, TurnKind.CREATED, result.summary + resolved.notice,
            row.contentVersion, resultId = result.entry.id)
        dao.updateTurn(created.row(scope).copy(sequence = row.sequence))
    }

    private suspend fun respond(scope: ChatScope, requestId: String) {
        val replyId = "reply:$requestId"
        val context = database.withTransaction {
            if (dao.turn(scope.id, replyId) != null) return@withTransaction null
            val owner = requireOwner(scope)
            val input = lastPending(scope)?.takeIf { it.id == requestId } ?: return@withTransaction null
            requireUnlockedLesson(scope, owner)
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
                AiProtocol.tutorPrompt(owner, pending, canInviteCheck = pending == null)
            } else {
                val current = library.snapshot()
                val candidates = current.childrenOf(owner?.id)
                val remaining = if (owner?.kind == EntryKind.TREE) {
                    candidates.associate { it.id to current.remainingBranches(it.id) }
                } else emptyMap()
                AiProtocol.generationPrompt(owner, candidates, remaining)
            }
            Request(owner, input, pending, listOf(ModelMessage("system", prompt)) + messageWindow(scope.mode, recent))
        } ?: return

        val config = withContext(Dispatchers.IO) { settings.active() }.validated()
        try {
            val raw = gateway.complete(config, context.messages, structured = true)
            persistResponse(scope, requestId, context, raw)
        } catch (error: RecoverableReply) {
            // 只补试一次。沿用原请求标识，撤回、重学或已完成的请求无需再发。
            if (lastPending(scope)?.id != requestId) return
            val system = context.messages.first()
            val messages = listOf(system.copy(text = "${system.text}\n\n${error.retryInstruction}")) + context.messages.drop(1)
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
        val draft = reply.draft ?: return Turn(id, Speaker.ASSISTANT, TurnKind.MESSAGE, reply.reply, version)
        if (owner?.kind == EntryKind.TREE) {
            val result = library.applyDraft(owner, draft)
            return Turn(id, Speaker.ASSISTANT, TurnKind.CREATED, result.summary, version, resultId = result.entry.id)
        }
        if (draft.skills.isNotEmpty() || (owner != null && (draft.directionId != null || draft.directionName != null)) ||
            (draft.trees.isEmpty() && (owner != null || draft.directionName == null || draft.directionId != null))
        ) {
            throw RuleViolation("AI 返回的草稿与当前入口不匹配，请重试。")
        }
        try {
            library.validateNewTrees(draft.resolveNewTreeReferences().draft.trees)
        } catch (error: RuleViolation) {
            throw RecoverableReply(
                error.message ?: "生成的技能树存在冲突，请重试。",
                "上一版技能树没有通过校验。${error.message.orEmpty()} 请重新生成，核对名称和每棵树的前置关系后输出完整 JSON。",
            )
        }
        return Turn(id, Speaker.ASSISTANT, TurnKind.DRAFT, raw, version, resultId = owner?.id)
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

    private suspend fun requireUnlockedLesson(scope: ChatScope, owner: Entry?) {
        if (scope.mode == ChatMode.TUTOR && library.snapshot().skillState(checkNotNull(owner).id) == SkillState.LOCKED) {
            throw RuleViolation("点亮全部前置技能后才能学习。")
        }
    }

    private suspend fun lastPending(scope: ChatScope): Turn? =
        dao.recentTurns(scope.id, 1).firstOrNull { it.speaker == Speaker.USER.name }?.toTurn()

    private fun messageWindow(mode: ChatMode, recent: List<Turn>): List<ModelMessage> {
        val messages = recent.map {
            val role = if (it.speaker == Speaker.USER) "user" else "assistant"
            val text = when {
                mode == ChatMode.TUTOR && it.speaker == Speaker.ASSISTANT -> AiProtocol.tutorHistory(it)
                it.kind == TurnKind.CHECK -> "【App 发起检验掌握】请出一道当前技能练习。"
                it.kind == TurnKind.RESTART -> "【App 发起重新学习】请按当前技能说明重新组织这节课。" +
                    if (it.text.isNotEmpty()) "\n本次学习要求：${it.text}" else ""
                else -> it.modelText
            }
            ModelMessage(role, text)
        }
        var remaining = CONTEXT_CHARS
        return messages.takeLastWhile {
            remaining -= it.text.length
            remaining >= 0
        }.dropWhile { it.role != "user" }
    }

    private fun Turn.row(scope: ChatScope) = TurnRow(
        id = id, chatId = scope.id, speaker = speaker.name, kind = kind.name,
        text = secret.seal(text, "$id:turn"), contentVersion = contentVersion,
        exerciseId = exerciseId, resultId = resultId,
        choices = choices.takeIf(List<String>::isNotEmpty)?.let { secret.seal(JSONArray(it).toString(), "$id:choices") },
    )

    private fun TurnRow.toTurn(): Turn {
        val body = secret.open(text, "$id:turn")
        val turnKind = TurnKind.valueOf(kind)
        if (turnKind == TurnKind.DRAFT) {
            val draft = checkNotNull(AiProtocol.generation(body).draft)
            val preview = GenerationPreview(draft.trees.map { it.name }, resultId ?: draft.directionId, draft.directionName)
            val summary = if (draft.trees.isEmpty()) "已生成方向草稿" else "已生成 ${draft.trees.size} 棵技能树"
            return Turn(id, Speaker.ASSISTANT, turnKind, summary + draft.resolveNewTreeReferences().notice,
                contentVersion, preview = preview)
        }
        return Turn(id, Speaker.valueOf(speaker), turnKind, body, contentVersion, exerciseId, resultId, choices?.let {
            val values = JSONArray(secret.open(it, "$id:choices"))
            List(values.length()) { index -> values.getString(index) }
        }.orEmpty())
    }

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
