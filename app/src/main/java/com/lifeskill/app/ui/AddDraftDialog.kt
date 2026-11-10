package com.lifeskill.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.GenerationDestination
import com.lifeskill.app.model.GenerationPreview
import com.lifeskill.app.model.LibraryRules

@Composable
internal fun AddDraftDialog(
    preview: GenerationPreview,
    directions: List<Entry>,
    working: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onCreateDirection: () -> Unit,
    onSelectionChange: () -> Unit,
    onConfirm: (GenerationDestination) -> Unit,
) {
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var createNew by rememberSaveable { mutableStateOf(false) }
    val existing = if (preview.treeNames.isEmpty()) emptyList() else directions
    val newName = preview.suggestedDirectionName?.let { suggestion ->
        runCatching {
            LibraryRules.name(suggestion).also { LibraryRules.checkUnique(it, directions.map(Entry::name)) }
        }.getOrNull()
    }
    val destination = if (createNew) newName?.let { GenerationDestination.New(it) }
    else existing.firstOrNull { it.id == selectedId }?.let { GenerationDestination.Existing(it.id) }

    AlertDialog(
        onDismissRequest = { if (!working) onDismiss() },
        containerColor = Panel,
        shape = RoundedCornerShape(20.dp),
        title = { Text("选择学习方向") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(Modifier.selectableGroup()) {
                    existing.forEach { direction ->
                        DirectionChoice(direction.name, !createNew && selectedId == direction.id, working,
                            recommended = preview.suggestedDirectionId == direction.id,
                        ) {
                            selectedId = direction.id
                            createNew = false
                            onSelectionChange()
                        }
                    }
                    if (newName != null) {
                        DirectionChoice("新建 · $newName", createNew, working, recommended = false) {
                            createNew = true
                            onSelectionChange()
                        }
                    }
                }
                if (existing.isEmpty() && newName == null) {
                    Text(if (directions.isEmpty()) "还没有学习方向" else "这份草稿没有可添加的方向")
                    TextButton(onClick = onCreateDirection, enabled = !working) { Text("新增方向") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = { destination?.let(onConfirm) }, enabled = !working && destination != null,
                shape = RoundedCornerShape(12.dp),
            ) { Text(if (working) "正在添加" else "确认添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !working) { Text("取消") } },
    )
}

@Composable
private fun DirectionChoice(
    name: String, selected: Boolean, working: Boolean, recommended: Boolean, onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .selectable(selected, enabled = !working, role = Role.RadioButton, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected, onClick = null, enabled = !working)
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        if (recommended) Text("推荐", color = Gold, style = MaterialTheme.typography.labelMedium)
    }
}
