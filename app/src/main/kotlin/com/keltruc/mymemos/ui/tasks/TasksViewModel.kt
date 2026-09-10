package com.keltruc.mymemos.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.text.DueDateParser
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.ui.components.toggleTaskLine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.time.Clock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import javax.inject.Inject
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

data class OpenTask(val memo: Memo, val lineIndex: Int, val text: String, val due: LocalDate?)

data class TaskGroup(val memo: Memo, val tasks: List<OpenTask>) {
    val earliestDue: LocalDate? get() = tasks.mapNotNull { it.due }.minOrNull()
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TasksViewModel @Inject constructor(
    accountRepository: AccountRepository,
    private val memoRepository: MemoRepository,
) : ViewModel() {
    private val taskLine = Regex("^(\\s*)(?:[-*+]|\\d+[.)]) \\[ ] (.*)$")

    val groups: StateFlow<List<TaskGroup>> = accountRepository.activeAccount.filterNotNull()
        .flatMapLatest { memoRepository.observeMemosWithOpenTasks(it.id) }
        .map { memos ->
            val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
            memos.filter { !it.isLocked }.map { memo ->
                val tasks = memo.displayContent.lines().mapIndexedNotNull { i, line ->
                    taskLine.matchEntire(line)?.let { m ->
                        val text = m.groupValues[2]
                        OpenTask(memo, i, text, DueDateParser.parse(text, today)?.date)
                    }
                }
                TaskGroup(memo, tasks)
            }.filter { it.tasks.isNotEmpty() }
                .sortedWith(compareBy<TaskGroup> { it.earliestDue == null }.thenBy { it.earliestDue }.thenByDescending { it.memo.updateTime })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun complete(task: OpenTask) = viewModelScope.launch {
        toggleTaskLine(task.memo.content, task.lineIndex, true)?.let { memoRepository.updateContent(task.memo.localId, it) }
    }
}
