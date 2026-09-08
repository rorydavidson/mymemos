package com.keltruc.mymemos.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.TemplateRepository
import com.keltruc.mymemos.model.Template
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repo: TemplateRepository,
    private val config: com.keltruc.mymemos.data.config.ConfigRepository,
) : ViewModel() {
    val templates: StateFlow<List<Template>> = repo.templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val recurring: StateFlow<List<com.keltruc.mymemos.model.RecurringTemplate>> = config.config.map { it.recurring }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setRecurring(title: String, hour: Int?, minute: Int?) = viewModelScope.launch {
        config.update { c ->
            val rest = c.recurring.filterNot { it.templateTitle == title }
            c.copy(recurring = if (hour == null || minute == null) rest else rest + com.keltruc.mymemos.model.RecurringTemplate(title, hour, minute))
        }
    }
    fun save(id: Long?, title: String, body: String) = viewModelScope.launch { repo.save(id, title, body) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}
