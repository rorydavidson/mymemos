package com.keltruc.mymemos.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.TemplateRepository
import com.keltruc.mymemos.model.Template
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(private val repo: TemplateRepository) : ViewModel() {
    val templates: StateFlow<List<Template>> = repo.templates.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun save(id: Long?, title: String, body: String) = viewModelScope.launch { repo.save(id, title, body) }
    fun delete(id: Long) = viewModelScope.launch { repo.delete(id) }
}
