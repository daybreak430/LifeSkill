package com.lifeskill.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeskill.app.data.UsageSummary
import com.lifeskill.app.data.UsageTotal
import com.lifeskill.app.model.CollectedCertificate
import com.lifeskill.app.model.LearningStatistics
import com.lifeskill.app.model.PersonalProfile
import kotlin.math.roundToInt

@Composable
internal fun PersonalScreen(
    app: AppViewModel, model: PersonalViewModel, statistics: LearningStatistics, active: Boolean, dark: Boolean,
    usage: UsageSummary,
) {
    val result by model.personalData.collectAsStateWithLifecycle()
    val certificateStatus by model.certificateStatus.collectAsStateWithLifecycle()
    val data = result?.getOrNull()
    val certificates = data?.certificates.orEmpty()
    val (hidden, visible) = remember(certificates) { certificates.partition { it.hidden } }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var browsingHidden by rememberSaveable { mutableStateOf(false) }
    val selected = certificates.firstOrNull { it.treeId == selectedId }
    LaunchedEffect(data != null, selectedId, selected, hidden.isEmpty()) {
        if (data != null) {
            if (selected == null) selectedId = null
            if (hidden.isEmpty()) browsingHidden = false
        }
    }
    val columnWidth = 144.dp * LocalDensity.current.fontScale.coerceIn(1f, 1.6f)
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("我的", Modifier.weight(1f).semantics { heading() }, style = LibraryTitle)
            GlyphButton(Glyph.SETTINGS, "设置", { app.open(Route(Page.SETTINGS)) })
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(columnWidth),
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().weight(1f).testTag("personal-home"),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = HomeTabClearance),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (data == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    if (result == null) Loading() else EmptyState("无法读取个人资料和证书") { Emblem(Glyph.PERSON) }
                }
            } else {
                item(key = "personal-profile", span = { GridItemSpan(maxLineSpan) }) {
                    PersonalProfileCard(data.profile) { app.open(Route(Page.PROFILE_EDIT)) }
                }
                item(key = "study-statistics", span = { GridItemSpan(maxLineSpan) }) {
                    StudyStatisticsCard(statistics, certificates.count { !it.isSample }, usage.total)
                }
                item(key = "certificate-heading", span = { GridItemSpan(maxLineSpan) }) {
                    Row(Modifier.fillMaxWidth().padding(top = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("我的证书", Modifier.weight(1f).semantics { heading() }, style = MaterialTheme.typography.titleLarge)
                        if (hidden.isNotEmpty()) {
                            TextButton({ browsingHidden = true }) { Text("已隐藏（${hidden.size}）") }
                        }
                    }
                }
                if (visible.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Surface(Modifier.fillMaxWidth(), shape = PanelShape, color = Panel) {
                            if (hidden.isEmpty()) {
                                EmptyState("暂无结业证书", "通过结业考试并领取证书后，会在这里显示。") { Emblem(Glyph.DIAMOND) }
                            } else {
                                EmptyState("暂无展示的证书") { Emblem(Glyph.DIAMOND) }
                            }
                        }
                    }
                }
                items(visible, key = { it.treeId }) { certificate ->
                    CertificateDisplay(certificate, dark) {
                        browsingHidden = false
                        selectedId = certificate.treeId
                    }
                }
            }
        }
    }
    if (active) {
        if (selected != null) {
            val controls = CertificateViewerControls(
                certificateStatus, destroy = { model.destroyCertificate(selected.treeId) },
                toggleVisibility = { model.setCertificateHidden(selected.treeId, !selected.hidden) }, hidden = selected.hidden,
            )
            IssuedCertificateDialog(selected, controls, usage.courses[selected.treeId], animateViewer = true) {
                selectedId = null
                model.clearCertificateStatus()
            }
        } else if (browsingHidden && hidden.isNotEmpty()) {
            HiddenCertificatesDialog(hidden, { browsingHidden = false }) { selectedId = it }
        }
    }
}

