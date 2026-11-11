package com.lifeskill.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeskill.app.model.CertificateStyle
import com.lifeskill.app.model.EXAM_MAX_SCORE
import com.lifeskill.app.model.EXAM_PASS_SCORE
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.ExamAnswer
import com.lifeskill.app.model.ExamAttempt
import com.lifeskill.app.model.ExamQuestion
import com.lifeskill.app.model.ExamQuestionType
import com.lifeskill.app.model.SkillState

@Composable
internal fun ExamScreen(
    tree: Entry, ready: Boolean, app: AppViewModel, model: TreeAssessmentViewModel, active: Boolean,
) {
    val attempts by model.attempts.collectAsStateWithLifecycle()
    val status by model.examStatus.collectAsStateWithLifecycle()
    val draft by model.examDraft.collectAsStateWithLifecycle()
    val certificate by model.certificate.collectAsStateWithLifecycle()
    val latest = attempts?.firstOrNull()
    val best = attempts.orEmpty().mapNotNull(ExamAttempt::score).maxOrNull()
    var reviewing by rememberSaveable { mutableStateOf(false) }
    val close = { if (reviewing) reviewing = false else app.back() }

    BackHandler(enabled = active && reviewing) { reviewing = false }
    Column(Modifier.fillMaxSize().imePadding()) {
        PageHeader(if (reviewing) "错题集" else "验收考试", close, subtitle = tree.name) {
            if (!reviewing && latest != null && latest.score == null && best != null) {
                GlyphButton(Glyph.BOOK, "查看错题集", { reviewing = true })
            }
        }
        when {
            reviewing -> ExamReview(attempts.orEmpty(), Modifier.weight(1f))
            latest?.score != null -> key(latest.id) {
                ExamResult(
                    tree.name, checkNotNull(latest.score), CertificateStyle.forTree(tree), certificate,
                    checkNotNull(best), ready, status, active, model::startExam, { reviewing = true },
                    { nickname -> model.collectCertificate(latest.id, nickname) },
                )
            }
            latest != null -> key(latest.id) {
                val answers = draft?.takeIf { it.attemptId == latest.id }?.answers ?: latest.answers
                ExamPaper(latest, answers, status, { index, answer -> model.updateAnswer(latest, index, answer) },
                    { model.submitExam(latest) })
            }
            attempts == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (status.error == null) Loading() else AssessmentStatusText(status)
            }
            else -> ExamIntro(ready, status, model::startExam)
        }
    }
}

@Composable
private fun ExamIntro(ready: Boolean, status: AssessmentStatus, start: () -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            EmptyState(
                if (ready) "准备验收" else "验收考试未解锁",
                if (ready) "10 道题 · 满分 $EXAM_MAX_SCORE · $EXAM_PASS_SCORE 分通过" else "点亮全部课程后开始考试。",
            ) {
                AssessmentMedallion(EntryIcon.BOOK, 76.dp, if (ready) SkillState.AVAILABLE else SkillState.LOCKED)
            }
        }
        if (ready) item {
            Action(if (status.busy) "正在出题" else "开始考试", Glyph.BOOK, start, Modifier.fillMaxWidth(),
                primary = true, enabled = !status.busy)
        }
        item { AssessmentStatusText(status) }
    }
}

private enum class CertificateMode { COLLECT, VIEW }

