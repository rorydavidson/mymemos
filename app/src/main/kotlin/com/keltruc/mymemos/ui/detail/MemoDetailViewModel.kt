package com.keltruc.mymemos.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.navigation.MemoDetailRoute
import com.keltruc.mymemos.ui.components.toggleTaskLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MemoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoRepository: MemoRepository,
    accountRepository: AccountRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<MemoDetailRoute>()

    val memo: StateFlow<Memo?> = memoRepository.observeMemo(route.localId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val serverUrl: StateFlow<String> = accountRepository.activeAccount.map { it?.serverUrl.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    fun toggleTask(lineIndex: Int, checked: Boolean) = viewModelScope.launch {
        val m = memo.value ?: return@launch
        toggleTaskLine(m.content, lineIndex, checked)?.let { memoRepository.updateContent(m.localId, it) }
    }

    fun togglePin() = viewModelScope.launch { memo.value?.let { memoRepository.setPinned(it.localId, !it.pinned) } }

    fun toggleArchive() = viewModelScope.launch {
        memo.value?.let {
            memoRepository.setState(it.localId, if (it.state == MemoState.NORMAL) MemoState.ARCHIVED else MemoState.NORMAL)
        }
    }

    fun delete() = viewModelScope.launch { memo.value?.let { memoRepository.delete(it.localId) } }

    fun keepConflictCopy() = viewModelScope.launch { memo.value?.let { memoRepository.resolveConflict(it.localId) } }
}
