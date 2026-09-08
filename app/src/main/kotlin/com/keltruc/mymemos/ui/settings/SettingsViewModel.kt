package com.keltruc.mymemos.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.prefs.Settings
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.AccountSettingsRepository
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.User
import com.keltruc.mymemos.model.UserPreferences
import com.keltruc.mymemos.model.Visibility
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: AppPreferences,
    private val accountRepository: AccountRepository,
    private val settingsRepository: AccountSettingsRepository,
) : ViewModel() {
    val settings: StateFlow<Settings> = preferences.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())
    val account: StateFlow<Account?> = accountRepository.activeAccount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Server-side bits, loaded best-effort; null while offline or loading. */
    val profile = MutableStateFlow<User?>(null)
    val serverPrefs = MutableStateFlow<UserPreferences?>(null)
    val message = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            val acc = accountRepository.activeAccount.filterNotNull().first()
            runCatching { settingsRepository.profile(acc) }.onSuccess { profile.value = it }
            runCatching { settingsRepository.preferences(acc) }.onSuccess { serverPrefs.value = it }
        }
    }

    fun setSortCompleted(enabled: Boolean) = viewModelScope.launch { preferences.setSortCompletedTasks(enabled) }
    fun setDynamicColour(enabled: Boolean) = viewModelScope.launch { preferences.setDynamicColour(enabled) }

    fun saveProfile(displayName: String, description: String, email: String) = viewModelScope.launch {
        val acc = account.value ?: return@launch
        runCatching { settingsRepository.updateProfile(acc, displayName, description, email) }
            .onSuccess { profile.value = it }
            .onFailure { message.value = it.message ?: "Could not save profile" }
    }

    fun changePassword(newPassword: String) = viewModelScope.launch {
        val acc = account.value ?: return@launch
        runCatching { settingsRepository.changePassword(acc, newPassword) }
            .onSuccess { message.value = "Password changed" }
            .onFailure { message.value = it.message ?: "Could not change password" }
    }

    fun setDefaultVisibility(visibility: Visibility) = viewModelScope.launch {
        val acc = account.value ?: return@launch
        runCatching { settingsRepository.setDefaultVisibility(acc, visibility) }
            .onSuccess { serverPrefs.value = serverPrefs.value?.copy(defaultVisibility = visibility) ?: UserPreferences("", visibility) }
            .onFailure { message.value = it.message ?: "Could not save" }
    }

    fun signOut() = viewModelScope.launch { account.value?.let { accountRepository.signOut(it) } }
}
