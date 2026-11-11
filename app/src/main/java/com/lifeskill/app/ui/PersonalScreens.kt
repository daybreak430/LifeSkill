package com.lifeskill.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeskill.app.model.CollectedCertificate
import com.lifeskill.app.model.LearningStatistics
import com.lifeskill.app.model.PersonalProfile

@Composable
internal fun PersonalScreen(app: AppViewModel, model: PersonalViewModel, statistics: LearningStatistics, active: Boolean) {
    val result by model.personalData.collectAsStateWithLifecycle()
    val data = result?.getOrNull()
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我的", Modifier.weight(1f).semantics { heading() }, style = LibraryTitle)
            GlyphButton(Glyph.SETTINGS, "设置", { app.open(Route(Page.SETTINGS)) })
        }
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f).testTag("personal-home"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (data == null) {
                item {
                    if (result == null) Loading() else EmptyState("无法读取个人资料和证书") { Emblem(Glyph.PERSON) }
                }
            } else {
                item { PersonalProfileCard(data.profile) { app.open(Route(Page.PROFILE_EDIT)) } }
                item { StudyStatisticsCard(statistics, data.certificates.size) }
                item {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("证书收藏夹", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        TextButton({ app.open(Route(Page.CERTIFICATES)) }) { Text("查看全部") }
                    }
                }
                if (data.certificates.isEmpty()) item { EmptyCertificates() }
                items(data.certificates.take(3), key = { it.treeId }) { certificate ->
                    CertificateListItem(certificate) { selectedId = certificate.treeId }
                }
            }
        }
    }
    if (active) data?.certificates?.firstOrNull { it.treeId == selectedId }?.let {
        IssuedCertificateDialog(it) { selectedId = null }
    }
}

@Composable
internal fun CertificateCollectionScreen(app: AppViewModel, model: PersonalViewModel, active: Boolean) {
    val result by model.personalData.collectAsStateWithLifecycle()
    val certificates = result?.getOrNull()?.certificates
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        PageHeader("证书收藏夹", app::back, Modifier.widthIn(max = 640.dp))
        LazyColumn(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f).testTag("certificate-collection"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when {
                result == null -> item { Loading() }
                certificates == null -> item { EmptyState("无法读取证书") { Emblem(Glyph.DIAMOND) } }
                certificates.isEmpty() -> item { EmptyCertificates() }
                else -> items(certificates, key = { it.treeId }) { certificate ->
                    CertificateListItem(certificate) { selectedId = certificate.treeId }
                }
            }
        }
    }
    if (active) certificates?.firstOrNull { it.treeId == selectedId }?.let {
        IssuedCertificateDialog(it) { selectedId = null }
    }
}

@Composable
private fun PersonalProfileCard(profile: PersonalProfile, edit: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = PanelShape, color = Panel) {
        Row(
            Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProfileAvatarImage(profile.avatar, 58.dp)
            Text(profile.displayName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            GlyphButton(Glyph.EDIT, "编辑资料", edit, background = Raised)
        }
    }
}

@Composable
private fun StudyStatisticsCard(statistics: LearningStatistics, certificateCount: Int) {
    Surface(Modifier.fillMaxWidth(), shape = PanelShape, color = Panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("学习统计", Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StudyMetric("${statistics.skills.lit} / ${statistics.skills.total}", "已掌握技能", Modifier.weight(1f))
                StudyMetric(statistics.passedTrees.toString(), "通过验收", Modifier.weight(1f))
                StudyMetric(certificateCount.toString(), "收藏证书", Modifier.weight(1f))
            }
            statistics.directions.forEach { direction ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(direction.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("${direction.progress.lit} / ${direction.progress.total}", color = Muted,
                            style = MaterialTheme.typography.labelMedium)
                    }
                    LinearProgressIndicator(progress = { direction.progress.fraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp), color = Gold, trackColor = Raised,
                        drawStopIndicator = {})
                }
            }
        }
    }
}

@Composable
private fun StudyMetric(value: String, label: String, modifier: Modifier) {
    Surface(modifier, shape = ControlShape, color = Raised) {
        Column(Modifier.padding(horizontal = 9.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = Gold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = Muted)
        }
    }
}

@Composable
private fun EmptyCertificates() {
    Surface(Modifier.fillMaxWidth(), shape = PanelShape, color = Panel) {
        EmptyState("暂无结业证书", "通过验收并收下证书后，会在这里显示。") { Emblem(Glyph.DIAMOND) }
    }
}

@Composable
private fun CertificateListItem(certificate: CollectedCertificate, open: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = ControlShape, color = Panel) {
        Row(
            Modifier.clickable(role = Role.Button, onClickLabel = "查看结业证书", onClick = open)
                .testTag("collected-certificate-${certificate.treeId}").padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            CertificateThumbnail(certificate.style)
            Text(certificate.treeName, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Mark(Glyph.NEXT, Muted)
        }
    }
}