@Composable
private fun ExamResult(
    treeName: String, score: Int, style: CertificateStyle, certificate: ExamCertificateContext?, best: Int, ready: Boolean,
    status: AssessmentStatus, active: Boolean,
    retake: () -> Unit, review: () -> Unit, collectCertificate: (String?) -> Unit,
) {
    val passed = score >= EXAM_PASS_SCORE
    val issued = certificate?.issued
    var certificateMode by rememberSaveable { mutableStateOf<CertificateMode?>(null) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Surface(shape = PanelShape, color = Panel, modifier = Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AssessmentMedallion(EntryIcon.BOOK, 96.dp, score = score)
                    Text("$score 分", style = MaterialTheme.typography.headlineLarge)
                    Text(when (score) {
                        EXAM_MAX_SCORE -> "满分"
                        in EXAM_PASS_SCORE until EXAM_MAX_SCORE -> "通过验收"
                        else -> "未通过"
                    }, style = MaterialTheme.typography.titleMedium)
                    if (score != best) Text("最高 $best 分", color = Muted, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (passed && certificate != null) item {
            Action(
                if (issued == null) "获取结业证书" else "查看结业证书", Glyph.DIAMOND,
                { certificateMode = if (issued == null) CertificateMode.COLLECT else CertificateMode.VIEW },
                Modifier.fillMaxWidth(), primary = true, enabled = !status.busy,
            )
        }
        item { Action("查看错题集", Glyph.BOOK, review, Modifier.fillMaxWidth()) }
        if (best < EXAM_MAX_SCORE) item {
            Action(if (status.busy) "正在出题" else "再考一次", Glyph.RESTART, retake, Modifier.fillMaxWidth(),
                primary = !passed, enabled = ready && !status.busy)
            if (!ready) Text("点亮全部课程后可再考。", color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        item { AssessmentStatusText(status) }
    }
    if (active && certificate != null) when (certificateMode) {
        CertificateMode.COLLECT -> ExamCertificateDialog(
            issued?.treeName ?: treeName, issued?.style ?: style, issued?.recipient ?: certificate.nickname,
            collected = issued != null, status = status, onCollect = collectCertificate,
        ) { certificateMode = null }
        CertificateMode.VIEW -> issued?.let { IssuedCertificateDialog(it) { certificateMode = null } }
        null -> Unit
    }
}

@Composable
private fun ExamPaper(
    attempt: ExamAttempt, answers: List<ExamAnswer>, status: AssessmentStatus,
    update: (Int, ExamAnswer) -> Unit, submit: () -> Unit,
) {
    val completed = answers.count { it.selected.isNotEmpty() || it.text.isNotBlank() }
    val keyboard = LocalSoftwareKeyboardController.current
    var confirm by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().testTag("exam-questions"), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(attempt.questions) { index, question ->
                ExamQuestionCard(index, question, answers[index], !status.busy) { update(index, it) }
            }
        }
        Surface(color = Panel, shadowElevation = 3.dp) {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AssessmentStatusText(status)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("已答 $completed / ${answers.size}", Modifier.weight(1f), color = Muted,
                        style = MaterialTheme.typography.labelLarge)
                    Action(if (status.busy) "正在判卷" else "交卷", Glyph.CHECK, {
                        keyboard?.hide()
                        confirm = true
                    }, primary = true, enabled = !status.busy)
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("确认交卷？") },
        text = {
            Text(if (completed == answers.size) "交卷后，本次答案无法修改。"
                else "还有 ${answers.size - completed} 题未作答，空白题计 0 分。交卷后，本次答案无法修改。")
        },
        confirmButton = { TextButton(onClick = { confirm = false; submit() }) { Text("确认交卷") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("继续答题") } },
    )
}

@Composable
private fun ExamQuestionCard(
    index: Int, question: ExamQuestion, answer: ExamAnswer, enabled: Boolean, update: (ExamAnswer) -> Unit,
) {
    Surface(shape = PanelShape, color = Panel, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${index + 1} · ${question.type.label} · ${question.type.points} 分",
                color = Gold, style = MaterialTheme.typography.labelLarge)
            Text(question.prompt, style = MaterialTheme.typography.titleSmall)
            if (question.choices.isEmpty()) {
                val legacyShort = question.type == ExamQuestionType.SHORT
                OutlinedTextField(
                    answer.text, { update(ExamAnswer(text = it)) }, Modifier.fillMaxWidth().testTag("exam-answer-$index"),
                    label = { Text("第 ${index + 1} 题答案") }, enabled = enabled,
                    minLines = if (legacyShort) 3 else 1, maxLines = if (legacyShort) 6 else 2,
                    shape = ControlShape,
                )
            } else {
                val single = question.type == ExamQuestionType.SINGLE
                Column(if (single) Modifier.selectableGroup() else Modifier) {
                    question.choices.forEachIndexed { choiceIndex, choice ->
                        val number = choiceIndex + 1
                        val selected = number in answer.selected
                        val onChange = {
                            val selection = if (single) setOf(number)
                            else if (selected) answer.selected - number else answer.selected + number
                            update(ExamAnswer(selected = selection))
                        }
                        val interaction = if (single) Modifier.selectable(selected, enabled, Role.RadioButton, onClick = onChange)
                        else Modifier.toggleable(selected, enabled, Role.Checkbox) { onChange() }
                        Row(
                            interaction.fillMaxWidth().heightIn(min = 48.dp).testTag("exam-choice-$index-$choiceIndex"),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            if (single) RadioButton(selected, onClick = null, enabled = enabled)
                            else Checkbox(selected, onCheckedChange = null, enabled = enabled)
                            Text("${'A' + choiceIndex}. $choice", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExamReview(attempts: List<ExamAttempt>, modifier: Modifier) {
    val graded = attempts.filter { it.score != null }
    val mistakes = graded.flatMapIndexed { index, attempt ->
        attempt.questions.indices.filter { attempt.marks[it].points < attempt.questions[it].type.points }
            .map { Triple(graded.size - index, attempt, it) }
    }
    LazyColumn(modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (mistakes.isEmpty()) item {
            EmptyState("暂无错题") { Emblem(Glyph.CHECK) }
        }
        itemsIndexed(mistakes, key = { _, (_, attempt, index) -> "${attempt.id}:$index" }) { _, (round, attempt, index) ->
            val question = attempt.questions[index]
            val answer = attempt.answers[index]
            val mark = attempt.marks[index]
            val selected = answer.selected.sorted().joinToString("、") { question.choices[it - 1] }
            val referenceChoices = question.correctChoices.sorted().joinToString("、") { question.choices[it - 1] }
            Surface(shape = PanelShape, color = Panel, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("第 $round 次 · 第 ${index + 1} 题 · ${mark.points}/${question.type.points} 分",
                        color = Gold, style = MaterialTheme.typography.labelLarge)
                    Text(question.prompt, style = MaterialTheme.typography.titleSmall)
                    ReviewText("你的答案", selected.ifBlank { answer.text.ifBlank { "未作答" } })
                    ReviewText("参考答案", listOf(referenceChoices, question.reference).filter { it.isNotBlank() }.joinToString("\n"))
                    ReviewText("判卷反馈", mark.feedback)
                }
            }
        }
    }
}

@Composable
private fun ReviewText(title: String, value: String) {
    Text(title, color = Muted, style = MaterialTheme.typography.labelMedium)
    Text(value, style = MaterialTheme.typography.bodyMedium)
}

@Composable
internal fun AssessmentStatusText(status: AssessmentStatus) {
    status.error?.let {
        Text(it, Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}
