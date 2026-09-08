package com.keltruc.mymemos.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.ui.components.AttachmentStrip
import com.keltruc.mymemos.ui.components.MemoContent
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailScreen(
    localId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: MemoDetailViewModel = hiltViewModel(),
) {
    val memo by viewModel.memo.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    val m = memo ?: return@TopAppBar
                    IconButton(onClick = viewModel::togglePin) { Icon(Icons.Default.PushPin, stringResource(R.string.pin)) }
                    IconButton(onClick = viewModel::toggleArchive) {
                        Icon(if (m.state == MemoState.ARCHIVED) Icons.Default.Unarchive else Icons.Default.Archive, stringResource(R.string.archive))
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.edit_memo)) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                },
            )
        },
    ) { padding ->
        val m = memo ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (m.syncStatus == SyncStatus.CONFLICT) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.conflict_banner), style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::keepConflictCopy) { Text(stringResource(R.string.conflict_keep)) }
                    }
                }
            }
            Text(
                formatter.format(m.createTime.atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MemoContent(content = m.content, onToggleTask = viewModel::toggleTask, modifier = Modifier.fillMaxWidth())
            if (m.attachments.isNotEmpty()) {
                Text(stringResource(R.string.attachments), style = MaterialTheme.typography.titleSmall)
                AttachmentStrip(attachments = m.attachments, serverUrl = serverUrl, thumbSize = 140)
            }
            m.location?.let {
                Text("Location: ${it.placeholder.ifEmpty { "${it.latitude}, ${it.longitude}" }}")
            }
            Text(
                "${m.visibility.name.lowercase().replaceFirstChar(Char::uppercase)} · ${m.remoteName ?: "not yet synced"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; viewModel.delete(); onBack() }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
