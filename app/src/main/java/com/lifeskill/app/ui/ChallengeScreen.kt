package com.lifeskill.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeskill.app.model.DIAGNOSTIC_QUESTIONS
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.TreeChallenge

/** 页尾状态：0 未开始、1 通过、2 未通过、3 出题中。带题目，换题也走同一条过渡。 */
private data class ChallengeTail(val stage: Int, val prompt: String? = null)

@Composable
internal fun ChallengeScreen(
    tree: Entry, kind: TreeChallenge, passedExam: Boolean, app: AppViewModel, model: TreeAssessmentViewModel,
) {
    val session by model.challenge(kind).collectAsStateWithLifecycle()
    val status by model.challengeStatus(kind).collectAsStateWithLifecycle()
    val drafts by model.challengeDrafts.collectAsStateWithLifecycle()
    val input = drafts[kind].orEmpty()
    val current = session
    val state = rememberLazyListState()
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(current) {
        if (current != null) state.scrollToItem(current.exchanges.size)
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(kind.title, app::back, subtitle = tree.name)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("challenge-messages"), state = state,
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(current?.exchanges.orEmpty()) { index, exchange ->
                Column(Modifier.animateItem(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    ChallengeMessage(
                        if (kind == TreeChallenge.DIAGNOSTIC) "第 ${index + 1} 题" else "练习",
                        exchange.prompt,
                    )
                    ChallengeMessage("你的回答", exchange.answer, user = true)
                    ChallengeMessage("AI 反馈", exchange.feedback)
                }
            }
            item(key = "challenge-tail") {
                // 页尾在“准备开始 / 出题 / 完成”之间换挡，题目本身也会随轮次变化：
                // 统一淡入淡出，读者能分清是同一道题更新了，还是进入下一题。
                val tail = when {
                    current == null -> ChallengeTail(0)
                    current.passed -> ChallengeTail(1)
                    current.finished -> ChallengeTail(2)
                    else -> ChallengeTail(3, current.prompt)
                }
                AnimatedContent(
                    targetState = tail,
                    transitionSpec = { contentSwap() },
                    label = "练习页尾",
                ) { shown ->
                    when (shown.stage) {
                        0 -> {
                            EmptyState(
                                when {
                                    !passedExam -> "通过结业考试后可开始复习。"
                                    kind == TreeChallenge.DIAGNOSTIC -> "回答 3 道看似简单的问题，找出理解上的漏洞。"
                                    else -> "AI 会从课程里指定一个概念、过程或方法论，请用自己的话讲给 10 岁的孩子听。"
                                },
                            ) { ChallengeSigilMedallion(kind, 70.dp) }
                            if (passedExam) Action(if (status.busy) "正在准备" else "开始练习", Glyph.SPARK,
                                { model.startChallenge(kind) }, Modifier.fillMaxWidth(), primary = true, enabled = !status.busy)
                        }
                        1 -> EmptyState("练习完成") { ChallengeSigilMedallion(kind, 86.dp, completed = true) }
                        2 -> {
                            EmptyState("本次练习未通过", "可以根据反馈再练一次。") { ChallengeSigilMedallion(kind, 70.dp) }
                            Action(if (status.busy) "正在准备" else "再练一次", Glyph.RESTART, { model.startChallenge(kind) },
                                Modifier.fillMaxWidth(), primary = true, enabled = !status.busy)
                        }
                        else -> ChallengeMessage(
                            if (kind == TreeChallenge.DIAGNOSTIC) {
                                "第 ${(current?.exchanges?.size ?: 0) + 1} / $DIAGNOSTIC_QUESTIONS 题"
                            } else "复述",
                            shown.prompt.orEmpty(),
                        )
                    }
                }
            }
        }
        if (current != null && !current.finished) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                if (status.busy) Text("正在生成反馈", Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.labelMedium, color = Muted)
                AiComposer(input, { model.updateChallengeDraft(kind, it) },
                    if (kind == TreeChallenge.FEYNMAN) "输入你的复述" else "输入你的回答",
                    enabled = !status.busy, errorText = status.error, sendEnabled = input.isNotBlank() && !status.busy,
                    onSend = { keyboard?.hide(); model.submitChallenge(kind) },
                )
            }
        } else AssessmentStatusText(status)
    }
}

@Composable
private fun ChallengeMessage(label: String, text: String, user: Boolean = false) {
    Surface(shape = PanelShape, color = if (user) Raised else Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = Gold)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
