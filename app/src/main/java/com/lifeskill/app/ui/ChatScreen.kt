package com.lifeskill.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeskill.app.data.ConversationRepository
import com.lifeskill.app.model.ChatMode
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

private val ReplyInset = 42.dp

@Composable
internal fun ChatScreen(scope: ChatScope, library: Library, app: AppViewModel, chat: ChatViewModel, pageActive: Boolean) {
    val history by chat.history.collectAsStateWithLifecycle()
    val status by chat.status.collectAsStateWithLifecycle()
    val owner = library.byId[scope.ownerId]
    val tutor = scope.mode == ChatMode.TUTOR
    val locked = tutor && owner != null && library.skillState(owner.id) == SkillState.LOCKED
    val pending = history.lastOrNull()?.takeIf { it.speaker == Speaker.USER }
    val currentLesson = remember(history, owner?.contentVersion) {
        if (tutor) TutorRules.currentLesson(history.filter { it.contentVersion == owner?.contentVersion }) else emptyList()
    }
    val exercise = remember(currentLesson) { TutorRules.pendingExercise(currentLesson) }
    val lastQuizTurn = currentLesson.lastOrNull { it.speaker == Speaker.ASSISTANT && it.kind != TurnKind.OFF_TOPIC }
    val latestReply = currentLesson.lastOrNull()?.takeIf { it.speaker == Speaker.ASSISTANT }
    val examAvailable = !locked && TutorRules.canStartExam(currentLesson)
    val checkOffered = !locked && !examAvailable && latestReply?.kind == TurnKind.CHECK_OFFER
    var draft by remember(scope) { mutableStateOf("") }
    var showRestart by rememberSaveable { mutableStateOf(false) }
    var selectedDraftId by rememberSaveable { mutableStateOf<String?>(null) }
    val draftTurn = history.firstOrNull { it.id == selectedDraftId && it.preview != null }
    val exceedsLimit = draft.trim().length > ConversationRepository.INPUT_LIMIT
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var lastSeenMessage by rememberSaveable { mutableStateOf<String?>(null) }
    val footerVisible = status.working || pending != null || status.error != null
    val canSend = !status.working && pending == null

    LaunchedEffect(pageActive, history.lastOrNull()?.id) {
        val last = history.lastOrNull() ?: return@LaunchedEffect
        if (!pageActive || lastSeenMessage == last.id) return@LaunchedEffect
        val initial = lastSeenMessage == null
        val layout = listState.layoutInfo
        val nearEnd = (layout.visibleItemsInfo.lastOrNull()?.index ?: 0) >= layout.totalItemsCount - 2
        lastSeenMessage = last.id
        // 阅读历史时保留锚点，仅发送消息或原本在末尾时跟随新回复。
        if (!initial && !nearEnd && last.speaker != Speaker.USER) return@LaunchedEffect
        val destination = history.lastIndex + if (footerVisible && last.speaker == Speaker.USER) 1 else 0
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > destination }
        if (initial) listState.scrollToItem(destination) else listState.animateScrollToItem(destination)
    }

    Column(Modifier.fillMaxSize()) {
        AiChatHeader(
            if (tutor) "AI 老师" else "AI 生成", owner?.name, app::back,
            onRestart = if (tutor) ({ showRestart = true }) else null,
            restartEnabled = !status.working, onSettings = { app.open(Route(Page.SETTINGS)) },
        )
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("chat-messages"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (history.isEmpty()) {
                item {
                    EmptyState(
                        owner?.name ?: "一起规划技能树",
                        if (tutor) owner?.goal.orEmpty() else if (owner != null) {
                            "告诉 AI 你想在这里新增什么。"
                        } else {
                            "描述你想学的主题、已有基础和目标。"
                        },
                    ) { AiCrystal(54.dp, forAction = !tutor) }
                    if (tutor && !status.working) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            ChatPill("开始学习", {
                                keyboard?.hide()
                                chat.send("请引导我开始学习这个技能点。")
                            }, glyph = Glyph.SPARK)
                        }
                    }
                }
            }
            items(history, key = Turn::id) { turn ->
                val active = exercise != null && turn.id == lastQuizTurn?.id && turn.exerciseId == exercise.id
                val choices = if (active) exercise.choices else turn.choices
                Column(
                    Modifier.animateItem(fadeInSpec = tween(170), fadeOutSpec = tween(120), placementSpec = null)
                        .animateContentSize(tween(170)),
                ) {
                    ChatBubble(
                        turn = turn,
                        result = library.byId[turn.resultId],
                        choices = choices,
                        active = active,
                        canAnswer = canSend,
                        onAnswer = { answer ->
                            keyboard?.hide()
                            chat.send(answer)
                        },
                        open = { app.open(Route.entry(it)) },
                    )
                    if (turn.preview != null) {
                        ChatPill(
                            "选择方向",
                            onClick = {
                                keyboard?.hide()
                                chat.clearDraftError()
                                selectedDraftId = turn.id
                            },
                            modifier = Modifier.padding(start = ReplyInset, top = 6.dp),
                            enabled = canSend, trailingGlyph = Glyph.NEXT,
                        )
                    }
                    if (turn.id == latestReply?.id) {
                        if (checkOffered) {
                            Text(
                                "可以试试「检验掌握」", Modifier.padding(start = ReplyInset, top = 8.dp),
                                style = MaterialTheme.typography.bodySmall, color = Muted,
                            )
                        }
                        if (choices.isEmpty()) {
                            FlowRow(
                                Modifier.padding(start = ReplyInset, top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ChatPill(
                                    TutorRules.CONTINUE_REPLY,
                                    onClick = { keyboard?.hide(); chat.send(TutorRules.CONTINUE_REPLY) },
                                    enabled = canSend,
                                )
                                if (examAvailable) {
                                    ChatPill(
                                        TutorRules.EXAM_REPLY,
                                        onClick = { keyboard?.hide(); chat.send(TutorRules.EXAM_REPLY, check = true) },
                                        enabled = canSend, glyph = Glyph.TARGET,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (footerVisible) {
                item(key = "chat-status") {
                    ChatFooter(status, pending, chat::retry, Modifier.animateItem(placementSpec = null)) { turn ->
                        if (turn.kind == TurnKind.MESSAGE) draft = turn.text
                        chat.discard(turn)
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 10.dp, top = 4.dp),
        ) {
            if (locked) {
                Text(
                    "点亮前置技能后可检验掌握", Modifier.padding(vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium, color = Muted,
                )
            } else if (checkOffered) {
                ChatPill(
                    "检验掌握",
                    onClick = { keyboard?.hide(); chat.send("", check = true) },
                    enabled = canSend, glyph = Glyph.TARGET,
                )
            }
            AiComposer(
                draft, { draft = it }, if (tutor) "输入学习问题" else "描述你想学什么", enabled = canSend,
                errorText = if (exceedsLimit) "每条消息最多 ${ConversationRepository.INPUT_LIMIT} 个字符" else null,
                sendEnabled = draft.isNotBlank() && !exceedsLimit && canSend,
                onSend = {
                    keyboard?.hide()
                    chat.send(draft)
                    draft = ""
                },
            )
        }
    }
    if (pageActive && showRestart) {
        RestartLessonDialog(
            onDismiss = { showRestart = false },
            onStart = { requirements ->
                showRestart = false
                keyboard?.hide()
                chat.restartLesson(requirements)
                draft = ""
            },
        )
    }
    draftTurn?.takeIf { pageActive }?.let { turn ->
        AddDraftDialog(
            checkNotNull(turn.preview), library.childrenOf(null), status.working, status.draftError,
            onDismiss = { selectedDraftId = null },
            onCreateDirection = {
                selectedDraftId = null
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
    open: (Entry) -> Unit,
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
    val prominent = turn.kind == TurnKind.CREATED || turn.kind == TurnKind.PASSED
    val colors = aiColors
    val shape = RoundedCornerShape(20.dp, 20.dp, if (user) 12.dp else 20.dp, if (user) 20.dp else 12.dp)
    Row(
        modifier = Modifier.fillMaxWidth().padding(
            start = if (user) 44.dp else 0.dp, end = if (user) 0.dp else 14.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(6.dp, if (user) Alignment.End else Alignment.Start),
    ) {
        if (!user) AiCrystal(36.dp)
        Surface(
            color = if (user) Color.Transparent else colors.panel,
            shape = shape, shadowElevation = if (user) 0.dp else 2.dp,
        ) {
            val backdrop = if (user) Modifier.background(Brush.linearGradient(colors.outgoing)) else Modifier
            Column(backdrop.padding(horizontal = 15.dp, vertical = 12.dp)) {
                val badge = when (turn.kind) {
                    TurnKind.EXERCISE -> "掌握检查"
                    TurnKind.RETRY -> "再试一次"
                    else -> null
                }
                if (badge != null) {
                    Text(badge, color = colors.accent, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(bottom = 5.dp))
                }
                SelectionContainer {
                    if (user || turn.kind == TurnKind.CREATED) {
                        Text(turn.text, style = MaterialTheme.typography.bodyLarge,
                            color = if (user) Color.White else if (prominent) colors.accent else Ink)
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
                        Text("打开${result.kind.label} · ${result.name}", Modifier.weight(1f, fill = false), color = colors.accent)
                        Mark(Glyph.NEXT, colors.accent, Modifier.padding(start = 4.dp).size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatFooter(status: ChatStatus, pending: Turn?, retry: () -> Unit, modifier: Modifier, discard: (Turn) -> Unit) {
    Surface(
        modifier.fillMaxWidth().padding(start = ReplyInset).animateContentSize(tween(170))
            .semantics { liveRegion = LiveRegionMode.Polite },
        shape = ControlShape, color = aiColors.panel,
    ) {
        Column(Modifier.padding(start = 12.dp, end = 10.dp, top = 9.dp, bottom = 6.dp)) {
            if (status.working) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Loading(Modifier.size(25.dp))
                    Text("正在等待 AI 回复", style = MaterialTheme.typography.labelMedium, color = Muted)
                }
            } else {
                Text(
                    status.error ?: "这条消息还没有回复。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (status.error == null) Muted else MaterialTheme.colorScheme.error,
                )
                if (pending != null) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ChatPill("重试", retry)
                        TextButton(onClick = { discard(pending) }) {
                            Text(when (pending.kind) {
                                TurnKind.CHECK -> "撤回检验"
                                TurnKind.RESTART -> "取消请求"
                                else -> "撤回编辑"
                            }, color = aiColors.accent)
                        }
                    }
                }
            }
        }
    }
}
