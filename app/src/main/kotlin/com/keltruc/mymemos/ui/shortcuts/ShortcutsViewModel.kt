package com.keltruc.mymemos.ui.shortcuts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.ShortcutRepository
import com.keltruc.mymemos.model.Shortcut
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ShortcutsViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val shortcutRepository: ShortcutRepository,
) : ViewModel() {
    private val account = accountRepository.activeAccount.filterNotNull()
    val shortcuts: StateFlow<List<Shortcut>> = account.flatMapLatest { shortcutRepository.observe(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val error = MutableStateFlow<String?>(null)

    fun save(existing: Shortcut?, title: String, filter: String) = viewModelScope.launch {
        val acc = account.first()
        runCatching {
            if (existing == null) shortcutRepository.create(acc, title, filter) else shortcutRepository.update(acc, existing.copy(title = title, filter = filter))
        }.onFailure { error.value = it.message ?: "Could not save shortcut" }
    }

    fun delete(shortcut: Shortcut) = viewModelScope.launch {
        runCatching { shortcutRepository.delete(account.first(), shortcut) }
            .onFailure { error.value = it.message ?: "Could not delete shortcut" }
    }
}
