package com.keltruc.mymemos.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.AccountRepository
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
class SessionViewModel @Inject constructor(accountRepository: AccountRepository) : ViewModel() {
    val state: StateFlow<SessionState> = accountRepository.activeAccount
        .map { account -> if (account == null) SessionState.SignedOut else SessionState.SignedIn(account) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SessionState.Loading)
}
