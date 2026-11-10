package com.lifeskill.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
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
    var showPrerequisites by remember(initial) { mutableStateOf(false) }
    var showIcons by remember(initial) { mutableStateOf(false) }
    val nameTooLong = name.trim().length > LibraryRules.NAME_LIMIT
    val goalTooLong = goal.trim().length > LibraryRules.GOAL_LIMIT
    val invalidShortName = skill && runCatching { LibraryRules.shortName(shortName) }.isFailure
    val activeIcon = editor.icon ?: EntryIcon.default(initial.kind, initial.id)
    val canSave = !editor.saving && !editor.suggesting && name.isNotBlank() && !nameTooLong && !invalidShortName &&
        (!skill || goal.isNotBlank() && !goalTooLong)
    Dialog(onDismissRequest = app::closeEditor) {
        val focus = LocalFocusManager.current
        Surface(Modifier.fillMaxWidth(), shape = DialogShape, color = Panel) {
            Column {
                Text(
                    (if (initial.id == null) "新增" else "编辑") + initial.kind.label,
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge,
                )
                // 只滚动表单内容，键盘弹出和选择区展开时，底部操作仍可见。
                Column(
                    Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                        .animateContentSize(tween(180))
                        .testTag("entry-form").padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EditorField(
                        name, { name = it }, "名称", !editor.saving,
                        error = if (nameTooLong) "名称最多 ${LibraryRules.NAME_LIMIT} 个字符" else null,
                        imeAction = if (skill) ImeAction.Next else ImeAction.Done,
                    )
                    if (skill) {
                        EditorField(
                            goal, { goal = it }, "技能说明", !editor.saving, minLines = 2, maxLines = 5,
                            error = if (goalTooLong) "技能说明最多 ${LibraryRules.GOAL_LIMIT} 个字符" else null,
                        )
                        EditorField(
                            shortName, { shortName = it }, "节点简称（选填）", !editor.saving,
                            placeholder = "2–5 个字", error = if (invalidShortName) "请输入 2–5 个字" else null,
                            imeAction = ImeAction.Done,
                        )
                        if (siblings.isNotEmpty()) {
                            EditorPickerRow(
                                title = "前置节点", value = if (selected.isEmpty()) "未选择" else "已选 ${selected.size}",
                                expanded = showPrerequisites, enabled = !editor.saving,
                                onClick = {
                                    focus.clearFocus()
                                    showPrerequisites = !showPrerequisites
                                },
                            )
                        }
                        if (showPrerequisites) {
                            val choices = remember(library, initial, selected) {
                                library.prerequisiteChoices(initial.parentId, initial.id, selected)
                            }
                            siblings.forEach { entry ->
                                val checked = entry.id in selected
                                val canSelect = entry.id in choices
                                Row(
                                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(ControlShape)
                                        .background(if (checked) GoldGround else Ground)
                                        .toggleable(checked, enabled = canSelect && !editor.saving, role = Role.Checkbox) {
                                            selected = if (it) selected + entry.id else selected - entry.id
                                        }.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Checkbox(checked, onCheckedChange = null, enabled = canSelect && !editor.saving)
                                    Text(
                                        entry.name, Modifier.weight(1f).padding(start = 10.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    if (!canSelect) {
                                        val reason = if (entry.id !in initial.prerequisites &&
                                            library.remainingBranches(entry.id) == 0
                                        ) "分支已满" else "不可选"
                                        Text(reason, color = Muted, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                        }
                    }
                    EditorPickerRow(
                        title = "选择图标", value = activeIcon.label, expanded = showIcons,
                        enabled = !editor.saving && !editor.suggesting,
                        onClick = {
                            focus.clearFocus()
                            showIcons = !showIcons
                        },
                        leading = { Emblem(activeIcon.glyph, diameter = 32.dp) },
                    )
                    if (showIcons) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SelectionChip(
                                label = "默认",
                                selected = editor.icon == null, onClick = {
                                    app.chooseIcon(null)
                                    showIcons = false
                                },
                                enabled = !editor.saving && !editor.suggesting,
                            )
                            EntryIcon.entries.forEach { icon ->
                                SelectionChip(
                                    label = icon.label,
                                    selected = editor.icon == icon, onClick = {
                                        app.chooseIcon(icon)
                                        showIcons = false
                                    },
                                    enabled = !editor.saving && !editor.suggesting,
                                    leadingIcon = { Mark(icon.glyph, if (editor.icon == icon) Gold else Muted, Modifier.size(16.dp)) },
                                )
                            }
                        }
                    }
                    AiAction(
                        label = if (editor.suggesting) "正在推荐…" else "AI 推荐图标",
                        onClick = {
                            focus.clearFocus()
                            app.suggestIcon(name, goal)
                        },
                        modifier = Modifier.align(Alignment.End),
                        enabled = name.isNotBlank() && !nameTooLong && !editor.saving && !editor.suggesting,
                    )
                    editor.iconError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
                HorizontalDivider(color = Line.copy(alpha = .55f))
                editor.error?.let {
                    Text(
                        it, Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = app::closeEditor, enabled = !editor.saving) { Text("取消") }
                    Button(
                        onClick = {
                            focus.clearFocus()
                            app.saveEntry(initial.copy(
                                name = name, goal = goal, prerequisites = selected, icon = editor.icon, shortName = shortName,
                            ))
                        },
                        modifier = Modifier.heightIn(min = 48.dp), enabled = canSave, shape = ControlShape,
                    ) { Text(if (editor.saving) "正在保存" else "保存") }
                }
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String, onChange: (String) -> Unit, label: String, enabled: Boolean,
    minLines: Int = 1, maxLines: Int = 1, placeholder: String? = null, error: String? = null,
    imeAction: ImeAction = ImeAction.Default,
) {
    OutlinedTextField(
        value, onChange, Modifier.fillMaxWidth(), label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        singleLine = maxLines == 1, minLines = minLines, maxLines = maxLines, enabled = enabled,
        isError = error != null, supportingText = error?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(imeAction = imeAction), shape = ControlShape,
        colors = fieldColors(),
    )
}

@Composable
private fun EditorPickerRow(
    title: String, value: String, expanded: Boolean, enabled: Boolean, onClick: () -> Unit,
    leading: (@Composable () -> Unit)? = null,
) {
    val angle by animateFloatAsState(if (expanded) 90f else 0f, tween(180), label = "表单展开")
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { role = Role.Button },
        shape = ControlShape, color = Raised,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            Text(value, color = Muted, style = MaterialTheme.typography.labelMedium)
            Mark(Glyph.NEXT, if (enabled) Gold else Line, Modifier.rotate(angle))
        }
    }
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
        shape = DialogShape, containerColor = Panel,
        title = { Text("删除${entry.kind.label}") },
        text = { Text("删除「${entry.name}」将一并移除$affected。此操作无法撤销。") },
        confirmButton = {
            Button(
                onClick = app::deleteEntry, enabled = !busy, shape = ControlShape,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError),
            ) {
                Text(if (busy) "正在删除" else "删除")
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
        shape = DialogShape, containerColor = Panel,
        title = { Text("重新学习") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("开始后将清空此技能的旧对话，且无法恢复。点亮进度会保留。")
                OutlinedTextField(
                    requirements, { requirements = it }, Modifier.fillMaxWidth().testTag("restart-requirements"),
                    label = { Text("本次学习要求（选填）") },
                    placeholder = { Text("例如：按零基础讲，用一个实例演示") },
                    maxLines = 4, isError = exceedsLimit, shape = ControlShape, colors = fieldColors(),
                    supportingText = if (exceedsLimit) {
                        { Text("最多 ${ConversationRepository.INPUT_LIMIT} 个字符") }
                    } else null,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onStart(requirements) }, enabled = !exceedsLimit, shape = ControlShape) { Text("开始新一轮") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
