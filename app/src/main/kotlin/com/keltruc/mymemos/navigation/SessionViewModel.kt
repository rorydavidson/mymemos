package com.keltruc.mymemos.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import kotlinx.coroutines.launch
import com.keltruc.mymemos.model.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val account: Account) : SessionState
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val memoRepository: MemoRepository,
    private val intentRouter: IntentRouter,
) : ViewModel() {
    val pendingDestination: StateFlow<Destination?> = intentRouter.pending
    fun consumeDestination() = intentRouter.consume()

    /** Turns a server memo name into a local id (fetching if needed) and hands it on. */
    fun resolveMemo(remoteName: String, onResolved: (String) -> Unit) = viewModelScope.launch {
        val acc = accountRepository.activeAccountOrNull() ?: return@launch
        memoRepository.ensureLocal(acc, remoteName)?.let(onResolved)
    }

    val state: StateFlow<SessionState> = accountRepository.activeAccount
        .map { account -> if (account == null) SessionState.SignedOut else SessionState.SignedIn(account) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)
}
