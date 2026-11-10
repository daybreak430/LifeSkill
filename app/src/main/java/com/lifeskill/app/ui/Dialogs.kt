package com.lifeskill.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.LibraryRules
import com.lifeskill.app.data.ConversationRepository

@Composable
internal fun EditorDialog(editor: Editor, library: Library, app: AppViewModel) {
    val initial = editor.initial
    val skill = initial.kind == EntryKind.SKILL
    val siblings = if (skill) library.childrenOf(initial.parentId).filterNot { it.id == initial.id } else emptyList()
    var name by remember(initial) { mutableStateOf(initial.name) }
    var goal by remember(initial) { mutableStateOf(initial.goal) }
    var shortName by remember(initial) { mutableStateOf(initial.shortName) }
    var selected by remember(initial) { mutableStateOf(initial.prerequisites) }
    var showPrerequisites by remember(initial) { mutableStateOf(initial.prerequisites.isNotEmpty()) }
    var showIcons by remember(initial) { mutableStateOf(false) }
    val nameTooLong = name.trim().length > LibraryRules.NAME_LIMIT
    val goalTooLong = goal.trim().length > LibraryRules.GOAL_LIMIT
    val invalidShortName = skill && runCatching { LibraryRules.shortName(shortName) }.isFailure
    val activeIcon = editor.icon ?: EntryIcon.default(initial.kind, initial.id)

    AlertDialog(
        onDismissRequest = app::closeEditor,
        title = { Text((if (initial.id == null) "新增" else "编辑") + initial.kind.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(
                    name, { name = it }, Modifier.fillMaxWidth(),
                    label = { Text("名称") }, singleLine = true, enabled = !editor.saving,
                    isError = nameTooLong,
                    supportingText = if (nameTooLong) { { Text("名称最多 ${LibraryRules.NAME_LIMIT} 个字符") } } else null,
                )
                if (skill) {
                    OutlinedTextField(
                        goal, { goal = it }, Modifier.fillMaxWidth(),
                        label = { Text("技能说明") }, minLines = 2, maxLines = 5, enabled = !editor.saving,
                        isError = goalTooLong,
                        supportingText = if (goalTooLong) { { Text("技能说明最多 ${LibraryRules.GOAL_LIMIT} 个字符") } } else null,
                    )
                    OutlinedTextField(
                        shortName, { shortName = it }, Modifier.fillMaxWidth(),
                        label = { Text("节点简称（选填）") }, placeholder = { Text("2–5 个字") },
                        singleLine = true, enabled = !editor.saving, isError = invalidShortName,
                        supportingText = if (invalidShortName) { { Text("请输入 2–5 个字") } } else null,
                    )
                    if (siblings.isNotEmpty()) {
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                                showPrerequisites = !showPrerequisites
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("前置节点", Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                            Text(
                                if (selected.isEmpty()) "选择" else "已选 ${selected.size}",
                                style = MaterialTheme.typography.labelMedium, color = Gold,
                            )
                            Mark(Glyph.NEXT, Gold, Modifier.padding(start = 6.dp))
                        }
                    }
                    if (showPrerequisites) {
                        siblings.forEach { entry ->
                            val checked = entry.id in selected
                            val canSelect = entry.id in initial.prerequisites || library.remainingBranches(entry.id) > 0
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .toggleable(checked, enabled = canSelect && !editor.saving, role = Role.Checkbox) {
                                        selected = if (it) selected + entry.id else selected - entry.id
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked, onCheckedChange = null, enabled = canSelect && !editor.saving)
                                Text(
                                    entry.name, Modifier.weight(1f).padding(start = 10.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                if (!canSelect) {
                                    Text("分支已满", color = Muted, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Mark(activeIcon.glyph, Gold)
                    Text(
                        activeIcon.label, Modifier.weight(1f).padding(start = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    TextButton(
                        onClick = { showIcons = !showIcons },
                        enabled = !editor.saving && !editor.suggesting,
                    ) { Text(if (showIcons) "收起图标" else "选择图标") }
                }
                if (showIcons) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = editor.icon == null, onClick = {
                                app.chooseIcon(null)
                                showIcons = false
                            },
                            enabled = !editor.saving && !editor.suggesting, label = { Text("默认") },
                        )
                        EntryIcon.entries.forEach { icon ->
                            FilterChip(
                                selected = editor.icon == icon, onClick = {
                                    app.chooseIcon(icon)
                                    showIcons = false
                                },
                                enabled = !editor.saving && !editor.suggesting, label = { Text(icon.label) },
                                leadingIcon = { Mark(icon.glyph, if (editor.icon == icon) Gold else Muted) },
                            )
                        }
                    }
                }
                TextButton(
                    onClick = { app.suggestIcon(name, goal) },
                    enabled = name.isNotBlank() && !nameTooLong && !editor.saving && !editor.suggesting,
                ) { Text(if (editor.suggesting) "正在推荐…" else "AI 推荐图标") }
                editor.iconError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { app.saveEntry(initial.copy(
                    name = name, goal = goal, prerequisites = selected, icon = editor.icon, shortName = shortName,
                )) },
                enabled = !editor.saving && !editor.suggesting && name.isNotBlank() && !nameTooLong && !invalidShortName &&
                    (!skill || goal.isNotBlank() && !goalTooLong),
            ) { Text(if (editor.saving) "正在保存" else "保存") }
        },
        dismissButton = { TextButton(onClick = app::closeEditor, enabled = !editor.saving) { Text("取消") } },
    )
}

@Composable
internal fun DeletionDialog(entry: Entry, library: Library, busy: Boolean, app: AppViewModel) {
    val affected = when (entry.kind) {
        EntryKind.DIRECTION -> "该方向下的 ${library.childrenOf(entry.id).size} 棵技能树、${library.progress(entry.id).total} 个技能点及相关对话"
        EntryKind.TREE -> "树内的 ${library.progress(entry.id).total} 个技能点及相关对话"
        EntryKind.SKILL -> "该技能点的连线、点亮记录和学习对话"
    }
    AlertDialog(
        onDismissRequest = app::cancelDelete,
        title = { Text("删除${entry.kind.label}") },
        text = { Text("删除「${entry.name}」将一并移除$affected。此操作无法撤销。") },
        confirmButton = {
            TextButton(onClick = app::deleteEntry, enabled = !busy) {
                Text(if (busy) "正在删除" else "删除", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = app::cancelDelete, enabled = !busy) { Text("取消") } },
    )
}

@Composable
internal fun RestartLessonDialog(onDismiss: () -> Unit, onStart: (String) -> Unit) {
    var requirements by remember { mutableStateOf("") }
    val exceedsLimit = requirements.trim().length > ConversationRepository.INPUT_LIMIT
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重新学习") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("开始后将清空此技能的旧对话，且无法恢复。点亮进度会保留。")
                OutlinedTextField(
                    requirements, { requirements = it }, Modifier.fillMaxWidth().testTag("restart-requirements"),
                    label = { Text("本次学习要求（选填）") },
                    placeholder = { Text("例如：按零基础讲，用一个实例演示") },
                    maxLines = 4, isError = exceedsLimit,
                    supportingText = if (exceedsLimit) {
                        { Text("最多 ${ConversationRepository.INPUT_LIMIT} 个字符") }
                    } else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onStart(requirements) }, enabled = !exceedsLimit) { Text("开始新一轮") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
