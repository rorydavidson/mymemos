package com.keltruc.mymemos.ui.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.backup.BackupManager
import com.keltruc.mymemos.data.export.BackupCrypto
import com.keltruc.mymemos.data.export.MarkdownExporter
import com.keltruc.mymemos.data.repository.AccountRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class DataViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountRepository: AccountRepository,
    private val exporter: MarkdownExporter,
    private val backupManager: BackupManager,
) : ViewModel() {
    val busy = MutableStateFlow(false)
    val message = MutableStateFlow<String?>(null)

    fun exportMarkdown(target: Uri) = run {
        val account = accountRepository.activeAccount.filterNotNull().first()
        val count = withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(target, "wt")!!.use { exporter.export(account.id, it) }
        }
        "Exported $count memos"
    }

    fun backup(target: Uri, password: String) = run {
        backupManager.backup(target, password.toCharArray())
        "Backup written"
    }

    fun restore(source: Uri, password: String) = run {
        backupManager.restore(source, password.toCharArray())
        "Restored"
    }

    private fun run(block: suspend () -> String) = viewModelScope.launch {
        busy.value = true
        runCatching { block() }
            .onSuccess { message.value = it }
            .onFailure { message.value = if (it is BackupCrypto.WrongPasswordOrCorrupt) it.message else (it.message ?: "Failed") }
        busy.value = false
    }
}
