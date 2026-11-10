package com.lifeskill.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifeskill.app.AppContainer
import com.lifeskill.app.data.AppearanceSettings
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope

@Composable
internal fun LifeSkillApp(
    container: AppContainer,
    appearanceSettings: AppearanceSettings,
    onSystemBars: (Boolean, Color) -> Unit,
) {
    val factory = remember(container, appearanceSettings) {
        viewModelFactory { initializer { AppViewModel(container, createSavedStateHandle(), appearanceSettings) } }
    }
    val app: AppViewModel = viewModel(factory = factory)
    val appearance by app.appearance.collectAsStateWithLifecycle()
    val dark = appearance.isDark(isSystemInDarkTheme())
    val library by app.library.collectAsStateWithLifecycle()
    val latestStudySkillId by app.latestStudySkillId.collectAsStateWithLifecycle()
    val route by app.route.collectAsStateWithLifecycle()
    val editor by app.editor.collectAsStateWithLifecycle()
    val deletion by app.deletion.collectAsStateWithLifecycle()
    val deleting by app.deleting.collectAsStateWithLifecycle()
    val notice by app.notice.collectAsStateWithLifecycle()
    val error by app.loadError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val stateHolder = rememberSaveableStateHolder()

    BackHandler(enabled = route.page != Page.HOME) { app.back() }
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        app.consumeNotice(text)
    }

    LifeSkillTheme(dark) {
        val gradient = when (route.page) {
            Page.TUTOR, Page.GENERATE -> aiColors.backdrop
            else -> studyColors.backdrop
        }
        SideEffect { onSystemBars(dark, gradient.first()) }
        Scaffold(
            modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(gradient)).safeDrawingPadding(),
            containerColor = Color.Transparent, contentColor = Ink, contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val current = library
                if (current == null) {
                    val problem = error
                    if (problem == null) Loading(Modifier.align(Alignment.Center))
                    else Action("$problem 点击重试", Glyph.TREE, app::load, Modifier.align(Alignment.Center))
                } else {
                    val entry = current.byId[route.id]
                    val latestStudySkill = current.byId[latestStudySkillId]
                    stateHolder.SaveableStateProvider(route.encode()) {
                        when (route.page) {
                            Page.HOME -> HomeScreen(current, latestStudySkill, app)
                            Page.DIRECTION -> entry?.let { DirectionScreen(it, current, latestStudySkill?.parentId, app) }
                            Page.TREE -> entry?.let { SkillTreeScreen(it, current, latestStudySkillId, app) }
                            Page.SKILL -> entry?.let { SkillScreen(it, current, app) }
                            Page.SETTINGS -> {
                                val model: SettingsViewModel = viewModel(
                                    factory = remember(container) {
                                        viewModelFactory { initializer { SettingsViewModel(container) } }
                                    },
                                )
                                SettingsScreen(app, model)
                            }
                            Page.GENERATE, Page.TUTOR -> {
                                val mode = if (route.page == Page.GENERATE) ChatMode.GENERATE else ChatMode.TUTOR
                                val scope = ChatScope(mode, route.id)
                                ChatScreen(scope, current, app, conversationModel(container, scope))
                            }
                        }
                    }
                    editor?.let { EditorDialog(it, current, app) }
                    deletion?.let { DeletionDialog(it, current, deleting, app) }
                }
            }
        }
    }
}

@Composable
private fun conversationModel(container: AppContainer, scope: ChatScope): ChatViewModel = viewModel(
    key = scope.id,
    factory = remember(container, scope) { viewModelFactory { initializer { ChatViewModel(container, scope) } } },
)
