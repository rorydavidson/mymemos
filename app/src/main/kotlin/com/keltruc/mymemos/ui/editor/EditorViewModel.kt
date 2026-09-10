package com.keltruc.mymemos.ui.editor

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.keltruc.mymemos.data.attachments.FileAttachmentStore
import com.keltruc.mymemos.data.crypto.MemoCipher
import com.keltruc.mymemos.data.crypto.PasswordSession
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.JavaTimeTemplateValues
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.repository.TemplateRepository
import com.keltruc.mymemos.model.Template
import com.keltruc.mymemos.model.Attachment
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.navigation.EditorRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EditorUiState(
    val content: String = "",
    val visibility: Visibility = Visibility.PRIVATE,
    val pinned: Boolean = false,
    val attachments: List<Attachment> = emptyList(),
    val serverUrl: String = "",
    val loaded: Boolean = false,
    val saved: Boolean = false,
    val isNew: Boolean = true,
    /** Save encrypts. Set when opening a locked memo or by the toolbar toggle. */
    val locked: Boolean = false,
    /** A locked memo whose password we do not have yet; the editor cannot show it. */
    val needsPassword: Boolean = false,
    val passwordError: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoRepository: MemoRepository,
    private val accountRepository: AccountRepository,
    private val passwordSession: PasswordSession,
    private val attachmentStore: FileAttachmentStore,
    templateRepository: TemplateRepository,
) : ViewModel() {
    val passwordRemembered: Boolean get() = passwordSession.isRemembered
    val templates: StateFlow<List<Template>> = templateRepository.templates
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun expand(template: Template): String =
        TemplateRepository.expand(template.body, JavaTimeTemplateValues())

    private val route = savedStateHandle.toRoute<EditorRoute>()
    private var localId: String? = route.localId

    private val _state = MutableStateFlow(EditorUiState(isNew = route.localId == null))
    val state: StateFlow<EditorUiState> = _state

    /** Tag suggestions for the autocomplete popup, most used first. */
    val tags: StateFlow<List<String>> = accountRepository.activeAccount.filterNotNull()
        .flatMapLatest { memoRepository.observeTags(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val account = accountRepository.activeAccount.filterNotNull().first()
            val id = localId
            if (id == null) {
                _state.update { it.copy(loaded = true, serverUrl = account.serverUrl, content = route.initialText.orEmpty()) }
                // Shared images: the memo has to exist before a file can hang off it.
                route.initialImages.map(Uri::parse).forEach { uri -> attach(uri) }
            } else {
                val memo = memoRepository.observeMemo(id).first()
                val locked = memo?.isLocked == true
                val plain = if (locked) passwordSession.current()?.let { pw -> runCatching { memoRepository.decrypt(memo!!, pw) }.getOrNull() } else memo?.displayContent
                _state.update {
                    it.copy(
                        content = plain.orEmpty(),
                        visibility = memo?.visibility ?: Visibility.PRIVATE,
                        pinned = memo?.pinned ?: false,
                        loaded = plain != null,
                        locked = locked,
                        needsPassword = locked && plain == null,
                        serverUrl = account.serverUrl,
                    )
                }
            }
            // Attachments come live so uploads picked during editing show up at once.
            (if (id == null) flowOf(emptyList()) else memoRepository.observeMemo(id).map { it?.attachments.orEmpty() })
                .collect { list -> _state.update { it.copy(attachments = list) } }
        }
    }

    fun onContent(value: String) = _state.update { it.copy(content = value) }
    fun onLocked(value: Boolean) = _state.update { it.copy(locked = value) }

    /** Password entered for a locked memo: decrypt it into the editor, or record it for saving. */
    fun submitPassword(password: CharArray, remember: Boolean) = viewModelScope.launch {
        val id = localId
        if (id != null && _state.value.needsPassword) {
            val memo = memoRepository.observeMemo(id).first() ?: return@launch
            val plain = try { memoRepository.decrypt(memo, password) } catch (e: MemoCipher.WrongPassword) {
                _state.update { it.copy(passwordError = "Wrong password") }; return@launch
            }
            _state.update { it.copy(content = plain, loaded = true, needsPassword = false, passwordError = null) }
        }
        passwordSession.set(password, remember)
        pendingSave?.let { pendingSave = null; save() }
    }

    fun cancelPassword() { pendingSave = null; _state.update { it.copy(passwordError = null) } }
    private var pendingSave: Boolean? = null
    val askPasswordForSave = MutableStateFlow(false)
    fun onVisibility(value: Visibility) = _state.update { it.copy(visibility = value) }
    fun onPinned(value: Boolean) = _state.update { it.copy(pinned = value) }

    fun attach(uri: Uri) {
        viewModelScope.launch {
            val id = ensureMemoExists()
            memoRepository.addAttachment(id, attachmentStore.stage(uri))
        }
    }

    fun removeAttachment(attachment: Attachment) {
        viewModelScope.launch { memoRepository.removeAttachment(attachment.localId) }
    }

    fun save() {
        val s = _state.value
        if (s.content.isBlank() && s.attachments.isEmpty()) return
        val password = if (s.locked) passwordSession.current() else null
        if (s.locked && password == null) {
            pendingSave = true
            askPasswordForSave.value = true
            return
        }
        askPasswordForSave.value = false
        viewModelScope.launch {
            val id = ensureMemoExists()
            if (password != null) memoRepository.updateLockedContent(id, s.content, password) else memoRepository.updateContent(id, s.content)
            val current = memoRepository.observeMemo(id).first() ?: return@launch
            if (current.visibility != s.visibility) memoRepository.setVisibility(id, s.visibility)
            if (current.pinned != s.pinned) memoRepository.setPinned(id, s.pinned)
            _state.update { it.copy(saved = true) }
        }
    }

    /** Attaching to a brand-new memo needs a row to hang the file on, so create it early. */
    private suspend fun ensureMemoExists(): String {
        localId?.let { return it }
        val account = accountRepository.activeAccount.filterNotNull().first()
        val s = _state.value
        val id = memoRepository.create(account.id, if (s.locked) "" else s.content, s.visibility, s.pinned)
        localId = id
        _state.update { it.copy(isNew = false) }
        return id
    }
}
