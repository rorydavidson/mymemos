package com.keltruc.mymemos.ui.signin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInMode { PASSWORD, TOKEN }

data class SignInUiState(
    val serverUrl: String = "",
    val username: String = "",
    val password: String = "",
    val token: String = "",
    val mode: SignInMode = SignInMode.PASSWORD,
    val serverVersion: String? = null,
    val probing: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
) {
    val canSubmit: Boolean
        get() = serverUrl.isNotBlank() && !submitting && when (mode) {
            SignInMode.PASSWORD -> username.isNotBlank() && password.isNotBlank()
            SignInMode.TOKEN -> token.isNotBlank()
        }
}

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state

    fun onServerUrl(value: String) = _state.update { it.copy(serverUrl = value, serverVersion = null, error = null) }
    fun onUsername(value: String) = _state.update { it.copy(username = value, error = null) }
    fun onPassword(value: String) = _state.update { it.copy(password = value, error = null) }
    fun onToken(value: String) = _state.update { it.copy(token = value, error = null) }
    fun onMode(mode: SignInMode) = _state.update { it.copy(mode = mode, error = null) }

    fun probeServer() {
        val url = _state.value.serverUrl
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(probing = true) }
            runCatching { accountRepository.probeServer(url) }
                .onSuccess { profile -> _state.update { it.copy(probing = false, serverVersion = profile.version) } }
                .onFailure { e -> _state.update { it.copy(probing = false, error = e.message ?: "Could not reach server") } }
        }
    }

    fun submit() {
        val s = _state.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, error = null) }
            runCatching {
                when (s.mode) {
                    SignInMode.PASSWORD -> accountRepository.signInWithPassword(s.serverUrl, s.username, s.password)
                    SignInMode.TOKEN -> accountRepository.signInWithToken(s.serverUrl, s.token)
                }
            }.onFailure { e ->
                _state.update { it.copy(submitting = false, error = e.message ?: "Sign-in failed") }
            }
            // On success the active account flow flips the nav host; nothing else to do here.
        }
    }
}