@Composable
private fun HiddenCertificatesDialog(certificates: List<CollectedCertificate>, close: () -> Unit, open: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = close, shape = DialogShape, containerColor = Panel,
        title = { Text("已隐藏的证书") },
        text = {
            LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(certificates, key = { it.treeId }) { certificate ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(ControlShape).background(Raised)
                            .clickable(role = Role.Button, onClickLabel = "查看结业证书") { open(certificate.treeId) }
                            .testTag("hidden-certificate-${certificate.treeId}").padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(certificate.displayTitle, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Mark(Glyph.NEXT, Muted)
                    }
                }
            }
        },
        confirmButton = { TextButton(close) { Text("关闭") } },
    )
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

private val StatisticsWhite = Color(0xFFF5FCF5)
private val StatisticsMint = Color(0xFFA6CEBB)
private val StatisticsDivider = Color.White.copy(alpha = .15f)

@Composable
private fun StudyStatisticsCard(
    statistics: LearningStatistics, certificateCount: Int, usage: UsageTotal?,
) {
    val progress = statistics.skills
    Surface(Modifier.fillMaxWidth(), shape = PanelShape, color = Color(0xFF12352C)) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(Color(0xFF244E41), Color(0xFF112E27))))
                .padding(horizontal = 20.dp, vertical = 17.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("学习统计", Modifier.semantics { heading() }, color = StatisticsWhite,
                style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) {}) {
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(progress.lit.toString(), Modifier.alignByBaseline(), color = StatisticsWhite,
                            style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                        Text("/ ${progress.total}", Modifier.alignByBaseline(), color = StatisticsMint,
                            style = MaterialTheme.typography.titleMedium)
                    }
                    Text("已完成课时", color = StatisticsMint, style = MaterialTheme.typography.labelLarge)
                }
                Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { progress.fraction }, modifier = Modifier.fillMaxSize().clearAndSetSemantics {},
                        color = Color(0xFFBAE6CD), trackColor = StatisticsDivider, strokeWidth = 4.dp,
                    )
                    Text("${(progress.fraction * 100).roundToInt()}%", color = StatisticsWhite,
                        style = MaterialTheme.typography.labelMedium)
                }
            }
            HorizontalDivider(color = StatisticsDivider)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                StudyMetric(statistics.passedTrees.toString(), "通过结业考试", Modifier.weight(1f))
                StudyMetric(certificateCount.toString(), "已领取证书", Modifier.weight(1f))
            }
            HorizontalDivider(color = StatisticsDivider)
            AiUsageSummary(usage, color = StatisticsWhite, cumulative = true)
        }
    }
}

@Composable
private fun StudyMetric(value: String, label: String, modifier: Modifier) {
    Row(modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(value, color = StatisticsWhite, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(label, color = StatisticsMint, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun CertificateDisplay(certificate: CollectedCertificate, dark: Boolean, open: () -> Unit) {
    val frame = RoundedCornerShape(17.dp)
    val wall = if (dark) listOf(Color(0xFF292923), Color(0xFF34332C), Color(0xFF282721))
        else listOf(Color(0xFFF3EEE3), Color(0xFFFFFCF3), Color(0xFFEAE1D0))
    val ledge = if (dark) listOf(Color(0xFF877051), Color(0xFF4F4132))
        else listOf(Color(0xFFCCB38B), Color(0xFFA9895F))
    Column(
        Modifier.fillMaxWidth().clip(frame).clickable(role = Role.Button, onClickLabel = "查看结业证书", onClick = open)
            .testTag("collected-certificate-${certificate.treeId}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(.99f).clip(frame).background(Brush.verticalGradient(wall))
                .border(1.dp, ledge.first().copy(alpha = .32f), frame).padding(horizontal = 13.dp, vertical = 13.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(Modifier.fillMaxSize().padding(bottom = 5.dp), contentAlignment = Alignment.Center) {
                CertificateThumbnail(certificate, Modifier.aspectRatio(.79f, matchHeightConstraintsFirst = true)
                    .shadow(4.dp, RectangleShape))
            }
            Box(Modifier.fillMaxWidth().height(6.dp).background(Brush.verticalGradient(ledge), RoundedCornerShape(2.dp)))
        }
        Text(certificate.displayTitle, Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
    }
}

private val CollectedCertificate.displayTitle get() = if (isSample) "示例 · $treeName" else treeName
