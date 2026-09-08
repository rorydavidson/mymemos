package com.keltruc.mymemos.ui.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.config.ConfigRepository
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.model.NoteColour
import com.keltruc.mymemos.model.TagStyle
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TagRow(val tag: String, val style: TagStyle?)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TagsViewModel @Inject constructor(
    accountRepository: AccountRepository,
    memoRepository: MemoRepository,
    private val configRepository: ConfigRepository,
) : ViewModel() {
    val tags: StateFlow<List<TagRow>> = combine(
        accountRepository.activeAccount.filterNotNull().flatMapLatest { memoRepository.observeTags(it.id) },
        configRepository.config.map { it.tagStyles },
    ) { tags, styles ->
        (tags + styles.keys).distinct().sorted().map { TagRow(it, styles[it]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStyle(tag: String, emoji: String?, colour: NoteColour?) = viewModelScope.launch {
        configRepository.setTagStyle(tag, TagStyle(emoji?.takeIf { it.isNotBlank() }, colour))
    }
}
