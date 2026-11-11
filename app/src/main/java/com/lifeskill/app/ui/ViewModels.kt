package com.lifeskill.app.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lifeskill.app.AppContainer
import com.lifeskill.app.ai.ModelMessage
import com.lifeskill.app.ai.AiProtocol
import com.lifeskill.app.ai.ModelSettings
import com.lifeskill.app.ai.Provider
import com.lifeskill.app.data.EntryEdit
import com.lifeskill.app.data.AppearanceMode
import com.lifeskill.app.data.AppearanceSettings
import com.lifeskill.app.model.ChatMode
import com.lifeskill.app.model.ChatScope
import com.lifeskill.app.model.Entry
import com.lifeskill.app.model.EntryIcon
import com.lifeskill.app.model.EntryKind
import com.lifeskill.app.model.GenerationDestination
import com.lifeskill.app.model.GenerationOptions
import com.lifeskill.app.model.Library
import com.lifeskill.app.model.LibraryRules
import com.lifeskill.app.model.RuleViolation
import com.lifeskill.app.model.SkillState
import com.lifeskill.app.model.SkillUnlock
import com.lifeskill.app.model.Turn
import com.lifeskill.app.model.TurnKind
import com.lifeskill.app.model.unlocksSince
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal enum class Page { HOME, DIRECTION, TREE, SKILL, GENERATE, TUTOR, SETTINGS }

internal data class Route(val page: Page, val id: String? = null) {
    fun encode() = "${page.name}:${id.orEmpty()}"

    companion object {
        fun decode(value: String) = Route(
            Page.valueOf(value.substringBefore(':')), value.substringAfter(':').ifEmpty { null },
        )
        fun entry(entry: Entry) = Route(
            when (entry.kind) {
                EntryKind.DIRECTION -> Page.DIRECTION
                EntryKind.TREE -> Page.TREE
                EntryKind.SKILL -> Page.SKILL
            },
            entry.id,
        )
    }
}

internal data class NavigationFrame(val route: Route, val depth: Int)

internal data class Editor(
    val initial: EntryEdit,
    val saving: Boolean = false,
    val error: String? = null,
    val icon: EntryIcon? = initial.icon,
    val suggesting: Boolean = false,
    val iconError: String? = null,
)

