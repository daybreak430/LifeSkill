package com.lifeskill.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.LibraryRules

@Composable
internal fun EditorDialog(editor: Editor, library: Library, app: AppViewModel) {
    val initial = editor.initial
    val skill = initial.kind == EntryKind.SKILL
    val siblings = if (skill) library.childrenOf(initial.parentId).filterNot { it.id == initial.id } else emptyList()
    var name by remember(initial) { mutableStateOf(initial.name) }
    var goal by remember(initial) { mutableStateOf(initial.goal) }
    var selected by remember(initial) { mutableStateOf(initial.prerequisites) }
    var showPrerequisites by remember(initial) { mutableStateOf(initial.prerequisites.isNotEmpty()) }
    val nameTooLong = name.trim().length > LibraryRules.NAME_LIMIT
    val goalTooLong = goal.trim().length > LibraryRules.GOAL_LIMIT

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
                        label = { Text("学习目标") }, minLines = 2, maxLines = 5, enabled = !editor.saving,
                        isError = goalTooLong,
                        supportingText = if (goalTooLong) { { Text("学习目标最多 ${LibraryRules.GOAL_LIMIT} 个字符") } } else null,
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
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                    .toggleable(checked, enabled = !editor.saving, role = Role.Checkbox) {
                                        selected = if (it) selected + entry.id else selected - entry.id
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked, onCheckedChange = null, enabled = !editor.saving)
                                Text(entry.name, Modifier.padding(start = 10.dp), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                editor.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { app.saveEntry(initial.copy(name = name, goal = goal, prerequisites = selected)) },
                enabled = !editor.saving && name.isNotBlank() && !nameTooLong &&
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
