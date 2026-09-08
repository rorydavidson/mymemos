package com.keltruc.mymemos.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.repository.ShortcutRepository
import com.keltruc.mymemos.model.Shortcut
import com.keltruc.mymemos.data.sync.FailedOp
import com.keltruc.mymemos.data.sync.SyncEngine
import com.keltruc.mymemos.data.sync.SyncState
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.ui.components.toggleTaskLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
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
    val sync: SyncState = SyncState(),
    val failedOps: List<FailedOp> = emptyList(),
    val refreshing: Boolean = false,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val memoRepository: MemoRepository,
    private val shortcutRepository: ShortcutRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)
    private val showArchived = MutableStateFlow(false)
    private val selectedShortcut = MutableStateFlow<Shortcut?>(null)
    /** Server names returned by the active shortcut; null when no shortcut is selected. */
    private val shortcutResults = MutableStateFlow<List<String>?>(null)
    private val refreshing = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)

    private val account = accountRepository.activeAccount.filterNotNull()

    private val memos = combine(account, query.debounce(150), selectedTag, showArchived, shortcutResults) { acc, q, tag, archived, names ->
        listOf(acc, q, tag, archived, names)
    }.flatMapLatest { v ->
        val acc = v[0] as Account
        val q = v[1] as String
        val tag = v[2] as String?
        val archived = v[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val names = v[4] as List<String>?
        val base = when {
            names != null -> shortcutRepository.observeMemos(acc.id, names.ifEmpty { listOf("") })
            q.isBlank() -> memoRepository.observeTimeline(acc.id, if (archived) MemoState.ARCHIVED else MemoState.NORMAL)
            else -> memoRepository.search(acc.id, q)
        }
        base.map { list -> if (tag == null) list else list.filter { tag in it.tags } }
    }
    private val shortcuts = account.flatMapLatest { shortcutRepository.observe(it.id) }

    private val tags = account.flatMapLatest { memoRepository.observeTags(it.id) }
    private val sync = account.flatMapLatest { memoRepository.observeSyncState(it.id) }
    private val failed = account.flatMapLatest { memoRepository.observeFailedOps(it.id) }

    val state: StateFlow<TimelineUiState> = combine(
        listOf(accountRepository.activeAccount, memos, tags, query, selectedTag, showArchived, sync, failed, refreshing, message, shortcuts, selectedShortcut),
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
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    init {
        refresh()
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
        memoRepository.setState(memo.localId, if (memo.state == MemoState.NORMAL) MemoState.ARCHIVED else MemoState.NORMAL)
    }

    fun delete(memo: Memo) = viewModelScope.launch { memoRepository.delete(memo.localId) }

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
