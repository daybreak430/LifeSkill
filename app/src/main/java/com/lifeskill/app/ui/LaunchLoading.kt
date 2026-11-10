package com.lifeskill.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lifeskill.app.R

@Composable
internal fun LaunchLoading(background: Color) {
    val accent = colorResource(R.color.launch_accent)
    Box(Modifier.fillMaxSize().background(background), contentAlignment = Alignment.Center) {
        // 标记单独居中，文案和进度条不会改变与系统开屏衔接的位置。
        Image(painterResource(R.drawable.launch_mark), contentDescription = null, modifier = Modifier.size(80.dp))
        Column(
            Modifier.offset(y = 77.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, color = colorResource(R.color.launch_ink))
            LinearProgressIndicator(Modifier.size(width = 36.dp, height = 2.dp), color = accent, trackColor = accent.copy(alpha = .18f))
        }
    }
}
