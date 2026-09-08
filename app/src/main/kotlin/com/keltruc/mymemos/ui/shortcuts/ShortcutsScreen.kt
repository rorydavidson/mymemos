package com.keltruc.mymemos.ui.shortcuts

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
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.Shortcut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortcutsScreen(onBack: () -> Unit, viewModel: ShortcutsViewModel = hiltViewModel()) {
    val shortcuts by viewModel.shortcuts.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Shortcut?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    LaunchedEffect(error) { error?.let { snackbar.showSnackbar(it); viewModel.error.value = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shortcuts)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; showEditor = true }) { Icon(Icons.Default.Add, stringResource(R.string.shortcut_new)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (shortcuts.isEmpty()) {
            Text(
                stringResource(R.string.shortcut_empty),
                Modifier.padding(padding).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(shortcuts, key = { it.name }) { s ->
                ListItem(
                    headlineContent = { Text(s.title) },
                    supportingContent = { Text(s.filter, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) },
                    trailingContent = { IconButton(onClick = { viewModel.delete(s) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) } },
                    modifier = Modifier.clickable { editing = s; showEditor = true },
                )
            }
        }
    }

    if (showEditor) {
        var title by remember { mutableStateOf(editing?.title.orEmpty()) }
        var filter by remember { mutableStateOf(editing?.filter.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(stringResource(if (editing == null) R.string.shortcut_new else R.string.edit_memo)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.shortcut_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        value = filter,
                        onValueChange = { filter = it },
                        label = { Text(stringResource(R.string.shortcut_filter)) },
                        placeholder = { Text(stringResource(R.string.shortcut_filter_hint)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = title.isNotBlank(), onClick = { viewModel.save(editing, title.trim(), filter.trim()); showEditor = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
