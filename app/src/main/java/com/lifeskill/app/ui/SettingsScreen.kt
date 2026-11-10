package com.lifeskill.app.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.data.AppearanceMode

@Composable
internal fun SettingsScreen(app: AppViewModel, model: SettingsViewModel) {
    val state by model.status.collectAsStateWithLifecycle()
    val appearance by app.appearance.collectAsStateWithLifecycle()
    val config = state.config
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(model) { model.load() }
    Column(Modifier.fillMaxSize()) {
        PageHeader("设置", app::back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingsCard("外观") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppearanceMode.entries.forEach { mode ->
                        SelectionChip(mode.label, appearance == mode, { app.setAppearance(mode) })
                    }
                }
            }
            SettingsCard("模型设置") {
                if (config == null) {
                    if (state.working) Loading(Modifier.fillMaxWidth().padding(20.dp))
                    else if (state.error) TextButton(onClick = { model.load() }) { Text("重新加载模型设置") }
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Provider.entries.forEach { provider ->
                            SelectionChip(
                                provider.label, provider == config.provider,
                                onClick = { if (provider != config.provider) model.load(provider) },
                                enabled = !state.working,
                            )
                        }
                    }
                    if (config.provider == Provider.CUSTOM) {
                        OutlinedTextField(
                            config.baseUrl, { model.edit(config.copy(baseUrl = it)) }, Modifier.fillMaxWidth(),
                            label = { Text("Base URL") }, placeholder = { Text("https://example.com/v1") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            singleLine = true, enabled = !state.working, shape = ControlShape, colors = fieldColors(),
                        )
                        OutlinedTextField(
                            config.model, { model.edit(config.copy(model = it)) }, Modifier.fillMaxWidth(),
                            label = { Text("模型名") }, singleLine = true, enabled = !state.working,
                            shape = ControlShape, colors = fieldColors(),
                        )
                    } else {
                        Surface(shape = ControlShape, color = Ground) {
                            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(config.model, style = MaterialTheme.typography.titleMedium)
                                Text(config.baseUrl, style = MaterialTheme.typography.bodyMedium, color = Muted)
                            }
                        }
                    }
                    OutlinedTextField(
                        config.apiKey, { model.edit(config.copy(apiKey = it)) }, Modifier.fillMaxWidth(),
                        label = { Text("API Key") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        enabled = !state.working, shape = ControlShape, colors = fieldColors(),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Action("测试连接", Glyph.TARGET, {
                            keyboard?.hide()
                            model.test()
                        }, Modifier.weight(1f), enabled = !state.working)
                        Action("保存", Glyph.CHECK, {
                            keyboard?.hide()
                            model.save()
                        }, Modifier.weight(1f), primary = true, enabled = !state.working)
                    }
                    Text("AI 功能会把当前任务内容发送给选定的模型服务。", color = Muted, style = MaterialTheme.typography.bodySmall)
                }
                if (state.working && config != null) Loading(Modifier.fillMaxWidth().padding(8.dp))
                state.message?.let {
                    Text(
                        it, Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        color = if (state.error) MaterialTheme.colorScheme.error else Gold,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Surface(Modifier.fillMaxWidth().animateContentSize(tween(180)).panelShadow(), shape = PanelShape, color = Panel) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
