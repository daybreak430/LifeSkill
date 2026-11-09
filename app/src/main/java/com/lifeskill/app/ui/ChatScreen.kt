package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.Speaker
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind

@Composable
internal fun ChatScreen(scope: ChatScope, library: Library, app: AppViewModel, chat: ChatViewModel) {
    val history by chat.history.collectAsStateWithLifecycle()
    val status by chat.status.collectAsStateWithLifecycle()
    val owner = library.byId[scope.ownerId]
    val tutor = scope.mode == ChatMode.TUTOR
    val pending = history.lastOrNull()?.takeIf { it.speaker == Speaker.USER }
    var draft by remember(scope) { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val footerVisible = status.working || pending != null || status.error != null

    fun send(text: String) {
        keyboard?.hide()
        chat.send(text)
        draft = ""
    }

    LaunchedEffect(history.lastOrNull()?.id, footerVisible) {
        if (history.isNotEmpty()) {
            // 等下一次测量定位末尾，避免重建页面或键盘改变视口时触发嵌套测量。
            listState.requestScrollToItem(history.lastIndex + if (footerVisible) 1 else 0)
        }
    }

    Column(Modifier.fillMaxSize()) {
        PageHeader(if (tutor) "AI 老师" else "AI 生成", app::back, subtitle = owner?.name) {
            GlyphButton(Glyph.SETTINGS, "模型设置", { app.open(Route(Page.SETTINGS)) })
        }
        HorizontalDivider(color = Line.copy(alpha = .6f))
        LazyColumn(
            state = listState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("chat-messages"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
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
                        if (tutor) Glyph.TARGET else Glyph.SPARK,
                    )
                    if (tutor && !status.working) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Action("开始学习", Glyph.SPARK, { send("请引导我开始学习这个技能点。") })
                        }
                    }
                }
            }
            items(history, key = Turn::id) { turn ->
                ChatBubble(turn, library.byId[turn.resultId]) { app.open(Route.entry(it)) }
            }
            if (footerVisible) {
                item(key = "chat-status") {
                    ChatFooter(status, pending, chat::retry) { turn ->
                        if (turn.kind == TurnKind.MESSAGE) draft = turn.text
                        chat.discard(turn)
                    }
                }
            }
        }
        HorizontalDivider(color = Line.copy(alpha = .6f))
        Column(
            Modifier.fillMaxWidth().padding(start = 15.dp, end = 12.dp, bottom = 9.dp, top = 4.dp),
        ) {
            if (tutor) {
                TextButton(
                    onClick = { keyboard?.hide(); chat.send("", check = true) },
                    enabled = !status.working && pending == null,
                ) {
                    Mark(Glyph.TARGET, if (status.working || pending != null) Line else Gold, Modifier.size(16.dp))
                    Text("检验掌握", Modifier.padding(start = 6.dp))
                }
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= ConversationRepository.INPUT_LIMIT) draft = it },
                    modifier = Modifier.weight(1f).testTag("chat-input"),
                    label = { Text(if (tutor) "输入学习问题" else "描述你想学什么") },
                    minLines = 1, maxLines = 4, shape = RoundedCornerShape(13.dp),
                    enabled = !status.working && pending == null,
                )
                GlyphButton(
                    Glyph.SEND, "发送", { send(draft) },
                    tint = Gold, enabled = draft.isNotBlank() && !status.working && pending == null,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(turn: Turn, result: Entry?, open: (Entry) -> Unit) {
    val user = turn.speaker == Speaker.USER
    val prominent = turn.kind == TurnKind.CREATED || turn.kind == TurnKind.PASSED
    Column(
        modifier = Modifier.fillMaxWidth().padding(
            start = if (user) 38.dp else 0.dp, end = if (user) 0.dp else 24.dp,
        ),
        horizontalAlignment = if (user) Alignment.End else Alignment.Start,
    ) {
        val background = when {
            prominent -> GoldGround
            user -> Raised
            else -> Panel
        }
        Surface(
            color = background,
            border = BorderStroke(1.dp, if (prominent) Gold.copy(alpha = .55f) else Line),
            shape = RoundedCornerShape(14.dp),
        ) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 10.dp)) {
                val badge = when (turn.kind) {
                    TurnKind.EXERCISE -> "掌握检查"
                    TurnKind.RETRY -> "再试一次"
                    else -> null
                }
                if (badge != null) {
                    Text(badge, color = Gold, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(bottom = 5.dp))
                }
                SelectionContainer {
                    Text(turn.text, style = MaterialTheme.typography.bodyLarge, color = if (prominent) Gold else Ink)
                }
                if (result != null) {
                    TextButton(onClick = { open(result) }) {
                        Text("打开${result.kind.label} · ${result.name}", Modifier.weight(1f, fill = false))
                        Mark(Glyph.NEXT, Gold, Modifier.padding(start = 4.dp).size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatFooter(status: ChatStatus, pending: Turn?, retry: () -> Unit, discard: (Turn) -> Unit) {
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
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
                Row {
                    TextButton(onClick = retry) { Text("重试") }
                    TextButton(onClick = { discard(pending) }) {
                        Text(if (pending.kind == TurnKind.CHECK) "撤回检验" else "撤回编辑")
                    }
                }
            }
        }
    }
}
