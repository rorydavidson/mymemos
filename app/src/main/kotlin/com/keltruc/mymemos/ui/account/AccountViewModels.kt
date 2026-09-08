package com.keltruc.mymemos.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.AccountSettingsRepository
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Notification
import com.keltruc.mymemos.model.PersonalAccessToken
import com.keltruc.mymemos.model.UserStats
import com.keltruc.mymemos.model.Webhook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Shared shape for the small server-backed list screens. */
abstract class RemoteListViewModel<T>(private val accountRepository: AccountRepository) : ViewModel() {
    protected val state = MutableStateFlow<Remote<T>>(Remote.Loading)
    val items: StateFlow<Remote<T>> = state
    val message = MutableStateFlow<String?>(null)

    protected suspend fun account(): Account = accountRepository.activeAccount.filterNotNull().first()

    protected abstract suspend fun load(account: Account): T

    fun refresh() = viewModelScope.launch {
        state.value = Remote.Loading
        runCatching { load(account()) }
            .onSuccess { state.value = Remote.Ready(it) }
            .onFailure { state.value = Remote.Failed(it.message ?: "Could not load") }
    }

    protected fun act(block: suspend (Account) -> Unit) = viewModelScope.launch {
        runCatching { block(account()) }
            .onSuccess { refresh() }
            .onFailure { message.value = it.message ?: "Something went wrong" }
    }

    init {
        refresh()
    }
}

@HiltViewModel
class TokensViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val repo: AccountSettingsRepository,
) : RemoteListViewModel<TokensViewModel.Data>(accountRepository) {
    data class Data(val tokens: List<PersonalAccessToken>, val ownTokenName: String?)

    /** A freshly created token, shown once. */
    val created = MutableStateFlow<String?>(null)

    override suspend fun load(account: Account) = Data(repo.tokens(account), repo.ownTokenName(account))
    fun create(description: String, expiresInDays: Int?) = act { created.value = repo.createToken(it, description, expiresInDays) }
    fun revoke(token: PersonalAccessToken) = act { repo.deleteToken(it, token) }
}

@HiltViewModel
class WebhooksViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val repo: AccountSettingsRepository,
) : RemoteListViewModel<List<Webhook>>(accountRepository) {
    override suspend fun load(account: Account) = repo.webhooks(account)
    fun create(displayName: String, url: String) = act { repo.createWebhook(it, displayName, url) }
    fun delete(webhook: Webhook) = act { repo.deleteWebhook(it, webhook) }
}

@HiltViewModel
class NotificationsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val repo: AccountSettingsRepository,
) : RemoteListViewModel<List<Notification>>(accountRepository) {
    override suspend fun load(account: Account) = repo.notifications(account)
    fun markRead(n: Notification) = act { repo.markRead(it, n) }
    fun delete(n: Notification) = act { repo.deleteNotification(it, n) }
}

@HiltViewModel
class StatsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val repo: AccountSettingsRepository,
) : RemoteListViewModel<UserStats>(accountRepository) {
    override suspend fun load(account: Account) = repo.stats(account)
}
