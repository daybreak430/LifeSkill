package com.lifeskill.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeskill.app.data.ConversationRepository
import com.lifeskill.app.data.AttachmentDraft
import com.lifeskill.app.data.UsageTotal
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.CHAT_ATTACHMENT_LIMIT
import com.lifeskill.app.model.ChatAttachment
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.model.TutorRules
import com.lifeskill.app.model.choiceAnswer
import kotlinx.coroutines.flow.first

private const val CHAT_END = "chat-end"
private const val CHAT_STATUS = "chat-status"
private val creationCount = Regex("""([0-9]+) (?:个(方向|技能点)|棵(技能树)|门(课程)|节(课))""")

@Composable
internal fun ChatScreen(
    scope: ChatScope, library: Library, app: AppViewModel, chat: ChatViewModel, pageActive: Boolean,
    onExportConversation: (() -> Unit)?, lessonUsage: UsageTotal?,
) {
    val timeline by chat.history.collectAsStateWithLifecycle()
    val history = timeline.turns
    val status by chat.status.collectAsStateWithLifecycle()
    val webSearchAvailable by chat.webSearchAvailable.collectAsStateWithLifecycle()
    val editor by app.editor.collectAsStateWithLifecycle()
    val attachmentDrafts by chat.attachmentDrafts.collectAsStateWithLifecycle()
    val attachmentStatus by chat.attachmentStatus.collectAsStateWithLifecycle()
    var attachmentsExpanded by rememberSaveable(scope.id) { mutableStateOf(false) }
    var attachmentPreview by remember(scope) { mutableStateOf<ChatAttachment?>(null) }
    val owner = library.byId[scope.ownerId]
    val tutor = scope.mode == ChatMode.TUTOR
    val locked = tutor && owner != null && library.skillState(owner.id) == SkillState.LOCKED
    val pending = history.lastOrNull()?.takeIf { it.speaker == Speaker.USER }
    val currentLesson = remember(history, owner?.contentVersion) {
        if (tutor) TutorRules.currentLesson(history.filter { it.contentVersion == owner?.contentVersion }) else emptyList()
    }
    val exercise = remember(currentLesson) { TutorRules.pendingExercise(currentLesson) }
    var webSearch by rememberSaveable(scope.id) { mutableStateOf(true) }
    val supportsWebSearch = webSearchAvailable == true && !locked
    val showWebSearch = supportsWebSearch && exercise == null
    val lastQuizTurn = currentLesson.lastOrNull { it.speaker == Speaker.ASSISTANT && it.kind != TurnKind.OFF_TOPIC }
    val latestReply = currentLesson.lastOrNull()?.takeIf { it.speaker == Speaker.ASSISTANT }
    val examAvailable = !locked && TutorRules.canStartExam(currentLesson)
    val checkOffered = !locked && !examAvailable && latestReply?.kind == TurnKind.CHECK_OFFER
    var draft by remember(scope) { mutableStateOf("") }
    var showRestart by rememberSaveable { mutableStateOf(false) }
    var showingUsage by rememberSaveable(scope.id) { mutableStateOf(false) }
    var showClearConversation by rememberSaveable { mutableStateOf(false) }
    var openedDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedSourcesId by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSources = history.firstOrNull { it.id == selectedSourcesId }?.searchSources
    val draftTurn = history.firstOrNull { it.id == selectedDraftId && it.preview != null }
    val latestDraft = history.lastOrNull()?.takeIf { it.preview != null }
    val treeOffer = history.lastOrNull()?.takeIf { it.kind == TurnKind.TREE_OFFER }
    var openedOfferId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedOfferId by rememberSaveable { mutableStateOf<String?>(null) }
    val exceedsLimit = draft.trim().length > ConversationRepository.INPUT_LIMIT
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val listState = rememberLazyListState()
    var historyPositionInitialized by rememberSaveable { mutableStateOf(false) }
    var lastSeenMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var lastCelebratedId by rememberSaveable { mutableStateOf<String?>(null) }
    var openCelebrationId by rememberSaveable { mutableStateOf<String?>(null) }
    val newCelebrationId = timeline.celebrationId?.takeIf { it != lastCelebratedId }
    val celebration = newCelebrationId ?: openCelebrationId
    val statusMatches = status.requestId == null || status.requestId == pending?.id
    // 重试或撤回已提交时，旧历史快照可能晚一帧更新，不能重新出现重试按钮。
    val settled = status.requestId != null && statusMatches && !status.working && status.error == null
    val footerVisible = !status.clearing && !settled &&
        (pending != null || statusMatches && (status.working || status.error != null))
    val canSend = timeline.loaded && webSearchAvailable != null && !status.working && pending == null && !attachmentStatus.loading
    BackHandler(pageActive && attachmentsExpanded) { attachmentsExpanded = false }

    fun submit(text: String, check: Boolean = false, search: Boolean = false) {
        keyboard?.hide()
        chat.send(text, check, search)
    }

    fun previewAttachment(item: ChatAttachment) {
        keyboard?.hide()
        attachmentPreview = item
    }

    LaunchedEffect(pageActive) {
        if (pageActive) chat.refreshWebSearchAvailability()
    }

    fun openDraft(id: String) {
        keyboard?.hide()
        chat.clearDraftError()
        selectedDraftId = id
    }

    LaunchedEffect(pageActive, canSend, latestDraft?.id, editor == null, showClearConversation) {
        if (pageActive && canSend && editor == null && !showClearConversation &&
            latestDraft != null && openedDraftId != latestDraft.id
        ) {
            openedDraftId = latestDraft.id
            openDraft(latestDraft.id)
        }
    }

    LaunchedEffect(pageActive, canSend, treeOffer?.id, showClearConversation) {
        if (pageActive && canSend && !showClearConversation && treeOffer != null && openedOfferId != treeOffer.id) {
            keyboard?.hide()
            openedOfferId = treeOffer.id
            selectedOfferId = treeOffer.id
        }
    }

    LaunchedEffect(pageActive, newCelebrationId) {
        if (newCelebrationId != null) {
            lastCelebratedId = newCelebrationId
            if (pageActive) openCelebrationId = newCelebrationId
        }
    }

    LaunchedEffect(pageActive, timeline.loaded, history.lastOrNull()?.id) {
        if (!pageActive || !timeline.loaded) return@LaunchedEffect
        val initial = !historyPositionInitialized
        historyPositionInitialized = true
        val last = history.lastOrNull() ?: return@LaunchedEffect
        if (lastSeenMessage == last.id) return@LaunchedEffect
        lastSeenMessage = last.id
        val sent = last.speaker == Speaker.USER
        // 仅初次打开历史或发送消息时定位。收到回复时由列表按消息键保留当前阅读锚点。
        if (!initial && !sent) return@LaunchedEffect
        val destination = history.size + if (footerVisible && sent) 1 else 0
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > destination }
        if (initial) listState.scrollToItem(destination) else listState.animateScrollToItem(destination)
    }

    Column(Modifier.fillMaxSize()) {
        AiChatHeader(
            when {
                tutor -> "AI 老师"
                owner?.kind == EntryKind.TREE -> "AI 补充课时"
                else -> "AI 生成课程"
            }, owner?.name, app::back,
            onRestart = if (tutor) ({ showRestart = true }) else null,
            restartEnabled = !status.working && webSearchAvailable != null,
            onShowUsage = if (tutor) ({
                keyboard?.hide()
                showingUsage = true
            }) else null,
            onExportConversation = onExportConversation?.let { export -> {
                keyboard?.hide()
                export()
            } },
            onClearConversation = if (!tutor) ({
                keyboard?.hide()
                showClearConversation = true
            }) else null,
        )
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("chat-messages"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (history.isEmpty()) {
                item {
                    EmptyState(
                        owner?.name ?: "一起规划课程",
                        when {
                            tutor -> owner?.goal.orEmpty()
                            owner?.kind == EntryKind.TREE -> "告诉 AI 你想补充哪些内容"
                            else -> "描述你想学的主题和目标"
                        },
                    ) { AiCrystal(54.dp, forAction = !tutor) }
                    if (tutor && canSend) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ChatPill("开始学习", {
                                submit(TutorRules.START_REPLY, search = showWebSearch && webSearch)
                            }, glyph = Glyph.SPARK)
                        }
                    }
                }
            }
            items(history, key = Turn::id) { turn ->
                val active = exercise != null && turn.id == lastQuizTurn?.id && turn.exerciseId == exercise.id
                val choices = if (active) exercise.choices else turn.choices
                Column(
                    // 尺寸动画会把气泡阴影裁在消息边界内，列表只保留淡入淡出。
                    Modifier.animateItem(fadeInSpec = tween(170), fadeOutSpec = tween(120), placementSpec = null),
                ) {
                    turn.searchSources?.let { sources ->
                        val links = sources.searchSourceLinks()
                        if (links.isNotBlank()) {
                            ChatPill(
                                "查看来源 · ${links.lineSequence().count()}",
                                onClick = {
                                    keyboard?.hide()
                                    selectedSourcesId = turn.id
                                },
                                modifier = Modifier.padding(bottom = 4.dp),
                                glyph = Glyph.GLOBE, trailingGlyph = Glyph.NEXT,
                            )
                        } else {
                            Text(sources, Modifier.padding(start = 15.dp, bottom = 6.dp),
                                style = MaterialTheme.typography.labelMedium, color = Muted)
                        }
                    }
                    ChatAttachmentStrip(turn.attachments.filter { it.imageCredit == null }, chat::loadAttachment, ::previewAttachment, sent = true)
                    if (turn.text.isNotEmpty() || turn.attachments.isEmpty()) ChatBubble(
                        turn = turn,
                        result = library.byId[turn.resultId],
                        choices = choices,
                        active = active,
                        canAnswer = canSend,
                        onAnswer = { submit(it) },
                        onOpenDraft = if (turn.preview != null) ({ openDraft(turn.id) }) else null,
                        open = { app.open(Route.entry(it)) },
                        loadAttachment = chat::loadAttachment,
                        previewAttachment = ::previewAttachment,
                    )
                    if (turn.id == treeOffer?.id) {
                        ChatPill(
                            "生成设置",
                            onClick = {
                                keyboard?.hide()
                                selectedOfferId = turn.id
                            },
                            modifier = Modifier.padding(top = 6.dp),
                            enabled = canSend, trailingGlyph = Glyph.NEXT,
                        )
                    }
                    if (turn.id == latestReply?.id) {
                        if (choices.isEmpty() && (exercise == null || exercise.choices.isEmpty())) {
                            val quickReply = if (exercise == null) TutorRules.CONTINUE_REPLY else TutorRules.REISSUE_REPLY
                            FlowRow(
                                Modifier.padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ChatPill(
                                    quickReply,
                                    onClick = { submit(quickReply, search = showWebSearch && webSearch) },
                                    enabled = canSend,
                                )
                                if (examAvailable && !checkOffered) {
                                    ChatPill(
                                        TutorRules.EXAM_REPLY,
                                        onClick = { submit(TutorRules.EXAM_REPLY, check = true) },
                                        enabled = canSend, glyph = Glyph.TARGET,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (footerVisible) {
                item(key = CHAT_STATUS) {
                    // 回复插入时立即移除旧卡片，避免退出动画绘制到新气泡上。
                    ChatFooter(status.takeIf { statusMatches } ?: ChatStatus(), pending, retry = chat::retry) { turn ->
                        if (turn.kind == TurnKind.MESSAGE) chat.editPending(turn) { draft = turn.text }
                        else chat.discard(turn)
                    }
                }
            }
            item(key = CHAT_END) { Spacer(Modifier.height(1.dp)) }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp, top = 4.dp),
        ) {
            if (locked) {
                Text(
                    "完成先修课后可做本节练习", Modifier.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = Muted,
                )
            } else if (checkOffered) {
                ChatPill(
                    "做课后练习",
                    onClick = { submit("", check = true) },
                    enabled = canSend, glyph = Glyph.TARGET,
                )
            }
            AiComposer(
                draft, { draft = it; attachmentsExpanded = false },
                if (tutor) "输入学习问题" else "描述你想学什么", enabled = canSend && !locked,
                errorText = if (exceedsLimit) "每条消息最多 ${ConversationRepository.INPUT_LIMIT} 个字符" else null,
                sendEnabled = (draft.isNotBlank() || attachmentDrafts.isNotEmpty()) && !exceedsLimit && canSend && !locked,
                webSearch = webSearch.takeIf { showWebSearch },
                onToggleSearch = { webSearch = !webSearch },
                onSend = {
                    keyboard?.hide()
                    attachmentsExpanded = false
                    chat.send(draft, webSearch = showWebSearch && webSearch, includeAttachments = true) { draft = "" }
                },
                attachmentsExpanded = attachmentsExpanded,
                attachEnabled = canSend && !locked && (attachmentsExpanded || attachmentDrafts.size < CHAT_ATTACHMENT_LIMIT),
                onToggleAttachments = {
                    if (!attachmentsExpanded) {
                        focus.clearFocus()
                        keyboard?.hide()
                    }
                    attachmentsExpanded = !attachmentsExpanded
                },
                attachmentStrip = {
                    ChatAttachmentStrip(attachmentDrafts.map { it.attachment }, chat::loadAttachment, ::previewAttachment,
                        onRemove = { chat.removeAttachment(it.id) })
                },
            )
            ChatAttachmentSources(chat, attachmentsExpanded, { attachmentsExpanded = false }, canSend && !locked)
            val attachmentMessage = if (attachmentStatus.loading) "正在读取附件…" else attachmentStatus.message
            attachmentMessage?.let { Text(it, Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                color = Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
    if (pageActive) attachmentPreview?.let { ChatAttachmentPreview(it, chat) { attachmentPreview = null } }
    if (pageActive && owner != null) {
        celebration?.let { id ->
            SkillCelebration(id, owner) {
                lastCelebratedId = id
                openCelebrationId = null
            }
        }
    }
    if (pageActive && selectedSources != null) {
        SearchSourcesSheet(selectedSources.searchSourceLinks()) { selectedSourcesId = null }
    }
    if (pageActive && showingUsage) {
        AiUsageSheet("本节课", lessonUsage) { showingUsage = false }
    }
    if (pageActive && showRestart) {
        RestartLessonDialog(
            onDismiss = { showRestart = false },
            onStart = { requirements ->
                showRestart = false
                keyboard?.hide()
                chat.restartLesson(requirements, webSearch = supportsWebSearch && webSearch)
                chat.clearAttachments()
                attachmentsExpanded = false
                draft = ""
            },
        )
    }
    if (pageActive && showClearConversation) {
        ClearConversationDialog(
            onDismiss = { showClearConversation = false },
            onConfirm = {
                showClearConversation = false
                draft = ""
                chat.clearAttachments()
                attachmentsExpanded = false
                selectedDraftId = null
                selectedOfferId = null
                openedDraftId = latestDraft?.id ?: openedDraftId
                openedOfferId = treeOffer?.id ?: openedOfferId
                chat.clearConversation()
            },
        )
    }
    if (pageActive && !showClearConversation && canSend && treeOffer != null && treeOffer.id == selectedOfferId) {
        TreeGenerationDialog(
            onDismiss = { selectedOfferId = null },
            onConfirm = { options ->
                selectedOfferId = null
                chat.generateTree(treeOffer.id, options)
            },
        )
    }
    draftTurn?.takeIf { pageActive && editor == null && !showClearConversation }?.let { turn ->
        val preview = checkNotNull(turn.preview)
        AddDraftDialog(
            preview, library.childrenOf(null), status.working, status.draftError,
            onDismiss = { selectedDraftId = null },
            onCreateDirection = {
                // 仅建方向的草稿可直接手动完成。技能树草稿在方向编辑器关闭后继续确认。
                if (preview.treeNames.isEmpty()) selectedDraftId = null
                app.add(EntryKind.DIRECTION)
            },
            onSelectionChange = chat::clearDraftError,
            onConfirm = { chat.confirmDraft(turn.id, it) },
        )
    }
}

@Composable
private fun ChatBubble(
    turn: Turn,
    result: Entry?,
    choices: List<String>,
    active: Boolean,
    canAnswer: Boolean,
    onAnswer: (String) -> Unit,
    onOpenDraft: (() -> Unit)?,
    open: (Entry) -> Unit,
    loadAttachment: suspend (ChatAttachment) -> AttachmentDraft,
    previewAttachment: (ChatAttachment) -> Unit,
) {
    if (turn.kind == TurnKind.RESTART) {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            HorizontalDivider(color = aiColors.outline)
            Text("新一轮学习", style = MaterialTheme.typography.labelLarge, color = aiColors.accent)
            if (turn.text.isNotEmpty()) {
                Text(turn.text, style = MaterialTheme.typography.bodyMedium, color = Muted)
            }
        }
        return
    }
    val user = turn.speaker == Speaker.USER
    val created = turn.kind == TurnKind.CREATED
    val colors = aiColors
    val shape = RoundedCornerShape(20.dp, 20.dp, if (user) 12.dp else 20.dp, if (user) 20.dp else 12.dp)
    Box(
        modifier = Modifier.fillMaxWidth().padding(start = if (user) 44.dp else 0.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        Surface(
            modifier = if (user) Modifier else Modifier.fillMaxWidth(),
            color = if (user) Color.Transparent else colors.panel,
            shape = shape, shadowElevation = if (user) 0.dp else 2.dp,
        ) {
            if (created) {
                CreationReceipt(turn.text, result, open)
                return@Surface
            }
            if (onOpenDraft != null) {
                Row(
                    Modifier.clickable(
                        enabled = canAnswer, onClickLabel = "选择学习方向", role = Role.Button, onClick = onOpenDraft,
                    ).heightIn(min = 48.dp).padding(horizontal = 15.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(turn.text, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.bodyLarge)
                    Mark(Glyph.NEXT, colors.accent, Modifier.size(15.dp))
                }
                return@Surface
            }
            val backdrop = if (user) Modifier.background(Brush.linearGradient(colors.outgoing)) else Modifier
            Column(backdrop.padding(horizontal = 15.dp, vertical = 12.dp)) {
                val badge = when (turn.kind) {
                    TurnKind.EXERCISE -> "课后练习 · 第 ${turn.exerciseNumber ?: 1} 题"
                    TurnKind.RETRY -> "再试一次"
                    else -> null
                }
                if (badge != null) {
                    Text(badge, color = colors.accent, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 5.dp))
                }
                turn.attachments.filter { it.imageCredit != null }.forEach { image ->
                    LessonIllustration(image, loadAttachment) { previewAttachment(image) }
                }
                SelectionContainer {
                    if (user) {
                        Text(turn.text, style = MaterialTheme.typography.bodyLarge, color = Color.White)
                    } else {
                        LessonMarkdown(turn.text)
                    }
                }
                if (choices.isNotEmpty()) {
                    Column(Modifier.padding(top = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        choices.forEachIndexed { index, choice ->
                            val answer = choiceAnswer(index, choice)
                            if (active) {
                                Button(
                                    onClick = { onAnswer(answer) }, modifier = Modifier.fillMaxWidth(),
                                    enabled = canAnswer,
                                    shape = ControlShape, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.chip, contentColor = colors.accent,
                                        disabledContainerColor = Raised, disabledContentColor = Muted,
                                    ),
                                ) { Text(answer, Modifier.fillMaxWidth()) }
                            } else {
                                Text(answer, Modifier.padding(vertical = 3.dp), color = Muted, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (result != null) {
                    TextButton(onClick = { open(result) }) {
                        Text(result.openLabel(), Modifier.weight(1f, fill = false), color = colors.accent)
                        Mark(Glyph.NEXT, colors.accent, Modifier.padding(start = 4.dp).size(15.dp))
                    }
                }
            }
        }
    }
}

private fun Entry.openLabel(): String = when (kind) {
    EntryKind.DIRECTION -> "查看方向"
    EntryKind.TREE -> "查看课程"
    EntryKind.SKILL -> "查看本节课"
} + " · $name"

@Composable
private fun CreationReceipt(text: String, result: Entry?, open: (Entry) -> Unit) {
    val colors = aiColors
    val summary = text.substringBefore('\n')
    val counts = remember(summary) {
        // 旧消息存的是应用生成的计数文本。逐项匹配，保留无法识别的历史格式。
        val countsText = summary.removePrefix("已新增 ").removePrefix("已添加 ")
        if (countsText == summary) emptyList()
        else countsText.split('、').map { creationCount.matchEntire(it) }
            .takeIf { parts -> parts.all { it != null } }.orEmpty().filterNotNull()
    }
    val note = if (counts.isEmpty()) text.removePrefix("已新增 ").removePrefix("已添加 ").trim()
        else text.substringAfter('\n', "").trim().replace("前置关系", "先修关系")
    Column(
        Modifier.background(Brush.verticalGradient(listOf(lerp(colors.panel, colors.chip, .32f), colors.panel)))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            Emblem(Glyph.CHECK, colors.accent, colors.chip, diameter = 24.dp)
            Text("已添加", color = Ink, style = MaterialTheme.typography.titleSmall)
        }
        if (counts.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                counts.forEach { count ->
                    Column(Modifier.weight(1f)) {
                        Text(count.groupValues[1], color = colors.accent,
                            style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                        Text(when (val unit = count.groupValues.drop(2).first { it.isNotEmpty() }) {
                            "技能树" -> "课程"
                            "技能点", "课" -> "课时"
                            else -> unit
                        }, color = Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (note.isNotEmpty()) Text(note, color = Muted, style = MaterialTheme.typography.bodySmall)
        if (result != null) {
            Surface(
                onClick = { open(result) }, shape = ControlShape, color = colors.chip,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = result.openLabel()
                },
            ) {
                Row(
                    Modifier.heightIn(min = 48.dp).padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Mark(result.displayIcon.glyph, colors.accent, Modifier.size(20.dp))
                    Column(Modifier.weight(1f).clearAndSetSemantics {}) {
                        Text(result.name, color = Ink, style = MaterialTheme.typography.labelLarge,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(result.kind.label, color = colors.accent, style = MaterialTheme.typography.labelSmall)
                    }
                    Mark(Glyph.NEXT, colors.accent, Modifier.size(15.dp))
                }
            }
        }
    }
}

@Composable
private fun ChatFooter(status: ChatStatus, pending: Turn?, retry: (Turn) -> Unit, discard: (Turn) -> Unit) {
    Surface(
        Modifier.fillMaxWidth().animateContentSize(tween(170))
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = ControlShape, color = aiColors.panel,
    ) {
        Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 6.dp)) {
            if (status.working) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Loading(Modifier.size(25.dp))
                    Text(
                        if (pending?.kind == TurnKind.GENERATE_TREE) "正在生成课程" else "正在等待 AI 回复",
                        style = MaterialTheme.typography.labelMedium, color = Muted,
                    )
                }
            } else {
                Text(
                    status.error ?: "这条消息还没有回复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.error == null) Muted else MaterialTheme.colorScheme.error,
                )
                if (pending != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ChatPill("重试", { retry(pending) })
                        TextButton(onClick = { discard(pending) }) {
                            Text(when (pending.kind) {
                                TurnKind.CHECK -> "取消练习请求"
                                TurnKind.RESTART -> "取消请求"
                                TurnKind.GENERATE_TREE -> "取消生成"
                                else -> "撤回并编辑"
                            }, color = aiColors.accent)
                        }
                    }
                }
            }
        }
    }
}
