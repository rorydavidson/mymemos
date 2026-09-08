package com.keltruc.mymemos.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebhooksScreen(onBack: () -> Unit, viewModel: WebhooksViewModel = hiltViewModel()) {
    val state by viewModel.items.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.message.value = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.webhooks)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, stringResource(R.string.webhook_new)) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        RemoteContent(state, onRetry = viewModel::refresh) { hooks ->
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item { Text(stringResource(R.string.webhooks_hint), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(hooks, key = { it.name }) { h ->
                    ListItem(
                        headlineContent = { Text(h.displayName.ifEmpty { h.url }) },
                        supportingContent = { Text(h.url) },
                        trailingContent = { IconButton(onClick = { viewModel.delete(h) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
                    )
                }
            }
        }
    }

    if (showCreate) {
        var name by remember { mutableStateOf("") }
        var url by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.webhook_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.webhook_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = url.startsWith("http"), onClick = { viewModel.create(name.trim(), url.trim()); showCreate = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
