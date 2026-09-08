package com.keltruc.mymemos.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
import javax.inject.Inject

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
@HiltViewModel
class MemoDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val memoRepository: MemoRepository,
    private val shareRepository: ShareRepository,
    private val locationProvider: LocationProvider,
    accountRepository: AccountRepository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<MemoDetailRoute>()
    private val account = accountRepository.activeAccount.filterNotNull()
    private val memo = memoRepository.observeMemo(route.localId)
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
        toggleTaskLine(m.content, lineIndex, checked)?.let { memoRepository.updateContent(m.localId, it) }
    }

    fun togglePin() = viewModelScope.launch { state.value.memo?.let { memoRepository.setPinned(it.localId, !it.pinned) } }

    fun toggleArchive() = viewModelScope.launch {
        state.value.memo?.let {
            memoRepository.setState(it.localId, if (it.state == MemoState.NORMAL) MemoState.ARCHIVED else MemoState.NORMAL)
        }
    }

    fun delete() = viewModelScope.launch { state.value.memo?.let { memoRepository.delete(it.localId) } }

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
        android.util.Log.i("MemoDetail", "location fix: ${loc.latitude},${loc.longitude} '${loc.placeholder}'")
        memoRepository.setLocation(route.localId, loc)
    }

    fun clearLocation() = viewModelScope.launch { memoRepository.setLocation(route.localId, null) }
}
