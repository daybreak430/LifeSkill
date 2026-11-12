package com.lifeskill.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.lifeskill.app.AppContainer
import com.lifeskill.app.data.UsageSummary
import com.lifeskill.app.R
import com.lifeskill.app.data.AppearanceSettings
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.LearningStatistics
import com.lifeskill.app.model.TreeAssessmentSummary
import com.lifeskill.app.model.TreeChallenge
import com.lifeskill.app.model.examAvailable
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop

private const val PAGE_MOTION_MS = 220

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
    val systemDark = isSystemInDarkTheme()
    val dark = appearance.isDark(systemDark)
    val library by app.library.collectAsStateWithLifecycle()
    val latestStudySkillId by app.latestStudySkillId.collectAsStateWithLifecycle()
    val navigation by app.navigation.collectAsStateWithLifecycle()
    val route = navigation.route
    val unlocks by app.unlocks.collectAsStateWithLifecycle()
    val editor by app.editor.collectAsStateWithLifecycle()
    val addedEntryId by app.addedEntryId.collectAsStateWithLifecycle()
    val deletion by app.deletion.collectAsStateWithLifecycle()
    val deleting by app.deleting.collectAsStateWithLifecycle()
    val notice by app.notice.collectAsStateWithLifecycle()
    val error by app.loadError.collectAsStateWithLifecycle()
    val assessments by container.assessments.summaries.collectAsStateWithLifecycle(emptyMap())
    val usage by container.usage.summary.collectAsStateWithLifecycle(UsageSummary())
    val snackbar = remember { SnackbarHostState() }
    val stateHolder = rememberSaveableStateHolder()
    val resolver = LocalContext.current.applicationContext.contentResolver
    val exportDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) {
        app.finishConversationExport(it, resolver)
    }
    fun exportConversation(entryId: String) = app.prepareConversationExport(entryId) { exportDocument.launch(it) }

    BackHandler(enabled = route.page != Page.HOME) { app.back() }
    LaunchedEffect(notice) {
        val text = notice ?: return@LaunchedEffect
        snackbar.showSnackbar(text)
        app.consumeNotice(text)
    }

    LifeSkillTheme(dark) {
        if (library == null && error == null) {
            val launchBackground = colorResource(R.color.launch_background)
            SideEffect { onSystemBars(systemDark, launchBackground) }
            LaunchLoading(launchBackground)
            return@LifeSkillTheme
        }
        val gradient = when (route.page) {
            Page.TUTOR, Page.GENERATE, Page.DIAGNOSTIC, Page.FEYNMAN -> aiColors.backdrop
            else -> studyColors.backdrop
        }
        val backdrop = key(dark) {
            val top by animateColorAsState(gradient.first(), tween(PAGE_MOTION_MS), label = "页面顶部")
            val middle by animateColorAsState(
                if (gradient.size == 3) gradient[1] else lerp(gradient.first(), gradient.last(), .5f),
                tween(PAGE_MOTION_MS), label = "页面中部",
            )
            val bottom by animateColorAsState(gradient.last(), tween(PAGE_MOTION_MS), label = "页面底部")
            val brush = { Brush.verticalGradient(listOf(top, middle, bottom)) }
            brush
        }
        SideEffect { onSystemBars(dark, gradient.first()) }
        val showTabs = library != null && (route.page == Page.HOME || route.page == Page.ME)
        val tabBackdrop = rememberLayerBackdrop()
        // 玻璃层记录它下方的页面和渐变，标签栏作为独立浮层绘制，滚动内容可以从下面经过。
        val recording = if (showTabs && liveTabGlass) Modifier.layerBackdrop(tabBackdrop)
            .drawBehind { drawRect(backdrop()) } else Modifier
        Scaffold(
            modifier = Modifier.fillMaxSize()
                .drawBehind { drawRect(backdrop()) }.safeDrawingPadding(),
            containerColor = Color.Transparent, contentColor = Ink, contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = if (showTabs) HomeTabClearance else 0.dp)) },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                val current = library
                if (current == null) {
                    error?.let { Action("$it 点击重试", Glyph.TREE, app::load, Modifier.align(Alignment.Center)) }
                } else {
                    AnimatedContent(
                        targetState = navigation,
                        modifier = Modifier.fillMaxSize().clipToBounds().then(recording),
                        contentKey = { it.route.encode() },
                        transitionSpec = {
                            val backToTree = initialState.route.page == Page.TUTOR && targetState.route.page == Page.TREE
                            val direction = if (targetState.depth < initialState.depth || backToTree) -1 else 1
                            (fadeIn(tween(PAGE_MOTION_MS)) + slideInHorizontally(tween(PAGE_MOTION_MS, easing = FastOutSlowInEasing)) {
                                direction * it / 14
                            }) togetherWith (fadeOut(tween(160)) + slideOutHorizontally(tween(PAGE_MOTION_MS, easing = FastOutSlowInEasing)) {
                                -direction * it / 22
                            })
                        },
                        label = "页面转场",
                    ) { frame ->
                        val displayedRoute = frame.route
                        val active = displayedRoute == route
                        // 删除后退场页仍使用最后一帧的数据，避免卡片先消失。
                        var lastLibrary by remember { mutableStateOf(current) }
                        SideEffect { if (active) lastLibrary = current }
                        val displayedLibrary = if (active) current else lastLibrary
                        val entry = displayedLibrary.byId[displayedRoute.id]
                        val latestStudySkill = displayedLibrary.byId[latestStudySkillId]
                        val addition = addedEntryId.takeIf { active }
                        Box(Modifier.fillMaxSize().routeInput(active)) {
                            stateHolder.SaveableStateProvider(displayedRoute.encode()) {
                                when (displayedRoute.page) {
                                    Page.HOME -> HomeScreen(displayedLibrary, latestStudySkill, app, addition)
                                    Page.ME -> {
                                        val statistics = remember(displayedLibrary, assessments) {
                                            LearningStatistics.from(displayedLibrary, assessments)
                                        }
                                        PersonalScreen(app, personalModel(container), statistics, active, dark, usage)
                                    }
                                    Page.PROFILE_EDIT -> ProfileEditScreen(app, personalModel(container), active)
                                    Page.DIRECTION -> entry?.let {
                                        val issuedCourseIds by container.certificates.issuedCourseIds.collectAsStateWithLifecycle(emptySet())
                                        DirectionScreen(it, displayedLibrary, latestStudySkill?.parentId, issuedCourseIds, app, addition)
                                    }
                                    Page.TREE -> entry?.let {
                                        val unlock = unlocks.firstOrNull { unlock -> unlock.treeId == it.id }
                                        SkillTreeScreen(it, displayedLibrary, latestStudySkillId, app, unlock, active, addition,
                                            assessments[it.id] ?: TreeAssessmentSummary(), usage.courses[it.id], usage.generation[it.id],
                                            onExportConversation = { exportConversation(it.id) })
                                    }
                                    Page.SKILL -> entry?.let { SkillScreen(it, displayedLibrary, app, usage.lessons[it.id]) }
                                    Page.SETTINGS -> {
                                        val model: SettingsViewModel = viewModel(
                                            factory = remember(container) {
                                                viewModelFactory { initializer { SettingsViewModel(container) } }
                                            },
                                        )
                                        SettingsScreen(app, model)
                                    }
                                    Page.GENERATE, Page.TUTOR -> {
                                        val mode = if (displayedRoute.page == Page.GENERATE) ChatMode.GENERATE else ChatMode.TUTOR
                                        val scope = ChatScope(mode, displayedRoute.id)
                                        val onExport = scope.ownerId?.takeIf { mode == ChatMode.TUTOR }
                                            ?.let { id -> { exportConversation(id) } }
                                        ChatScreen(scope, displayedLibrary, app, conversationModel(container, scope), active, onExport)
                                    }
                                    Page.EXAM -> entry?.let {
                                        ExamScreen(it, displayedLibrary.examAvailable(it.id), app,
                                            assessmentModel(container, it.id), active, usage.courses[it.id])
                                    }
                                    Page.DIAGNOSTIC, Page.FEYNMAN -> entry?.let {
                                        val kind = if (displayedRoute.page == Page.DIAGNOSTIC) TreeChallenge.DIAGNOSTIC else TreeChallenge.FEYNMAN
                                        ChallengeScreen(it, kind, assessments[it.id]?.passed == true, app, assessmentModel(container, it.id))
                                    }
                                }
                            }
                        }
                    }
                    if (showTabs) {
                        FloatingHomeTabs(route.page, tabBackdrop, dark, app::selectTab, Modifier.align(Alignment.BottomCenter))
                    }
                    editor?.let { EditorDialog(it, current, app) }
                    deletion?.let { DeletionDialog(it, current, deleting, app) }
                }
            }
        }
    }
}

private fun Modifier.routeInput(active: Boolean): Modifier =
    if (active) this else clearAndSetSemantics { hideFromAccessibility() }.pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }

@Composable
private fun conversationModel(container: AppContainer, scope: ChatScope): ChatViewModel = viewModel(
    key = scope.id,
    factory = remember(container, scope) { viewModelFactory { initializer { ChatViewModel(container, scope) } } },
)

@Composable
private fun assessmentModel(container: AppContainer, treeId: String): TreeAssessmentViewModel = viewModel(
    key = "assessment:$treeId",
    factory = remember(container, treeId) {
        viewModelFactory {
            initializer { TreeAssessmentViewModel(container.assessments, treeId, container.certificates, container.profiles) }
        }
    },
)

@Composable
private fun personalModel(container: AppContainer): PersonalViewModel = viewModel(
    factory = remember(container) { viewModelFactory { initializer { PersonalViewModel(container) } } },
)
