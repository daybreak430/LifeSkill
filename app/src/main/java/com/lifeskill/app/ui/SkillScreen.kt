package com.lifeskill.app.ui

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.Mastery
import com.lifeskill.app.model.SkillState

@Composable
internal fun SkillScreen(skill: Entry, library: Library, app: AppViewModel) {
    val state = library.skillState(skill.id)
    val halo = state.tint()
    val prerequisites = library.prerequisitesOf(skill.id).mapNotNull(library.byId::get)
    val mastery = when {
        state == SkillState.LIT && skill.mastery == Mastery.AI -> "已完成 · 练习通过"
        else -> state.label
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                library.byId[skill.parentId]?.name.orEmpty(), Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge, color = Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            EntryMenu(skill, app)
            GlyphButton(Glyph.CLOSE, "关闭课时详情", app::back)
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(Modifier.fillMaxWidth(), shape = PanelShape, color = Panel) {
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.fillMaxWidth().height(134.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.matchParentSize()) {
                            val radius = 65.dp.toPx()
                            drawCircle(Brush.radialGradient(listOf(halo.copy(alpha = .18f), Color.Transparent), center, radius), radius)
                            drawCircle(halo.copy(alpha = .16f), radius * .83f, style = Stroke(1.dp.toPx()))
                        }
                        SkillMedallion(skill.displayIcon, state, 86.dp)
                    }
                    Text(skill.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Surface(Modifier.padding(top = 8.dp), shape = CircleShape, color = halo.copy(alpha = .10f)) {
                        Text(mastery, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = halo,
                            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
            Surface(
                shape = PanelShape, color = Panel,
                modifier = Modifier.fillMaxWidth().panelShadow(),
            ) {
                Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("学习目标", style = MaterialTheme.typography.titleMedium)
                    LessonMarkdown(skill.goal)
                }
            }
            if (prerequisites.isNotEmpty()) {
                Text("先修课", Modifier.padding(start = 4.dp, top = 4.dp), style = MaterialTheme.typography.titleMedium)
                prerequisites.forEach { entry ->
                    EntryTile(entry, library.skillState(entry.id), { app.open(Route.entry(entry)) })
                }
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val restriction = when {
                state == SkillState.LOCKED && skill.lit -> "完成先修课后，将恢复本节的完成状态"
                state == SkillState.LOCKED -> "完成列出的先修课后即可学习本节"
                else -> null
            }
            restriction?.let {
                Text(
                    it, Modifier.padding(bottom = 6.dp), color = Muted,
                    style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
                )
            }
            Action(
                "开始学习", Glyph.SPARK, { app.open(Route(Page.TUTOR, skill.id)) },
                Modifier.fillMaxWidth(), primary = true, enabled = state != SkillState.LOCKED,
            )
        }
    }
}
