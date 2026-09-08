package com.keltruc.mymemos.ui.review

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.location.LocationProvider
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

data class NearbyMemo(val memo: Memo, val metres: Double)

/** A memo from an earlier month or year on the same calendar day. */
data class Throwback(val memo: Memo, val yearsAgo: Int, val monthsAgo: Int)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReviewViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val memoRepository: MemoRepository,
    private val locationProvider: LocationProvider,
) : ViewModel() {
    private val account = accountRepository.activeAccount.filterNotNull()
    val day = MutableStateFlow(LocalDate.now().minusDays(1))
    val account_: StateFlow<Account?> = accountRepository.activeAccount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dayMemos: StateFlow<List<Memo>> = combine(account, day) { acc, d -> acc to d }
        .flatMapLatest { (acc, d) -> memoRepository.observeCreatedOn(acc.id, d) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val throwbacks: StateFlow<List<Throwback>> = account.flatMapLatest { acc ->
        memoRepository.observeActiveDays(acc.id).flatMapLatest { days ->
            val today = LocalDate.now()
            val matches = days.filter { it != today && it.dayOfMonth == today.dayOfMonth && it.isBefore(today) }.sortedDescending()
            if (matches.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(matches.map { d -> memoRepository.observeCreatedOn(acc.id, d).map { list -> d to list } }) { arrays ->
                    arrays.flatMap { (d, list) ->
                        val years = today.year - d.year
                        val months = years * 12 + (today.monthValue - d.monthValue)
                        list.map { Throwback(it, years, months) }
                    }
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val nearby = MutableStateFlow<List<NearbyMemo>?>(null)
    val nearbyError = MutableStateFlow<String?>(null)

    fun previousDay() { day.value = day.value.minusDays(1) }
    fun nextDay() { if (day.value.isBefore(LocalDate.now())) day.value = day.value.plusDays(1) }

    fun loadNearby() = viewModelScope.launch {
        val acc = account.first()
        val fix = locationProvider.current()
        if (fix == null) { nearbyError.value = "no-fix"; return@launch }
        nearby.value = memoRepository.memosWithLocation(acc.id).mapNotNull { m ->
            val loc = m.location ?: return@mapNotNull null
            NearbyMemo(m, distanceMetres(fix.latitude, fix.longitude, loc.latitude, loc.longitude))
        }.sortedBy { it.metres }
    }

    fun togglePin(memo: Memo) = viewModelScope.launch { memoRepository.setPinned(memo.localId, !memo.pinned) }
    fun archive(memo: Memo) = viewModelScope.launch { memoRepository.setState(memo.localId, MemoState.ARCHIVED) }
    fun delete(memo: Memo) = viewModelScope.launch { memoRepository.delete(memo.localId) }

    private fun distanceMetres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
            Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2)
        return 2 * r * Math.asin(Math.sqrt(a))
    }

    @Suppress("unused") private val zone: ZoneId = ZoneId.systemDefault()
}
