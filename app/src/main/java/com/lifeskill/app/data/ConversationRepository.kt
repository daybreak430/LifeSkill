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
import com.lifeskill.app.model.GenerationLevel
import com.lifeskill.app.model.GenerationOptions
import com.lifeskill.app.model.GenerationPreview
import com.lifeskill.app.model.SkillGraph
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.SkippedPrerequisiteTier
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
import org.json.JSONObject

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

    suspend fun generateTree(scope: ChatScope, offerId: String, options: GenerationOptions) {
        if (scope.mode != ChatMode.GENERATE) throw RuleViolation("请在 AI 生成对话中确认。")
        // 与待处理消息一起加密保存，自动补试、手动重试和重启都使用已确认的设置。
        val input = JSONObject()
            .put("level", options.level.name)
            .put("minimum", options.courseCount.first)
            .put("maximum", options.courseCount.last)
            .toString()
        queueAndRespond(scope, TurnKind.GENERATE_TREE, input, offerId)
    }

    private suspend fun queueAndRespond(scope: ChatScope, kind: TurnKind, input: String, offerId: String? = null) {
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
                if (kind == TurnKind.GENERATE_TREE && (owner?.kind == EntryKind.TREE || last == null ||
                        last.id != offerId || last.kind != TurnKind.TREE_OFFER.name ||
                        last.contentVersion != (owner?.contentVersion ?: 0L))
                ) {
                    throw RuleViolation("当前生成提议已失效，请重新描述学习要求。")
                }
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

    suspend fun retry(scope: ChatScope, expectedId: String? = null) {
        val pending = lastPending(scope)?.takeIf { expectedId == null || it.id == expectedId } ?: return
        respond(scope, pending.id)
    }

    suspend fun discardPending(scope: ChatScope, id: String) = database.withTransaction {
        if (lastPending(scope)?.id == id) dao.deleteTurn(scope.id, id)
    }

    suspend fun clearGeneration(scope: ChatScope) {
        if (scope.mode != ChatMode.GENERATE) throw RuleViolation("请在 AI 生成对话中清空会话。")
        // 同一条 SQL 移除所有消息及待处理请求，旧回复会因请求标识失效而无法写回。
        dao.clearTurns(scope.id)
    }

    suspend fun confirmDraft(scope: ChatScope, id: String, destination: GenerationDestination) = database.withTransaction {
        if (scope.mode != ChatMode.GENERATE) throw RuleViolation("请在 AI 生成对话中确认添加。")
        val row = dao.turn(scope.id, id) ?: throw RuleViolation("这份草稿已不存在。")
        if (row.kind == TurnKind.CREATED.name) return@withTransaction
        if (row.kind != TurnKind.DRAFT.name) throw RuleViolation("这条消息没有可添加的草稿。")
        requireOwner(scope)
        val resolved = checkNotNull(AiProtocol.generation(secret.open(row.text, "$id:turn"), savedDraft = true).draft)
            .prepareNewTrees()
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
                val levels = if (owner?.kind == EntryKind.TREE) {
                    SkillGraph.levels(candidates.map(Entry::id), current.edges)
                } else emptyMap()
                AiProtocol.generationPrompt(owner, candidates, remaining, levels, input.generationOptions)
            }
            val history = if (scope.mode == ChatMode.GENERATE && owner?.kind != EntryKind.TREE) {
                newTreeHistory(recent, input)
            } else recent
            Request(owner, input, pending, listOf(ModelMessage("system", prompt)) + messageWindow(scope.mode, history))
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
            val corrected = gateway.complete(config, messages, structured = true, useJsonMode = !error.retryWithoutJsonMode)
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
                ChatMode.GENERATE -> generationTurn(raw, current, replyId, context.input)
                ChatMode.TUTOR -> tutorTurn(raw, context, replyId)
            }
            dao.insertTurn(turn.row(scope))
        }
    }

    private suspend fun generationTurn(raw: String, owner: Entry?, id: String, input: Turn): Turn {
        val version = input.contentVersion
        val options = input.generationOptions
        val reply = AiProtocol.generation(raw)
        if (reply.kind == TurnKind.TREE_OFFER || reply.kind == TurnKind.TREE_QUESTION) {
            if (options != null || owner?.kind == EntryKind.TREE) {
                throw RecoverableReply("AI 返回了不适用的规划回复，请重试。", "请按当前入口和确认状态回答，不要返回 offer_tree 或 clarify_tree。")
            }
            return Turn(id, Speaker.ASSISTANT, reply.kind, reply.reply, version)
        }
        val draft = reply.draft ?: return Turn(id, Speaker.ASSISTANT, TurnKind.MESSAGE, reply.reply, version)
        if (owner?.kind == EntryKind.TREE) {
            val result = try {
                library.applyDraft(owner, draft)
            } catch (error: SkippedPrerequisiteTier) {
                throw RecoverableReply(
                    checkNotNull(error.message),
                    "上一版追加方案包含跨层前置。为每个新技能选择同一逻辑层的直接前置，并重新输出完整 JSON。",
                )
            }
            return Turn(id, Speaker.ASSISTANT, TurnKind.CREATED, result.summary, version, resultId = result.entry.id)
        }
        if (options == null && draft.trees.isNotEmpty()) {
            throw RecoverableReply(
                "请先确认技能树的生成设置。",
                "用户还未确认掌握程度和课程数量。主题清楚时仅返回 offer_tree，主题不清楚时用 clarify_tree 追问。",
            )
        }
        if (options != null) {
            val courseCount = draft.trees.singleOrNull()?.skills?.size ?: 0
            if (courseCount !in options.courseCount) {
                val expected = "1 棵技能树，包含 ${options.courseCountLabel}"
                throw RecoverableReply(
                    "请生成$expected。",
                    "上一版没有满足用户确认的树数或课数。请重新生成$expected，并输出完整 JSON。",
                )
            }
        }
        if (draft.skills.isNotEmpty() || (owner != null && (draft.directionId != null || draft.directionName != null)) ||
            (draft.trees.isEmpty() && (owner != null || draft.directionName == null || draft.directionId != null))
        ) {
            throw RuleViolation("AI 返回的草稿与当前入口不匹配，请重试。")
        }
        try {
            library.validateNewTrees(draft.prepareNewTrees().draft.trees)
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

    private fun newTreeHistory(recent: List<Turn>, input: Turn): List<Turn> {
        val latestOffer = recent.getOrNull(recent.lastIndex - 1)?.takeIf { it.kind == TurnKind.TREE_OFFER }
        val boundary = recent.indexOfLast {
            it.speaker == Speaker.ASSISTANT && it.kind != TurnKind.TREE_QUESTION && it.id != latestOffer?.id
        }
        // 保留本轮要求和连续追问。紧接提议的补充仍可引用原主题，旧规划的用户消息不再参与。
        return recent.drop(boundary + 1).filter { turn ->
            turn.speaker == Speaker.USER || turn.kind == TurnKind.TREE_QUESTION ||
                (input.kind == TurnKind.GENERATE_TREE && turn.id == latestOffer?.id)
        }
    }

    private fun messageWindow(mode: ChatMode, recent: List<Turn>): List<ModelMessage> {
        val messages = recent.map {
            val role = if (it.speaker == Speaker.USER) "user" else "assistant"
            val text = when {
                mode == ChatMode.TUTOR && it.speaker == Speaker.ASSISTANT -> AiProtocol.tutorHistory(it)
                mode == ChatMode.GENERATE && it.speaker == Speaker.ASSISTANT -> AiProtocol.generationHistory(it)
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
            val draft = checkNotNull(AiProtocol.generation(body, savedDraft = true).draft)
            val preview = GenerationPreview(draft.trees.map { it.name }, resultId ?: draft.directionId, draft.directionName)
            val summary = if (draft.trees.isEmpty()) "已生成方向草稿" else "已生成 ${draft.trees.size} 棵技能树"
            return Turn(id, Speaker.ASSISTANT, turnKind, summary + draft.prepareNewTrees().notice,
                contentVersion, preview = preview)
        }
        if (turnKind == TurnKind.GENERATE_TREE) {
            val data = JSONObject(body)
            val options = GenerationOptions(
                GenerationLevel.valueOf(data.getString("level")), data.getInt("minimum")..data.getInt("maximum"),
            )
            return Turn(id, Speaker.USER, turnKind, options.confirmation, contentVersion, generationOptions = options)
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
