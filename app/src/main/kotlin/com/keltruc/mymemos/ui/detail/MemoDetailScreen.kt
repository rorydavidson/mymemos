package com.keltruc.mymemos.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoDetailScreen(
    localId: String,
    onBack: () -> Unit,
    viewModel: MemoDetailViewModel = hiltViewModel(),
) {
    val memo by viewModel.memo.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
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
            Text(
                formatter.format(m.createTime.atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SelectionContainer {
                Text(m.content, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth())
            }
            if (m.tags.isNotEmpty()) {
                Text(m.tags.joinToString(" ") { "#$it" }, color = MaterialTheme.colorScheme.primary)
            }
            if (m.attachments.isNotEmpty()) {
                Text("Attachments", style = MaterialTheme.typography.titleSmall)
                m.attachments.forEach { Text("• ${it.filename}", style = MaterialTheme.typography.bodyMedium) }
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
}
