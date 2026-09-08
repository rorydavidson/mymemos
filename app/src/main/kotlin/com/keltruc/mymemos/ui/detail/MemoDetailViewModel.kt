package com.keltruc.mymemos.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.crypto.MemoCipher
import com.keltruc.mymemos.data.crypto.PasswordSession
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.repository.ShareRepository
import com.keltruc.mymemos.location.LocationProvider
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoShare
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.Reaction
import com.keltruc.mymemos.model.Reference
import com.keltruc.mymemos.navigation.MemoDetailRoute
import com.keltruc.mymemos.ui.components.toggleTaskLine
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DetailUiState(
    val memo: Memo? = null,
    val account: Account? = null,
    val comments: List<Memo> = emptyList(),
    val reactions: List<Reaction> = emptyList(),
    val references: List<Reference> = emptyList(),
    val backlinks: List<Memo> = emptyList(),
    val shares: List<MemoShare>? = null,
    val message: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = MemoDetailViewModel.Factory::class)
class MemoDetailViewModel @AssistedInject constructor(
    @Assisted private val localId: String,
    private val memoRepository: MemoRepository,
    private val shareRepository: ShareRepository,
    private val locationProvider: LocationProvider,
    private val passwordSession: PasswordSession,
    private val configRepository: com.keltruc.mymemos.data.config.ConfigRepository,
    private val alarmScheduler: com.keltruc.mymemos.notify.AlarmScheduler,
    accountRepository: AccountRepository,
    preferences: AppPreferences,
) : ViewModel() {
    /** Which date the date at the top of the screen shows, matching the timeline's order. */
    val sortByModified: StateFlow<Boolean> = preferences.settings
        .map { it.sortByModified }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Set after adding a reminder when Android will only deliver it approximately. */
    val exactAlarmHint = MutableStateFlow(false)

    fun addReminder(at: java.time.Instant) = viewModelScope.launch {
        val name = state.value.memo?.remoteName ?: run { message.value = "Sync this memo before setting a reminder."; return@launch }
        configRepository.update { c ->
            c.copy(reminders = c.reminders + com.keltruc.mymemos.model.Reminder(java.util.UUID.randomUUID().toString(), name, at.toEpochMilli()))
        }
        if (!alarmScheduler.canScheduleExact()) exactAlarmHint.value = true
    }

    fun removeReminder(id: String) = viewModelScope.launch {
        configRepository.update { c -> c.copy(reminders = c.reminders.filterNot { it.id == id }) }
    }
    /** Plain text of a locked memo once the password is known; null while locked. */
    val unlockedText = MutableStateFlow<String?>(null)
    val passwordError = MutableStateFlow<String?>(null)
    val askPassword = MutableStateFlow<PasswordPurpose?>(null)
    enum class PasswordPurpose { UNLOCK_VIEW, LOCK, REMOVE_LOCK }
    val passwordRemembered: Boolean get() = passwordSession.isRemembered

    /** Try the session password silently whenever the memo (re)loads as locked. */
    private fun tryAutoUnlock(memo: Memo) {
        if (!memo.isLocked) { unlockedText.value = null; return }
        val pw = passwordSession.current() ?: run { if (unlockedText.value == null) askPassword.value = PasswordPurpose.UNLOCK_VIEW; return }
        unlockedText.value = runCatching { memoRepository.decrypt(memo, pw) }.getOrNull()
        if (unlockedText.value == null) askPassword.value = PasswordPurpose.UNLOCK_VIEW
    }

    fun submitPassword(password: CharArray, remember: Boolean) = viewModelScope.launch {
        val memo = state.value.memo ?: return@launch
        val purpose = askPassword.value ?: return@launch
        try {
            when (purpose) {
                PasswordPurpose.UNLOCK_VIEW -> unlockedText.value = memoRepository.decrypt(memo, password)
                PasswordPurpose.LOCK -> memoRepository.lock(memo.localId, password)
                PasswordPurpose.REMOVE_LOCK -> { memoRepository.unlock(memo.localId, password); unlockedText.value = null }
            }
            passwordSession.set(password, remember)
            passwordError.value = null
            askPassword.value = null
        } catch (e: MemoCipher.WrongPassword) {
            passwordError.value = "Wrong password"
        }
    }

    fun requestLock() { askPassword.value = PasswordPurpose.LOCK }
    fun requestRemoveLock() { askPassword.value = PasswordPurpose.REMOVE_LOCK }
    fun dismissPassword() { askPassword.value = null; passwordError.value = null }

    /** Lock using the remembered password without asking, when there is one. */
    fun lockNow() = viewModelScope.launch {
        val memo = state.value.memo ?: return@launch
        val pw = passwordSession.current() ?: run { requestLock(); return@launch }
        memoRepository.lock(memo.localId, pw)
    }
    @AssistedFactory
    interface Factory {
        fun create(localId: String): MemoDetailViewModel
    }

    private val route = MemoDetailRoute(localId)
    private val account = accountRepository.activeAccount.filterNotNull()
    private val memo = memoRepository.observeMemo(route.localId)
    /** Reminders for this memo, from the config memo. */
    val reminders: StateFlow<List<com.keltruc.mymemos.model.Reminder>> = combine(memo, configRepository.config) { m, c ->
        c.reminders.filter { it.memoRemoteName == m?.remoteName }.sortedBy { it.atEpochMs }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val shares = MutableStateFlow<List<MemoShare>?>(null)
    private val message = MutableStateFlow<String?>(null)

    private val comments = combine(account, memo) { acc, m -> acc to m?.remoteName }
        .flatMapLatest { (acc, name) -> if (name == null) flowOf(emptyList()) else memoRepository.observeComments(acc.id, name) }
    /** References with the target's text filled in from Room when the server snippet is blank. */
    private val references = combine(account, memoRepository.observeReferences(route.localId)) { acc, refs -> acc to refs }
        .flatMapLatest { (acc, refs) ->
            val names = refs.map { it.relatedRemoteName }
            if (names.isEmpty()) {
                flowOf(refs)
            } else {
                memoRepository.observeByRemoteNames(acc.id, names).map { targets ->
                    val byName = targets.associateBy { it.remoteName }
                    targetIds = targets.mapNotNull { t -> t.remoteName?.let { it to t.localId } }.toMap()
                    refs.map { ref ->
                        val target = byName[ref.relatedRemoteName]
                        if (ref.relatedSnippet.isNotBlank() || target == null) ref
                        else ref.copy(relatedSnippet = target.content.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty())
                    }
                }
            }
        }
    private val backlinks = combine(account, memo) { acc, m -> acc to m?.remoteName }
        .flatMapLatest { (acc, name) -> if (name == null) flowOf(emptyList()) else memoRepository.observeBacklinks(acc.id, name) }

    val state: StateFlow<DetailUiState> = combine(
        listOf(
            memo, account, comments, memoRepository.observeReactions(route.localId),
            references, backlinks, shares, message,
        ),
    ) { v ->
        @Suppress("UNCHECKED_CAST")
        DetailUiState(
            memo = v[0] as Memo?,
            account = v[1] as Account?,
            comments = v[2] as List<Memo>,
            reactions = v[3] as List<Reaction>,
            references = v[4] as List<Reference>,
            backlinks = v[5] as List<Memo>,
            shares = v[6] as List<MemoShare>?,
            message = v[7] as String?,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        viewModelScope.launch { memo.filterNotNull().collect { tryAutoUnlock(it) } }
        // Comments are not in the list pull, so fetch them for this memo when opened.
        viewModelScope.launch {
            val acc = account.first()
            val name = memo.first()?.remoteName ?: return@launch
            runCatching { memoRepository.refreshComments(acc, name) }
        }
    }

    fun dismissMessage() = message.update { null }

    fun toggleTask(lineIndex: Int, checked: Boolean) = viewModelScope.launch {
        val m = state.value.memo ?: return@launch
        if (m.isLocked) {
            val plain = unlockedText.value ?: return@launch
            val pw = passwordSession.current() ?: return@launch
            toggleTaskLine(plain, lineIndex, checked)?.let { memoRepository.updateLockedContent(m.localId, it, pw) }
        } else {
            toggleTaskLine(m.content, lineIndex, checked)?.let { memoRepository.updateContent(m.localId, it) }
        }
    }

    fun togglePin() = viewModelScope.launch { state.value.memo?.let { memoRepository.setPinned(it.localId, !it.pinned) } }

    fun toggleArchive() = viewModelScope.launch {
        state.value.memo?.let {
            memoRepository.setState(it.localId, if (it.state == MemoState.NORMAL) MemoState.ARCHIVED else MemoState.NORMAL)
        }
    }

    fun delete() = viewModelScope.launch { state.value.memo?.let { memoRepository.delete(it.localId) } }

    fun setColour(colour: com.keltruc.mymemos.model.NoteColour?) = viewModelScope.launch { memoRepository.setColour(route.localId, colour) }

    fun keepConflictCopy() = viewModelScope.launch { state.value.memo?.let { memoRepository.resolveConflict(it.localId) } }

    fun addComment(text: String) = viewModelScope.launch {
        val s = state.value
        val parent = s.memo?.remoteName ?: run { message.value = "Sync this memo before commenting."; return@launch }
        val acc = s.account ?: return@launch
        memoRepository.addComment(acc.id, parent, text.trim(), s.memo.visibility)
    }

    fun react(reactionType: String) = viewModelScope.launch {
        val s = state.value
        val acc = s.account ?: return@launch
        memoRepository.toggleReaction(route.localId, acc.userResourceName, reactionType)
    }

    suspend fun referenceCandidates(query: String): List<Memo> {
        val acc = state.value.account ?: return emptyList()
        return memoRepository.pickReferenceCandidates(acc.id, query).filter { it.localId != route.localId }
    }

    /** Local id of a reference target, if we hold it, so the row can navigate. */
    fun openReference(ref: Reference): String? = targetIds[ref.relatedRemoteName]
    private var targetIds: Map<String, String> = emptyMap()

    fun addReference(target: Memo) = viewModelScope.launch { memoRepository.addReference(route.localId, target) }
    fun removeReference(ref: Reference) = viewModelScope.launch { memoRepository.removeReference(route.localId, ref.relatedRemoteName) }

    fun loadShares() = viewModelScope.launch {
        val s = state.value
        val name = s.memo?.remoteName ?: run { message.value = "Sync this memo before sharing."; return@launch }
        val acc = s.account ?: return@launch
        runCatching { shareRepository.list(acc, name) }
            .onSuccess { shares.value = it }
            .onFailure { message.value = it.message ?: "Could not load links" }
    }

    fun createShare() = viewModelScope.launch {
        val s = state.value
        val name = s.memo?.remoteName ?: return@launch
        val acc = s.account ?: return@launch
        runCatching { shareRepository.create(acc, name, null) }
            .onSuccess { shares.update { list -> (list.orEmpty() + it) } }
            .onFailure { message.value = it.message ?: "Could not create link" }
    }

    fun revokeShare(share: MemoShare) = viewModelScope.launch {
        val acc = state.value.account ?: return@launch
        runCatching { shareRepository.delete(acc, share) }
            .onSuccess { shares.update { list -> list?.filterNot { it.name == share.name } } }
            .onFailure { message.value = it.message ?: "Could not revoke link" }
    }

    fun closeShares() = shares.update { null }

    fun captureLocation() = viewModelScope.launch {
        val loc = locationProvider.current() ?: run { message.value = "Could not get a location fix."; return@launch }
        memoRepository.setLocation(route.localId, loc)
    }

    fun clearLocation() = viewModelScope.launch { memoRepository.setLocation(route.localId, null) }
}