internal class AppViewModel(
    private val container: AppContainer,
    private val state: SavedStateHandle,
    private val appearanceSettings: AppearanceSettings,
) : ViewModel() {
    private val _appearance = MutableStateFlow(appearanceSettings.load())
    val appearance = _appearance.asStateFlow()
    private val routes = state.getStateFlow("routes", arrayListOf(Route(Page.HOME).encode()))
    val navigation = routes.map { NavigationFrame(Route.decode(it.last()), it.size) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, NavigationFrame(Route.decode(routes.value.last()), routes.value.size))
    private val _library = MutableStateFlow<Library?>(null)
    val library = _library.asStateFlow()
    val latestStudySkillId = container.conversations.watchLatestStudySkillId()
        .catch { error -> if (error is CancellationException) throw error }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val _editor = MutableStateFlow<Editor?>(null)
    val editor = _editor.asStateFlow()
    private val _addedEntryId = MutableStateFlow<String?>(null)
    val addedEntryId = _addedEntryId.asStateFlow()
    private val _delete = MutableStateFlow<Entry?>(null)
    val deletion = _delete.asStateFlow()
    private val _deleting = MutableStateFlow(false)
    val deleting = _deleting.asStateFlow()
    private val _notice = MutableStateFlow<String?>(null)
    val notice = _notice.asStateFlow()
    private val _loadError = MutableStateFlow<String?>(null)
    val loadError = _loadError.asStateFlow()
    private val _unlocks = MutableStateFlow<List<SkillUnlock>>(emptyList())
    val unlocks = _unlocks.asStateFlow()

    init { load() }

    fun setAppearance(mode: AppearanceMode) {
        if (_appearance.value == mode) return
        appearanceSettings.save(mode)
        _appearance.value = mode
    }

    fun load() {
        _loadError.value = null
        viewModelScope.launch {
            try {
                container.library.initialize()
                container.library.library.collect { current ->
                    val previous = _library.value
                    _library.value = current
                    if (previous != null) {
                        _unlocks.value = _unlocks.value.filter { it.skillId in current.byId } +
                            current.unlocksSince(previous)
                    }
                    discardMissingRoutes(current)
                }
            } catch (error: Exception) {
                _loadError.value = readable(error, "无法读取本机技能库。")
            }
        }
    }

    fun open(route: Route) {
        val stack = routes.value
        if (stack.last() != route.encode()) state["routes"] = ArrayList(stack + route.encode())
    }

    fun back() {
        val stack = routes.value
        if (stack.size <= 1) return
        val current = Route.decode(stack.last())
        val previous = stack.dropLast(1)
        val tree = if (current.page == Page.TUTOR) {
            library.value?.byId?.get(current.id)?.parentId?.let { Route(Page.TREE, it).encode() }
        } else null
        val destination = if (tree == null) previous else {
            val treeIndex = previous.lastIndexOf(tree)
            // 从首页续学直接进入对话时，历史栈里还没有对应的技能树。
            if (treeIndex >= 0) previous.take(treeIndex + 1)
            else previous.dropLastWhile { Route.decode(it).page == Page.SKILL } + tree
        }
        state["routes"] = ArrayList(destination)
    }

    fun finishUnlock(key: String) {
        _unlocks.value = _unlocks.value.filterNot { it.key == key }
    }

    fun continueStudy(skill: Entry) {
        val current = library.value ?: return
        if (skill.id !in current.byId) return
        val locked = current.skillState(skill.id) == SkillState.LOCKED
        open(if (locked) Route.entry(skill) else Route(Page.TUTOR, skill.id))
    }

    private fun discardMissingRoutes(library: Library) {
        val stack = routes.value
        val valid = stack.filter { encoded ->
            val route = Route.decode(encoded)
            val id = route.id
            id == null || id in library.byId && (route.page != Page.TUTOR || library.skillState(id) != SkillState.LOCKED)
        }
        if (valid != stack) state["routes"] = ArrayList(valid)
    }

    fun add(kind: EntryKind, parentId: String? = null) {
        _editor.value = Editor(EntryEdit(null, parentId, kind, ""))
    }

    fun edit(entry: Entry) {
        val requirements = library.value?.prerequisitesOf(entry.id).orEmpty()
        _editor.value = Editor(EntryEdit(
            entry.id, entry.parentId, entry.kind, entry.name, entry.goal, requirements, entry.icon, entry.shortName.orEmpty(),
        ))
    }

    fun closeEditor() {
        if (_editor.value?.saving != true) _editor.value = null
    }

    fun saveEntry(edit: EntryEdit) {
        val editor = _editor.value?.takeUnless { it.saving || it.suggesting } ?: return
        _editor.value = editor.copy(saving = true, error = null)
        viewModelScope.launch {
            try {
                val saved = container.library.save(edit)
                if (edit.id == null) {
                    _addedEntryId.value = saved.id
                    if (saved.kind == EntryKind.TREE && Route.decode(routes.value.last()).page == Page.HOME) {
                        open(Route(Page.DIRECTION, saved.parentId))
                    }
                }
                _editor.value = null
            } catch (error: Exception) {
                _editor.value = editor.copy(error = readable(error, "保存失败，请重试。"))
            }
        }
    }

    fun finishShowingAddition(id: String) {
        if (_addedEntryId.value == id) _addedEntryId.value = null
    }

    fun chooseIcon(icon: EntryIcon?) {
        val editor = _editor.value?.takeUnless { it.saving || it.suggesting } ?: return
        _editor.value = editor.copy(icon = icon, iconError = null)
    }

    fun suggestIcon(name: String, goal: String) {
        val editor = _editor.value?.takeUnless { it.saving || it.suggesting } ?: return
        _editor.value = editor.copy(suggesting = true, iconError = null)
        viewModelScope.launch {
            try {
                val data = JSONObject()
                    .put("type", editor.initial.kind.label)
                    .put("name", LibraryRules.name(name))
                    .put("description", goal.trim().take(LibraryRules.GOAL_LIMIT))
                val config = withContext(Dispatchers.IO) { container.settings.active() }
                val messages = listOf(ModelMessage("system", AiProtocol.iconPrompt()), ModelMessage("user", data.toString()))
                val icon = AiProtocol.icon(container.gateway.complete(config, messages, structured = true))
                _editor.value?.takeIf { it.initial === editor.initial }?.let {
                    _editor.value = it.copy(icon = icon, suggesting = false)
                }
            } catch (error: Exception) {
                val message = readable(error, "无法推荐图标，请重试或手动选择。")
                _editor.value?.takeIf { it.initial === editor.initial }?.let {
                    _editor.value = it.copy(suggesting = false, iconError = message)
                }
            }
        }
    }

    fun confirmDelete(entry: Entry) {
        _delete.value = entry
    }

    fun cancelDelete() {
        if (!_deleting.value) _delete.value = null
    }

    fun deleteEntry() {
        val entry = _delete.value?.takeUnless { _deleting.value } ?: return
        _deleting.value = true
        viewModelScope.launch {
            try {
                container.library.delete(entry.id)
                _delete.value = null
                _notice.value = "已删除${entry.kind.label}"
            } catch (error: Exception) {
                _notice.value = readable(error, "删除失败，请重试。")
            } finally {
                _deleting.value = false
            }
        }
    }

    fun consumeNotice(text: String) {
        if (_notice.value == text) _notice.value = null
    }
}

