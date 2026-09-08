package com.keltruc.mymemos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.prefs.Settings
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.model.Account
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val accountRepository: AccountRepository,
) : ViewModel() {
    val settings: StateFlow<Settings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val account: StateFlow<Account?> = accountRepository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setSortCompleted(enabled: Boolean) = viewModelScope.launch { preferences.setSortCompletedTasks(enabled) }
    fun setDynamicColour(enabled: Boolean) = viewModelScope.launch { preferences.setDynamicColour(enabled) }
    fun signOut() = viewModelScope.launch { account.value?.let { accountRepository.signOut(it) } }
}
