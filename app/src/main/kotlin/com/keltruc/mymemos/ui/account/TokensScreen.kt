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
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.PersonalAccessToken
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TokensScreen(onBack: () -> Unit, viewModel: TokensViewModel = hiltViewModel()) {
    val state by viewModel.items.collectAsStateWithLifecycle()
    val created by viewModel.created.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showCreate by remember { mutableStateOf(false) }
    var revoking by remember { mutableStateOf<PersonalAccessToken?>(null) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.message.value = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.access_tokens)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, stringResource(R.string.token_new)) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        RemoteContent(state, onRetry = viewModel::refresh) { data ->
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                item { Text(stringResource(R.string.access_tokens_hint), Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                items(data.tokens, key = { it.name }) { t ->
                    val mine = t.name == data.ownTokenName
                    ListItem(
                        headlineContent = { Text(t.description.ifEmpty { t.name.substringAfterLast('/') } + if (mine) " · ${stringResource(R.string.token_this_device)}" else "") },
                        supportingContent = {
                            Text(
                                "Created ${fmt.format(t.createdAt.atZone(ZoneId.systemDefault()))}" +
                                    (t.expiresAt?.let { " · expires ${fmt.format(it.atZone(ZoneId.systemDefault()))}" } ?: " · never expires") +
                                    (t.lastUsedAt?.let { " · used ${fmt.format(it.atZone(ZoneId.systemDefault()))}" } ?: ""),
                            )
                        },
                        trailingContent = { IconButton(onClick = { revoking = t }) { Icon(Icons.Default.Delete, stringResource(R.string.share_revoke)) } },
                    )
                }
            }
        }
    }

    if (showCreate) {
        var description by remember { mutableStateOf("") }
        var days by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.token_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.token_description)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = days, onValueChange = { days = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.token_expires)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(enabled = description.isNotBlank(), onClick = { viewModel.create(description.trim(), days.toIntOrNull()); showCreate = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    created?.let { token ->
        AlertDialog(
            onDismissRequest = { viewModel.created.value = null },
            title = { Text(stringResource(R.string.token_created_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.token_created_hint))
                    SelectionContainer { Text(token, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        .setPrimaryClip(android.content.ClipData.newPlainText("Memos token", token))
                    viewModel.created.value = null
                }) { Text(stringResource(R.string.share_copy)) }
            },
        )
    }

    revoking?.let { t ->
        AlertDialog(
            onDismissRequest = { revoking = null },
            title = { Text(stringResource(R.string.share_revoke)) },
            text = { Text(stringResource(R.string.token_revoke_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.revoke(t); revoking = null }) { Text(stringResource(R.string.share_revoke)) } },
            dismissButton = { TextButton(onClick = { revoking = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