internal data class ChatStatus(
    val working: Boolean = false,
    val error: String? = null,
    val draftError: String? = null,
    val requestId: String? = null,
)

internal data class ChatHistory(val turns: List<Turn> = emptyList(), val celebrationId: String? = null)

internal class ChatViewModel(container: AppContainer, private val scope: ChatScope) : ViewModel() {
    private val repository = container.conversations
    private val _status = MutableStateFlow(ChatStatus())
    val status = _status.asStateFlow()
    val history = repository.watch(scope)
        .runningFold<List<Turn>, ChatHistory?>(null) { previous, turns ->
            val last = turns.lastOrNull()
            // 通过消息与点亮在同一事务提交，首份历史和撤回后重新露出的旧消息不触发庆祝。
            val celebration = if (previous != null && last?.kind == TurnKind.PASSED &&
                (previous.celebrationId == last.id || previous.turns.none { it.id == last.id })
            ) last.id else null
            ChatHistory(turns, celebration)
        }
        .filterNotNull()
        .catch { error ->
            if (error is CancellationException) throw error
            _status.value = ChatStatus(error = "无法读取本机对话记录。")
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatHistory())

    fun send(text: String, check: Boolean = false) = run { repository.send(scope, text, check) }
    fun generateTree(offerId: String, options: GenerationOptions) = run {
        repository.generateTree(scope, offerId, options)
    }

    fun restartLesson(requirements: String) = run { repository.restartLesson(scope, requirements) }
    fun retry(turn: Turn) = run(requestId = turn.id) { repository.retry(scope, turn.id) }
    fun discard(turn: Turn) = run(requestId = turn.id) { repository.discardPending(scope, turn.id) }
    fun confirmDraft(id: String, destination: GenerationDestination) = run(addingDraft = true) {
        repository.confirmDraft(scope, id, destination)
    }

    fun clearDraftError() {
        _status.value = _status.value.copy(draftError = null)
    }

    private fun run(addingDraft: Boolean = false, requestId: String? = null, action: suspend () -> Unit) {
        if (_status.value.working) return
        _status.value = ChatStatus(working = true, requestId = requestId)
        viewModelScope.launch {
            try {
                action()
                _status.value = ChatStatus(requestId = requestId)
            } catch (error: Exception) {
                _status.value = if (addingDraft) ChatStatus(draftError = readable(error, "添加失败，请重试。"))
                else ChatStatus(error = readable(error, "本次请求失败，请重试。"), requestId = requestId)
            }
        }
    }
}

internal data class SettingsStatus(
    val config: ModelSettings? = null,
    val working: Boolean = false,
    val message: String? = null,
    val error: Boolean = false,
)

internal class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _status = MutableStateFlow(SettingsStatus())
    val status = _status.asStateFlow()

    fun load(provider: Provider? = null) {
        run {
            val config = withContext(Dispatchers.IO) {
                if (provider == null) container.settings.active() else container.settings.load(provider)
            }
            SettingsStatus(config)
        }
    }

    fun edit(config: ModelSettings) {
        _status.value = _status.value.copy(config = config, message = null, error = false)
    }

    fun save() {
        val config = _status.value.config ?: return
        run {
            withContext(Dispatchers.IO) { container.settings.save(config) }
            SettingsStatus(config.validated(requireKey = false), message = "已保存为当前模型")
        }
    }

    fun test() {
        val config = _status.value.config ?: return
        run {
            val input = listOf(ModelMessage("user", "请简短回复「连接成功」。"))
            container.gateway.complete(config.validated(), input, structured = false)
            SettingsStatus(config, message = "连接成功")
        }
    }

    private fun run(action: suspend () -> SettingsStatus) {
        if (_status.value.working) return
        _status.value = _status.value.copy(working = true, message = null, error = false)
        viewModelScope.launch {
            try {
                _status.value = action()
            } catch (error: Exception) {
                _status.value = _status.value.copy(working = false, message = readable(error, "模型设置操作失败。"), error = true)
            }
        }
    }
}

private fun readable(error: Exception, fallback: String): String = when (error) {
    is CancellationException -> throw error
    is RuleViolation -> error.message ?: fallback
    else -> fallback
}
