package com.keltruc.mymemos.ui.timeline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Memo
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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TimelineUiState(
    val account: Account? = null,
    val memos: List<Memo> = emptyList(),
    val tags: List<String> = emptyList(),
    val query: String = "",
    val selectedTag: String? = null,
    val refreshing: Boolean = false,
    val error: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class TimelineViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val memoRepository: MemoRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedTag = MutableStateFlow<String?>(null)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val account = accountRepository.activeAccount.filterNotNull()

    private val memos = combine(account, query.debounce(150), selectedTag) { acc, q, tag -> Triple(acc, q, tag) }
        .flatMapLatest { (acc, q, tag) ->
            val base = if (q.isBlank()) memoRepository.observeTimeline(acc.id) else memoRepository.search(acc.id, q)
            if (tag == null) base else combine(base, flowOf(tag)) { list, t -> list.filter { t in it.tags } }
        }

    private val tags = account.flatMapLatest { memoRepository.observeTags(it.id) }

    val state: StateFlow<TimelineUiState> = combine(
        accountRepository.activeAccount, memos, tags, query, selectedTag, refreshing, error,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        TimelineUiState(
            account = values[0] as Account?,
            memos = values[1] as List<Memo>,
            tags = values[2] as List<String>,
            query = values[3] as String,
            selectedTag = values[4] as String?,
            refreshing = values[5] as Boolean,
            error = values[6] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    init {
        refresh()
    }

    fun onQuery(value: String) { query.value = value }
    fun onTag(tag: String?) { selectedTag.value = if (selectedTag.value == tag) null else tag }
    fun dismissError() { error.value = null }

    fun refresh(force: Boolean = false) {
        viewModelScope.launch {
            val acc = account.first()
            refreshing.value = true
            runCatching { memoRepository.refresh(acc, force) }
                .onFailure { error.value = it.message ?: "Refresh failed" }
            refreshing.value = false
        }
    }

    fun signOut() {
        viewModelScope.launch {
            accountRepository.activeAccountOrNull()?.let { accountRepository.signOut(it) }
        }
    }
}
