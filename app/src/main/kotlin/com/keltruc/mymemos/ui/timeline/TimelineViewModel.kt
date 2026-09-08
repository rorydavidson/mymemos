package com.keltruc.mymemos.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.AccountSettingsRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.repository.ShortcutRepository
import com.keltruc.mymemos.model.Shortcut
import com.keltruc.mymemos.data.sync.FailedOp
import com.keltruc.mymemos.data.sync.SyncEngine
import com.keltruc.mymemos.data.sync.SyncState
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.NoteColour
import com.keltruc.mymemos.ui.components.toggleTaskLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val account: Account? = null,
    val memos: List<Memo> = emptyList(),
    val tags: List<String> = emptyList(),
    val query: String = "",
    val selectedTag: String? = null,
    val showArchived: Boolean = false,
    val shortcuts: List<Shortcut> = emptyList(),
    val selectedShortcut: Shortcut? = null,
    val streak: Int = 0,
    val sync: SyncState = SyncState(),
    val failedOps: List<FailedOp> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
)

enum class UndoOf { DELETE, ARCHIVE, UNARCHIVE }

/** Something the user just did that can be put back, with what is needed to put it back. */
data class Undoable(val of: UndoOf, val localId: String, val previousState: MemoState? = null)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val memoRepository: MemoRepository,
    private val shortcutRepository: ShortcutRepository,
    private val settingsRepository: AccountSettingsRepository,
    private val preferences: AppPreferences,
) : ViewModel() {
    val unreadNotifications: StateFlow<Int> = settingsRepository.unreadNotifications

    /** Timeline headers the user has folded away; survives restarts. */
    val collapsedGroups: StateFlow<Set<String>> = preferences.settings
        .map { it.collapsedGroups }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Whether the timeline is ordered, grouped and dated by the modified time. */
    val sortByModified: StateFlow<Boolean> = preferences.settings
        .map { it.sortByModified }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun setSortByModified(enabled: Boolean) = viewModelScope.launch {
        preferences.setSortByModified(enabled)
    }

    /** Whether the timeline draws one-line rows rather than cards. */
    val compactList: StateFlow<Boolean> = preferences.settings
        .map { it.compactList }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun toggleCompactList() = viewModelScope.launch {
        preferences.setCompactList(!compactList.value)
    }

    fun toggleGroup(key: String) = viewModelScope.launch {
        preferences.setGroupCollapsed(key, key !in collapsedGroups.value)
    }

    private val query = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)
    private val showArchived = MutableStateFlow(false)
    private val selectedShortcut = MutableStateFlow<Shortcut?>(null)
    /** Server names returned by the active shortcut; null when no shortcut is selected. */
    private val shortcutResults = MutableStateFlow<List<String>?>(null)
    private val _undoable = MutableStateFlow<Undoable?>(null)

    /** The last action that can still be taken back, shown as a snackbar with an Undo button. */
    val undoable: StateFlow<Undoable?> = _undoable

    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val account = accountRepository.activeAccount.filterNotNull()

    private val filters = combine(account, query.debounce(150), selectedTag, showArchived, shortcutResults) { acc, q, tag, archived, names ->
        listOf(acc, q, tag, archived, names)
    }

    private val memos = combine(filters, sortByModified) { v, byModified -> v to byModified }.flatMapLatest { (v, byModified) ->
        val acc = v[0] as Account
        val q = v[1] as String
        val tag = v[2] as String?
        val archived = v[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val names = v[4] as List<String>?
        val base = when {
            names != null -> shortcutRepository.observeMemos(acc.id, names.ifEmpty { listOf("") })
            q.isBlank() -> memoRepository.observeTimeline(acc.id, byModified, if (archived) MemoState.ARCHIVED else MemoState.NORMAL)
            else -> memoRepository.search(acc.id, q)
        }
        base.map { list -> if (tag == null) list else list.filter { tag in it.tags } }
    }
    private val shortcuts = account.flatMapLatest { shortcutRepository.observe(it.id) }
    private val streak = account.flatMapLatest { memoRepository.observeActiveDays(it.id) }.map { days ->
        // Consecutive days ending today, or yesterday if nothing written yet today.
        var day = java.time.LocalDate.now()
        if (day !in days) day = day.minusDays(1)
        var n = 0
        while (day in days) { n++; day = day.minusDays(1) }
        n
    }

    private val tags = account.flatMapLatest { memoRepository.observeTags(it.id) }
    private val sync = account.flatMapLatest { memoRepository.observeSyncState(it.id) }
    private val failed = account.flatMapLatest { memoRepository.observeFailedOps(it.id) }

    val state: StateFlow<TimelineUiState> = combine(
        listOf(accountRepository.activeAccount, memos, tags, query, selectedTag, showArchived, sync, failed, refreshing, message, shortcuts, selectedShortcut, streak),
    ) { v ->
        @Suppress("UNCHECKED_CAST")
        TimelineUiState(
            account = v[0] as Account?,
            memos = v[1] as List<Memo>,
            tags = v[2] as List<String>,
            query = v[3] as String,
            selectedTag = v[4] as String?,
            showArchived = v[5] as Boolean,
            sync = v[6] as SyncState,
            failedOps = v[7] as List<FailedOp>,
            refreshing = v[8] as Boolean,
            message = v[9] as String?,
            shortcuts = v[10] as List<Shortcut>,
            selectedShortcut = v[11] as Shortcut?,
            streak = v[12] as Int,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    init {
        refresh()
        viewModelScope.launch { settingsRepository.refreshUnreadCount(account.first()) }
    }

    fun onQuery(value: String) { query.value = value }
    fun onTag(tag: String?) { selectedTag.value = if (selectedTag.value == tag) null else tag }
    fun toggleArchived() { showArchived.value = !showArchived.value }

    fun onShortcut(shortcut: Shortcut?) {
        if (shortcut == null || selectedShortcut.value?.name == shortcut.name) {
            selectedShortcut.value = null
            shortcutResults.value = null
            return
        }
        selectedShortcut.value = shortcut
        viewModelScope.launch {
            refreshing.value = true
            runCatching { shortcutRepository.run(account.first(), shortcut) }
                .onSuccess { shortcutResults.value = it }
                .onFailure {
                    message.value = "Shortcuts run on the server. Connect to use them."
                    selectedShortcut.value = null
                    shortcutResults.value = null
                }
            refreshing.value = false
        }
    }
    fun dismissMessage() { message.value = null }

    fun refresh(full: Boolean = false) {
        viewModelScope.launch {
            val acc = account.first()
            refreshing.value = true
            when (val outcome = memoRepository.syncNow(acc.id, full)) {
                is SyncEngine.Outcome.Retry -> message.value = outcome.cause.message ?: "Sync failed"
                is SyncEngine.Outcome.AuthFailed -> message.value = "Session expired. Sign in again."
                SyncEngine.Outcome.Success -> Unit
            }
            refreshing.value = false
        }
    }

    fun retryFailed() = viewModelScope.launch { memoRepository.retryFailed(account.first().id) }

    fun togglePin(memo: Memo) = viewModelScope.launch { memoRepository.setPinned(memo.localId, !memo.pinned) }

    fun toggleArchive(memo: Memo) = viewModelScope.launch {
        val wasArchived = memo.state == MemoState.ARCHIVED
        memoRepository.setState(memo.localId, if (wasArchived) MemoState.NORMAL else MemoState.ARCHIVED)
        _undoable.value = Undoable(if (wasArchived) UndoOf.UNARCHIVE else UndoOf.ARCHIVE, memo.localId, memo.state)
    }

    fun delete(memo: Memo) = viewModelScope.launch {
        // A memo that never reached the server is gone for good, so no Undo is offered for it.
        val canUndo = memoRepository.delete(memo.localId)
        _undoable.value = if (canUndo) Undoable(UndoOf.DELETE, memo.localId) else null
    }

    fun undo() = viewModelScope.launch {
        val action = _undoable.value ?: return@launch
        _undoable.value = null
        when (action.of) {
            UndoOf.DELETE -> if (!memoRepository.undoDelete(action.localId)) {
                message.value = "That memo had already gone to the server."
            }
            UndoOf.ARCHIVE, UndoOf.UNARCHIVE -> action.previousState?.let { memoRepository.setState(action.localId, it) }
        }
    }

    fun dismissUndo() { _undoable.value = null }

    fun setColour(memo: Memo, colour: NoteColour?) = viewModelScope.launch { memoRepository.setColour(memo.localId, colour) }

    fun toggleTask(memo: Memo, lineIndex: Int, checked: Boolean) = viewModelScope.launch {
        toggleTaskLine(memo.content, lineIndex, checked)?.let { memoRepository.updateContent(memo.localId, it) }
    }

    fun reauthenticate(password: String) = viewModelScope.launch {
        val acc = account.first()
        runCatching { accountRepository.reauthenticate(acc, password) }
            .onSuccess { refresh() }
            .onFailure { message.value = it.message ?: "Sign-in failed" }
    }

    fun signOut() {
        viewModelScope.launch {
            accountRepository.activeAccountOrNull()?.let { accountRepository.signOut(it) }
        }
    }
}
