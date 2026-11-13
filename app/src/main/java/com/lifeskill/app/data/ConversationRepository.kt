package com.lifeskill.app.data

import androidx.room.withTransaction
import com.lifeskill.app.ai.AiProtocol
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.RecoverableReply
import com.lifeskill.app.ai.SettingsStore
import com.lifeskill.app.ai.searchInstructions
import com.lifeskill.app.ai.searchQuery
import com.lifeskill.app.ai.searchSources
import com.lifeskill.app.model.CHAT_ATTACHMENT_LIMIT
import com.lifeskill.app.model.ChatAttachment
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.ConversationExport
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Exercise
import com.lifeskill.app.model.GenerationDestination
import com.lifeskill.app.model.GenerationLevel
import com.lifeskill.app.model.GenerationOptions
import com.lifeskill.app.model.GenerationPreview
import com.lifeskill.app.model.LessonTranscript
import com.lifeskill.app.model.SkillGraph
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.SkippedPrerequisiteTier
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.model.TutorRules
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.conversationExport
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
    private val usage: AiUsageRepository,
    private val secret: SecretBox,
    private val attachments: ChatAttachments,
) {
    private val dao = database.skills()

    fun watchLatestStudySkillId() = dao.watchLatestStudySkillId()

    fun watch(scope: ChatScope) = dao.watchTurns(scope.id)
        .map { turns -> turns.map { it.toTurn() } }
        .flowOn(Dispatchers.IO)

    suspend fun exportConversation(entryId: String): ConversationExport = withContext(Dispatchers.IO) {
        val (entry, lessons, turns) = database.withTransaction {
            // 标题、课时顺序和消息取自同一份数据库快照，避免导出时的编辑打乱分节。
            val snapshot = library.snapshot()
            val entry = snapshot.byId[entryId] ?: throw RuleViolation("课程或课时已删除。")
            val lessons = when (entry.kind) {
                EntryKind.SKILL -> listOf(entry)
                EntryKind.TREE -> snapshot.childrenOf(entry.id)
                EntryKind.DIRECTION -> throw RuleViolation("请在课程或课时内导出对话。")
            }
            Triple(entry, lessons, dao.tutorTurnsForExport(entry.id).groupBy(TurnRow::chatId))
        }
        val transcripts = lessons.mapNotNull { lesson ->
            turns[ChatScope(ChatMode.TUTOR, lesson.id).id]?.let { rows ->
                LessonTranscript(lesson.name, rows.map { it.toTurn() })
            }
        }
        if (transcripts.isEmpty()) throw RuleViolation("暂无对话可导出。")
        conversationExport(entry, transcripts)
    }

    suspend fun send(
        scope: ChatScope, text: String, check: Boolean = false, webSearch: Boolean = false,
        files: List<AttachmentDraft> = emptyList(),
        onRequestQueued: (String) -> Unit = {},
    ) {
        val input = text.trim()
        if (check && scope.mode != ChatMode.TUTOR) throw RuleViolation("请在 AI 老师页面做课后练习。")
        if (!check && (input.length > INPUT_LIMIT || input.isEmpty() && files.isEmpty())) {
            throw RuleViolation("请输入内容或添加附件。文字最多 ${INPUT_LIMIT} 个字符。")
        }
        if (files.size > CHAT_ATTACHMENT_LIMIT || check && files.isNotEmpty()) throw RuleViolation("本次无法添加这些附件。")
        val kind = if (check) TurnKind.CHECK else TurnKind.MESSAGE
        queueAndRespond(
            scope, kind, if (check && input != TutorRules.EXAM_REPLY) "开始课后练习" else input,
            onRequestQueued = onRequestQueued,
            webSearch = webSearch && !check && input.isNotEmpty(),
            files = files,
        )
    }

    suspend fun restartLesson(
        scope: ChatScope, requirements: String, webSearch: Boolean = false, onRequestQueued: (String) -> Unit = {},
    ) {
        if (scope.mode != ChatMode.TUTOR) throw RuleViolation("请在 AI 老师页面重新学习。")
        val input = requirements.trim()
        if (input.length > INPUT_LIMIT) throw RuleViolation("本次学习要求最多 ${INPUT_LIMIT} 个字符。")
        queueAndRespond(scope, TurnKind.RESTART, input, onRequestQueued = onRequestQueued, webSearch = webSearch)
    }

    suspend fun generateTree(
        scope: ChatScope, offerId: String, options: GenerationOptions, onRequestQueued: (String) -> Unit = {},
    ) {
        if (scope.mode != ChatMode.GENERATE) throw RuleViolation("请在 AI 生成对话中确认。")
        // 与待处理消息一起加密保存，自动补试、手动重试和重启都使用已确认的设置。
        val input = JSONObject()
            .put("level", options.level.name)
            .put("minimum", options.courseCount.first)
            .put("maximum", options.courseCount.last)
            .toString()
        queueAndRespond(scope, TurnKind.GENERATE_TREE, input, offerId, onRequestQueued)
    }

    private suspend fun queueAndRespond(
        scope: ChatScope, kind: TurnKind, input: String, offerId: String? = null,
        onRequestQueued: (String) -> Unit,
        webSearch: Boolean = false,
        files: List<AttachmentDraft> = emptyList(),
    ) {
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
                            throw RuleViolation("请先完成 ${TutorRules.EXAM_TEACHING_ROUNDS} 轮讲解，或答完当前练习后再开始课后练习。")
                        }
                    } else if (lastReply != TurnKind.CHECK_OFFER) {
                        throw RuleViolation("等 AI 老师提示后再做课后练习。")
                    }
                }
            }
            val turn = Turn(newId(), Speaker.USER, kind, input, owner?.contentVersion ?: 0, attachments = files.map { it.attachment })
            // 空串标记本条消息需要搜索，成功后换成加密结果，旧消息与普通消息仍为 null。
            dao.insertTurn(turn.row(scope).copy(searchContext = "".takeIf { webSearch }))
            for (file in files) attachments.save(turn.id, file)
            turn.id
        }
        // 先把待处理消息标识交给界面，成功回复覆盖旧历史时才能立即隐藏重试和撤回操作。
        onRequestQueued(id)
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
        if (result.entry.kind == EntryKind.TREE) usage.attachDraft(id, result.entry.id)
        val created = Turn(id, Speaker.ASSISTANT, TurnKind.CREATED, result.summary + resolved.notice,
            row.contentVersion, resultId = result.entry.id)
        dao.updateTurn(created.row(scope).copy(sequence = row.sequence, searchContext = row.searchContext))
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
            // 练习题可能已超出近期对话窗口，按待答题序号读取本轮题干。
            val askedQuestions = pending?.let {
                dao.recentTeaching(scope.id, input.contentVersion, listOf(TurnKind.EXERCISE.name), it.number)
                    .asReversed().map { row -> row.toTurn().text }
            }.orEmpty()
            val reissue = TutorRules.reissuesExercise(input, pending)
            val prompt = if (scope.mode == ChatMode.TUTOR) {
                checkNotNull(owner)
                AiProtocol.tutorPrompt(owner, pending, askedQuestions, reissue)
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
            val searchContext = dao.turn(scope.id, requestId)?.searchContext.takeIf { pending == null }
            Request(owner, input, pending, askedQuestions, reissue, searchContext,
                ModelMessage("system", prompt), history)
        } ?: return

        val config = withContext(Dispatchers.IO) { settings.active() }.validated()
        val history = messageWindow(scope, context.history, config)
        val owner = context.owner
        val target = when (owner?.kind) {
            EntryKind.SKILL -> UsageTarget(courseId = owner.parentId, lessonId = owner.id)
            EntryKind.TREE -> UsageTarget(courseId = owner.id, generation = true)
            else -> UsageTarget(
                draftId = replyId.takeIf { context.input.kind == TurnKind.GENERATE_TREE },
                generation = context.input.kind == TurnKind.GENERATE_TREE,
            )
        }
        val searchResults = context.searchContext?.let { saved ->
            if (!config.supportsWebSearch) throw RuleViolation("请切回 DeepSeek 后重试，或撤回后重新发送。")
            if (saved.isNotEmpty()) JSONArray(secret.open(saved, "$requestId:search"))
            else {
                val lessonName = owner?.name.takeIf { scope.mode == ChatMode.TUTOR }
                // 学习快捷回复只有操作意图，未填写重学要求时也用课时主题搜索。
                val question = when (context.input.text) {
                    TutorRules.START_REPLY, TutorRules.CONTINUE_REPLY -> lessonName ?: context.input.text
                    else -> context.input.text.ifBlank { lessonName.orEmpty() }
                }
                val query = searchQuery(question, lessonName)
                val results = usage.search(config, query, target).results
                // 搜索成功即保存证据，回复失败时复用这次结果，避免再次付费搜索。
                val stillPending = database.withTransaction {
                    val row = dao.recentTurns(scope.id, 1).firstOrNull()?.takeIf { it.id == requestId }
                        ?: return@withTransaction false
                    dao.updateTurn(row.copy(searchContext = secret.seal(results.toString(), "$requestId:search")))
                    true
                }
                if (!stillPending) return
                results
            }
        }
        val system = if (searchResults == null) context.system else
            context.system.copy(text = "${context.system.text}\n\n${searchResults.searchInstructions()}")
        val messages = listOf(system) + history
        val sources = searchResults?.searchSources()
        try {
            val raw = usage.complete(config, messages, structured = true, target = target)
            persistResponse(scope, requestId, context, raw, sources)
        } catch (error: RecoverableReply) {
            // 只补试一次。沿用原请求标识，撤回、重学或已完成的请求无需再发。
            if (lastPending(scope)?.id != requestId) return
            val system = messages.first()
            val retryMessages = listOf(system.copy(text = "${system.text}\n\n${error.retryInstruction}")) + messages.drop(1)
            try {
                val corrected = usage.complete(config, retryMessages, structured = true, target = target,
                    useJsonMode = !error.retryWithoutJsonMode)
                persistResponse(scope, requestId, context, corrected, sources)
            } catch (_: RecoverableReply) {
                throw RuleViolation(if (scope.mode == ChatMode.GENERATE) "这次没能生成可用的内容，请重试。"
                    else if (context.pending != null) "这次没能完成评估，已保留你的答案。"
                    else "这次没能完成回复，请重试。")
            }
        }
    }

    private suspend fun persistResponse(scope: ChatScope, requestId: String, context: Request, raw: String, sources: String?) {
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
            if (context.reissue) {
                // 旧题保留在对话里，但只有替换后的题参与本轮题序和判分。
                val original = checkNotNull(dao.exercise(scope.id, checkNotNull(context.pending).id))
                dao.updateTurn(original.copy(kind = TurnKind.MESSAGE.name, exerciseId = null, exerciseNumber = null))
            }
            dao.insertTurn(turn.copy(searchSources = sources).row(scope))
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
                    "上一版追加方案包含跨阶段的先修关系。为每节新课选择同一学习阶段的先修课，并重新输出完整 JSON。",
                )
            }
            return Turn(id, Speaker.ASSISTANT, TurnKind.CREATED, result.summary, version, resultId = result.entry.id)
        }
        if (options == null && draft.trees.isNotEmpty()) {
            throw RecoverableReply(
                "请先确认课程的生成设置。",
                "用户还未确认学习基础和课时数量。主题清楚时仅返回 offer_tree，主题不清楚时用 clarify_tree 追问。",
            )
        }
        if (options != null) {
            val courseCount = draft.trees.singleOrNull()?.skills?.size ?: 0
            if (courseCount !in options.courseCount) {
                val expected = "1 门课程，包含 ${options.courseCountLabel}课"
                throw RecoverableReply(
                    "请生成$expected。",
                    "上一版没有满足用户确认的课程数量或课时数量。请重新生成$expected，并输出完整 JSON。",
                )
            }
        }
        if (draft.skills.isNotEmpty() || (owner != null && (draft.directionId != null || draft.directionName != null)) ||
            (draft.trees.isEmpty() && (owner != null || draft.directionName == null || draft.directionId != null))
        ) {
            throw RecoverableReply("AI 返回的草稿与当前入口不匹配，请重试。",
                "草稿与当前入口不匹配。按当前入口和确认状态重新输出完整 JSON，检查 directionId、directionName、trees 和 skills。")
        }
        try {
            library.validateNewTrees(draft.prepareNewTrees().draft.trees)
        } catch (error: RuleViolation) {
            throw RecoverableReply(
                error.message ?: "生成的课程存在冲突，请重试。",
                "上一版课程没有通过校验。${error.message.orEmpty()} 请重新生成，核对名称和各门课程的先修关系后输出完整 JSON。",
            )
        }
        return Turn(id, Speaker.ASSISTANT, TurnKind.DRAFT, raw, version, resultId = owner?.id)
    }

    private suspend fun tutorTurn(raw: String, context: Request, id: String): Turn {
        val reply = AiProtocol.tutor(raw)
        if (!context.reissue) {
            try {
                TutorRules.validateVerdict(reply.kind, reply.exerciseId, context.pending, context.input)
            } catch (error: RuleViolation) {
                // 让模型结合当前题干重新判断原答案，避免仅替换标识后接受可能评错题的结果。
                val correction = context.pending?.takeIf { context.input.kind == TurnKind.MESSAGE }?.let {
                    "请只针对当前第 ${it.number} 题重新评估最后一条用户答案。若返回判定，exerciseId 必须原样填写 ${it.id}。"
                } ?: "最后一条用户消息不是当前练习的答案。按当前状态讲解或出第 1 题，禁止返回判定。"
                throw RecoverableReply(checkNotNull(error.message), correction)
            }
        }
        val earlierQuestions = if (context.reissue) context.askedQuestions.dropLast(1) else context.askedQuestions
        val question = reply.reply.filterNot(Char::isWhitespace)
        val correction = when {
            context.reissue && reply.kind != TurnKind.EXERCISE -> "重新出本题时只返回 exercise 和完整 question。"
            (reply.kind == TurnKind.NEXT_EXERCISE || context.reissue) &&
                earlierQuestions.any { it.filterNot(Char::isWhitespace).equals(question, ignoreCase = true) } ->
                "下一题的题干重复了，请用不同知识点或情境出题。"
            context.reissue -> null
            else -> TutorRules.assessmentCorrection(reply.kind, context.pending)
        }
        if (correction != null) {
            throw RecoverableReply("AI 的练习题序或内容有误，请重试。", correction)
        }
        if (context.input.kind == TurnKind.CHECK && reply.kind != TurnKind.EXERCISE) {
            throw RecoverableReply("AI 暂未给出练习题，请重试。", "用户已开始练习，只返回 exercise 和完整的第 1 题。")
        }
        var text = if (reply.kind == TurnKind.OFF_TOPIC) {
            "我们先回到「${context.owner?.name}」。你想继续哪个知识点，或试试一个小练习？"
        } else reply.reply
        var kind = if (reply.kind == TurnKind.NEXT_EXERCISE) TurnKind.EXERCISE else reply.kind
        if (kind == TurnKind.PASSED) {
            when (library.lightFromAssessment(checkNotNull(context.owner))) {
                AssessmentLight.LIT -> text = "练习通过\n$text"
                AssessmentLight.LOCKED -> {
                    kind = TurnKind.MESSAGE
                    text = "练习已通过。完成先修课后，需重新通过本节练习才能标记为已完成。\n\n$text"
                }
                AssessmentLight.UNCHANGED -> kind = TurnKind.MESSAGE
            }
        }
        val exerciseId = when (kind) {
            TurnKind.EXERCISE -> id
            TurnKind.RETRY, TurnKind.PASSED -> context.pending?.id
            else -> null
        }
        val exerciseNumber = when (reply.kind) {
            TurnKind.EXERCISE -> if (context.reissue) checkNotNull(context.pending).number else 1
            TurnKind.NEXT_EXERCISE -> checkNotNull(context.pending).number + 1
            else -> null
        }
        return Turn(id, Speaker.ASSISTANT, kind, text, context.input.contentVersion, exerciseId,
            choices = reply.choices, exerciseNumber = exerciseNumber)
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
            throw RuleViolation("完成全部先修课后才能学习本节。")
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

    private suspend fun messageWindow(
        scope: ChatScope, recent: List<Turn>, config: ModelSettings,
    ): List<ModelMessage> {
        val messages = recent.map {
            val role = if (it.speaker == Speaker.USER) "user" else "assistant"
            val text = when {
                scope.mode == ChatMode.TUTOR && it.speaker == Speaker.ASSISTANT -> AiProtocol.tutorHistory(it)
                scope.mode == ChatMode.GENERATE && it.speaker == Speaker.ASSISTANT -> AiProtocol.generationHistory(it)
                it.kind == TurnKind.CHECK -> "【App 发起课后练习】请出本节课后练习的第 1 题。"
                it.kind == TurnKind.RESTART -> "【App 发起重新学习】请按本节学习目标重新组织这节课。" +
                    if (it.text.isNotEmpty()) "\n本次学习要求：${it.text}" else ""
                else -> it.modelText
            }
            ModelMessage(role, text)
        }
        var remaining = CONTEXT_CHARS
        val window = messages.takeLastWhile {
            remaining -= it.text.length
            remaining >= 0
        }.dropWhile { it.role != "user" }
        // 保留最近一条资料消息的附件，供后续提问和「确认生成」使用，避免反复附带整段历史原件。
        val attachmentTurn = recent.indexOfLast { it.attachments.isNotEmpty() }
        val index = attachmentTurn - (recent.size - window.size)
        if (attachmentTurn < 0 || index !in window.indices) return window
        return window.toMutableList().apply {
            this[index] = attachments.addToMessage(scope.id, this[index], recent[attachmentTurn].attachments,
                config, required = attachmentTurn == recent.lastIndex)
        }
    }

    private fun Turn.row(scope: ChatScope) = TurnRow(
        id = id, chatId = scope.id, speaker = speaker.name, kind = kind.name,
        text = secret.seal(text, "$id:turn"), contentVersion = contentVersion,
        exerciseId = exerciseId, resultId = resultId,
        choices = choices.takeIf(List<String>::isNotEmpty)?.let { secret.seal(JSONArray(it).toString(), "$id:choices") },
        exerciseNumber = exerciseNumber,
        searchContext = searchSources?.let { secret.seal(it, "$id:search") },
        attachments = attachments.takeIf { it.isNotEmpty() }?.let { files ->
            val metadata = JSONArray()
            files.forEach { metadata.put(JSONObject().put("id", it.id).put("name", it.name).put("mimeType", it.mimeType)) }
            secret.seal(metadata.toString(), "$id:attachments")
        },
    )

    private fun TurnRow.toTurn(): Turn {
        val body = secret.open(text, "$id:turn")
        val turnKind = TurnKind.valueOf(kind)
        val sources = searchContext?.takeIf { speaker == Speaker.ASSISTANT.name }
            ?.let { secret.open(it, "$id:search") }
        if (turnKind == TurnKind.DRAFT) {
            val draft = checkNotNull(AiProtocol.generation(body, savedDraft = true).draft)
            val preview = GenerationPreview(draft.trees.map { it.name }, resultId ?: draft.directionId, draft.directionName)
            val summary = if (draft.trees.isEmpty()) "已生成方向草稿" else "已生成课程草稿"
            return Turn(id, Speaker.ASSISTANT, turnKind, summary + draft.prepareNewTrees().notice,
                contentVersion, preview = preview, searchSources = sources)
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
        }.orEmpty(), exerciseNumber = exerciseNumber, searchSources = sources,
            attachments = attachments?.let {
                val metadata = JSONArray(secret.open(it, "$id:attachments"))
                List(metadata.length()) { index ->
                    val file = metadata.getJSONObject(index)
                    ChatAttachment(file.getString("id"), file.getString("name"), file.getString("mimeType"))
                }
            }.orEmpty())
    }

    private data class Request(
        val owner: Entry?,
        val input: Turn,
        val pending: Exercise?,
        val askedQuestions: List<String>,
        val reissue: Boolean,
        val searchContext: String?,
        val system: ModelMessage,
        val history: List<Turn>,
    )

    companion object {
        const val INPUT_LIMIT = 4_000
        private const val CONTEXT_TURNS = 16
        private const val CONTEXT_CHARS = 16_000
    }
}
