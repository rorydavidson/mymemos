package com.keltruc.mymemos.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.navigation.MemoDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MemoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    memoRepository: MemoRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<MemoDetailRoute>()

    val memo: StateFlow<Memo?> = memoRepository.observeMemo(route.localId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
