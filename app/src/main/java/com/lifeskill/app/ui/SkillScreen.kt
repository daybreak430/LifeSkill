package com.lifeskill.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.Mastery

@Composable
internal fun SkillScreen(skill: Entry, library: Library, app: AppViewModel, tutor: ChatViewModel) {
    val chatStatus by tutor.status.collectAsStateWithLifecycle()
    val halo = if (skill.lit) Gold else Steel
    var showRestart by rememberSaveable { mutableStateOf(false) }
    val prerequisites = library.prerequisitesOf(skill.id).mapNotNull(library.byId::get)
    val mastery = when (skill.mastery) {
        Mastery.UNLIT -> "待学习"
        Mastery.MANUAL -> "已点亮 · 手动"
        Mastery.AI -> "已点亮 · AI 检验通过"
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                library.byId[skill.parentId]?.name.orEmpty(), Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge, color = Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            EntryMenu(skill, app)
            GlyphButton(Glyph.CLOSE, "关闭技能详情", app::back)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.matchParentSize()) {
                        val radius = 82.dp.toPx()
                        drawCircle(Brush.radialGradient(listOf(halo.copy(alpha = .22f), Color.Transparent), center, radius), radius)
                        drawCircle(halo.copy(alpha = .22f), radius * .83f, style = Stroke(1.dp.toPx()))
                        drawCircle(halo.copy(alpha = .10f), radius * .97f, style = Stroke(1.dp.toPx()))
                    }
                    SkillMedallion(skill.displayIcon, skill.lit, 106.dp)
                }
                Text(skill.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                Text(
                    mastery, Modifier.padding(top = 7.dp), color = if (skill.lit) Gold else Muted,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp), color = Panel, border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("技能说明", style = MaterialTheme.typography.titleMedium, color = Gold)
                    LessonMarkdown(skill.goal)
                }
            }
            if (prerequisites.isNotEmpty()) {
                Text("建议先学", style = MaterialTheme.typography.titleMedium)
                prerequisites.forEach { entry ->
                    EntryTile(entry, if (entry.lit) "已点亮" else "待学习", { app.open(Route.entry(entry)) })
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Action(
                "学习技能", Glyph.SPARK, { app.open(Route(Page.TUTOR, skill.id)) },
                Modifier.fillMaxWidth(), primary = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { app.setLit(skill, !skill.lit) }) {
                    Text(if (skill.lit) "取消点亮" else "手动点亮", color = Muted)
                }
                TextButton(onClick = { showRestart = true }, enabled = !chatStatus.working) {
                    Text("重新学习")
                }
            }
        }
    }
    if (showRestart) {
        RestartLessonDialog(
            onDismiss = { showRestart = false },
            onStart = { requirements ->
                showRestart = false
                tutor.restartLesson(requirements)
                app.open(Route(Page.TUTOR, skill.id))
            },
        )
    }
}
