package com.lifeskill.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .42f)).safeDrawingPadding(),
            contentAlignment = Alignment.Center) {
            Surface(shape = RoundedCornerShape(28.dp), color = Panel,
                modifier = Modifier.widthIn(min = 188.dp, max = 260.dp).testTag("restart-loading")) {
                Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    CircularProgressIndicator(Modifier.size(46.dp), color = aiColors.accent, strokeWidth = 3.dp)
                    Text("正在重新生成", color = Ink, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}
