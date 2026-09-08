package com.keltruc.mymemos.ui.data

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.keltruc.mymemos.backup.BackupManager
import com.keltruc.mymemos.data.export.BackupCrypto
import com.keltruc.mymemos.data.export.MarkdownExporter
import com.keltruc.mymemos.data.imports.MarkdownImporter
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.model.Visibility
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
    private val importer: MarkdownImporter,
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

    fun importMarkdown(sources: List<Uri>) = run {
        val account = accountRepository.activeAccount.filterNotNull().first()
        // Private unless the file's own front matter says otherwise: an import that quietly
        // published someone's notes would be a much worse surprise than one that did not.
        val result = importer.import(account.id, sources, Visibility.PRIVATE)
        buildString {
            append("Imported ${result.imported} memo${if (result.imported == 1) "" else "s"}")
            if (result.duplicates > 0) append(", ${result.duplicates} already here")
            if (result.skipped > 0) append(", ${result.skipped} had no Markdown in them")
            if (result.failures.isNotEmpty()) append(". ${result.failures.size} failed: ${result.failures.first()}")
        }
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
