package com.lifeskill.app.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun RestartLoadingDialog(onLeave: () -> Unit) {
    // 原生模态窗口隔离旧消息的触控与可访问焦点；返回离开页面，不只隐藏仍在工作的遮罩。
    Dialog(onDismissRequest = onLeave,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false,
            dismissOnBackPress = true, dismissOnClickOutside = false)) {
        // 遮罩与卡片一起淡入，重生成开始时不至于“啪”地糊上一层黑。
        val appear = remember { Animatable(0f) }
        LaunchedEffect(Unit) { appear.animateTo(1f, motion(Motion.Base)) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f * appear.value)).safeDrawingPadding(),
            contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(28.dp), color = Panel,
                modifier = Modifier.widthIn(min = 188.dp, max = 260.dp).testTag("restart-loading").graphicsLayer {
                    val scale = .94f + .06f * appear.value
                    scaleX = scale
                    scaleY = scale
                    alpha = appear.value
                }) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    CircularProgressIndicator(Modifier.size(46.dp), color = aiColors.accent, strokeWidth = 3.dp)
                    Text("正在重新生成", color = Ink, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
