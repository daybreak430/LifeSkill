package com.lifeskill.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.lifeskill.app.model.CertificateStyle
import com.lifeskill.app.model.CollectedCertificate
import com.lifeskill.app.model.DEFAULT_PROFILE_NAME
import com.lifeskill.app.model.ProfileAvatar
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.validatedNickname

private val CertificateNight = Color(0xFF0B1E26)
private val CertificatePaper = Color(0xFFFFF3DA)
private val CertificateGold = Color(0xFFD0A65E)
private val CertificateInk = Color(0xFF214437)

internal data class CertificateVisibilityControl(val hidden: Boolean, val status: AssessmentStatus, val toggle: () -> Unit)

@Composable
internal fun IssuedCertificateDialog(
    certificate: CollectedCertificate, visibility: CertificateVisibilityControl? = null, onClose: () -> Unit,
) {
    ExamCertificateDialog(certificate.treeName, certificate.style, certificate.recipient, certificate.avatar,
        visibility = visibility, onClose = onClose)
}

@Composable
internal fun ExamCertificateDialog(
    treeName: String, style: CertificateStyle, recipient: String?, avatar: ProfileAvatar, collected: Boolean = false,
    status: AssessmentStatus = AssessmentStatus(), onCollect: ((String?) -> Unit)? = null,
    visibility: CertificateVisibilityControl? = null, onClose: () -> Unit,
) {
    val viewing = onCollect == null
    var opened by rememberSaveable { mutableStateOf(viewing) }
    var closing by rememberSaveable { mutableStateOf(false) }
    var requested by rememberSaveable { mutableStateOf(false) }
    var enteringNickname by rememberSaveable { mutableStateOf(false) }
    var nickname by rememberSaveable { mutableStateOf("") }
    val exiting = closing || (!viewing && collected)
    var drag by remember { mutableFloatStateOf(0f) }
    val opening = remember { Animatable(if (opened) 1f else 0f) }
    val exit = remember { Animatable(0f) }
    val close by rememberUpdatedState(onClose)
    val showEnvelope by remember { derivedStateOf { opening.value < .86f } }
    val showCertificate by remember { derivedStateOf { opening.value > .32f } }
    val canCollect by remember { derivedStateOf { opening.value >= 1f } }

    // 同一条时间轴先翻开封口，再抽出证书。重建页面时直接恢复已拆开的状态。
    LaunchedEffect(opened) {
        if (opened) opening.animateTo(1f, tween(720))
    }
    LaunchedEffect(exiting) {
        if (exiting) {
            exit.animateTo(1f, tween(240))
            close()
        }
    }

    Dialog(
        onDismissRequest = {
            if (!status.busy && visibility?.status?.busy != true) {
                if (enteringNickname && !exiting) enteringNickname = false else closing = true
            }
        },
        properties = DialogProperties(
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.let {
                it.setWindowAnimations(0)
                it.setDimAmount(0f)
                WindowCompat.getInsetsController(it, it.decorView).apply {
                    isAppearanceLightStatusBars = false
                    isAppearanceLightNavigationBars = false
                }
            }
        }
        Box(
            Modifier.fillMaxSize().testTag("exam-certificate")
                .drawBehind { drawRect(CertificateNight.copy(alpha = .90f * (1f - exit.value))) }
                .safeDrawingPadding().imePadding().padding(horizontal = 24.dp, vertical = 12.dp)
                .graphicsLayer {
                    alpha = 1f - exit.value
                    scaleX = 1f - .07f * exit.value
                    scaleY = scaleX
                    translationY = 32.dp.toPx() * exit.value
                },
            contentAlignment = Alignment.Center,
        ) {
            if (showEnvelope) {
                val interaction = if (opened || exiting) {
                    Modifier.clearAndSetSemantics { hideFromAccessibility() }
                } else {
                    Modifier.semantics(mergeDescendants = true) {
                        role = Role.Button
                        onClick(label = "拆开信封") {
                            opened = true
                            true
                        }
                    }.pointerInput(Unit) {
                        val threshold = 32.dp.toPx()
                        detectVerticalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragCancel = { drag = 0f },
                            onDragEnd = {
                                if (drag <= -threshold) opened = true
                                drag = 0f
                            },
                            onVerticalDrag = { _, delta -> drag += delta },
                        )
                    }
                }
                Column(
                    Modifier.widthIn(max = 244.dp).fillMaxWidth().then(interaction).graphicsLayer {
                        translationY = drag.coerceIn(-48.dp.toPx(), 0f) * .55f +
                            54.dp.toPx() * motionPhase(opening.value, .30f, .86f)
                        alpha = 1f - motionPhase(opening.value, .48f, .86f)
                    },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CertificateEnvelope { opening.value }
                    Text("上滑拆开", color = CertificatePaper, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (enteringNickname && !exiting) {
                CertificateNicknameForm(nickname, { nickname = it }, status, cancel = { enteringNickname = false }) {
                    requested = true
                    onCollect?.invoke(it)
                }
            } else if (showCertificate) {
                Column(
                    Modifier.widthIn(max = 360.dp).fillMaxWidth().graphicsLayer {
                        val reveal = motionPhase(opening.value, .32f, 1f)
                        alpha = motionPhase(reveal, 0f, .55f)
                        scaleX = .66f + .34f * reveal
                        scaleY = scaleX
                        translationY = 50.dp.toPx() * (1f - reveal)
                    }.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    CertificateCard(treeName, style, recipient ?: DEFAULT_PROFILE_NAME, avatar)
                    if (viewing) {
                        CertificateViewerActions(visibility, exiting) { closing = true }
                    } else {
                        if (requested && status.error != null) AssessmentStatusText(status)
                        Action("收下", Glyph.CHECK, {
                            if (recipient == null) {
                                enteringNickname = true
                            } else {
                                requested = true
                                onCollect.invoke(null)
                            }
                        }, Modifier.fillMaxWidth(), primary = true, enabled = canCollect && !exiting && !status.busy)
                    }
                }
            }
        }
    }
}

@Composable
private fun CertificateViewerActions(visibility: CertificateVisibilityControl?, exiting: Boolean, close: () -> Unit) {
    val enabled = !exiting && visibility?.status?.busy != true
    visibility?.status?.error?.let { Text(it, color = CertificatePaper, style = MaterialTheme.typography.bodySmall) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (visibility != null) {
            TextButton(onClick = visibility.toggle, modifier = Modifier.weight(1f), enabled = enabled) {
                Text(if (visibility.hidden) "取消隐藏" else "隐藏该证书", color = CertificatePaper)
            }
        }
        Action("关闭", Glyph.CLOSE, close, Modifier.weight(1f), primary = true, enabled = enabled)
    }
}

@Composable
private fun CertificateNicknameForm(
    nickname: String, change: (String) -> Unit, status: AssessmentStatus, cancel: () -> Unit, submit: (String) -> Unit,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    Surface(Modifier.widthIn(max = 360.dp).fillMaxWidth(), shape = DialogShape, color = Panel) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("填写证书署名", style = MaterialTheme.typography.titleLarge)
            NicknameField(nickname, { change(it); validationError = null }, !status.busy,
                Modifier.focusRequester(focus).testTag("certificate-nickname"))
            AssessmentStatusText(AssessmentStatus(error = validationError ?: status.error))
            Action("保存并收下", Glyph.CHECK, {
                try {
                    val valid = checkNotNull(validatedNickname(nickname, required = true))
                    keyboard?.hide()
                    submit(valid)
                } catch (error: RuleViolation) {
                    validationError = error.message
                }
            }, Modifier.fillMaxWidth(), primary = true, enabled = !status.busy)
            TextButton({ keyboard?.hide(); cancel() }, Modifier.align(Alignment.CenterHorizontally), enabled = !status.busy) {
                Text("取消")
            }
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun CertificateEnvelope(progress: () -> Float) {
    Canvas(Modifier.fillMaxWidth().height(200.dp)) {
        val flap = motionPhase(progress(), 0f, .42f)
        val top = size.height * .27f
        val fold = Offset(center.x, top + size.height * .25f)
        val bottom = size.height - 1.dp.toPx()
        val left = 2.dp.toPx()
        val right = size.width - left
        drawRoundRect(
            CertificateInk, Offset(left, top), Size(right - left, bottom - top), CornerRadius(12.dp.toPx()),
        )
        val seam = CertificateGold.copy(alpha = .48f)
        drawLine(seam, Offset(left + 8.dp.toPx(), bottom - 4.dp.toPx()), fold, 1.dp.toPx())
        drawLine(seam, Offset(right - 8.dp.toPx(), bottom - 4.dp.toPx()), fold, 1.dp.toPx())
        val tip = Offset(fold.x, top + (fold.y - top) * (1f - 2f * flap))
        val lid = Path().apply {
            moveTo(left, top)
            lineTo(tip.x, tip.y)
            lineTo(right, top)
            close()
        }
        drawPath(lid, Color(0xFF376252))
        drawLine(seam, Offset(left, top), tip, 1.dp.toPx())
        drawLine(seam, tip, Offset(right, top), 1.dp.toPx())
        val seal = 1f - motionPhase(flap, 0f, .5f)
        drawCircle(CertificateGold.copy(alpha = seal), 11.dp.toPx() * seal, tip)
        drawCircle(CertificateInk.copy(alpha = .42f * seal), 6.dp.toPx() * seal, tip)
    }
}
