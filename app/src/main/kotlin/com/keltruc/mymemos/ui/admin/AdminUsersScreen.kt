package com.keltruc.mymemos.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.User
import com.keltruc.mymemos.ui.account.RemoteContent
import com.keltruc.mymemos.ui.components.Avatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminUsersScreen(serverUrl: String, onBack: () -> Unit, viewModel: AdminUsersViewModel = hiltViewModel()) {
    val state by viewModel.items.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showCreate by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<User?>(null) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.message.value = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_users)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, stringResource(R.string.user_new)) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        RemoteContent(state, onRetry = viewModel::refresh) { users ->
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(users, key = { it.name }) { u ->
                    ListItem(
                        leadingContent = { Avatar(name = u.displayName.ifEmpty { u.username }, url = u.avatarUrl, serverUrl = serverUrl, modifier = Modifier.size(40.dp)) },
                        headlineContent = { Text(u.displayName.ifEmpty { u.username }) },
                        supportingContent = { Text("@${u.username} · ${u.role.name.lowercase()}" + if (u.email.isNotEmpty()) " · ${u.email}" else "") },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { deleting = u }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            }
                        },
                    )
                }
            }
        }
    }

    if (showCreate) {
        var username by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("USER") }
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text(stringResource(R.string.user_new)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(stringResource(R.string.signin_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.signin_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = role == "USER", onClick = { role = "USER" }, label = { Text("User") })
                        FilterChip(selected = role == "ADMIN", onClick = { role = "ADMIN" }, label = { Text("Admin") })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = username.isNotBlank() && password.length >= 3, onClick = { viewModel.create(username.trim(), password, role); showCreate = false }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = { showCreate = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleting?.let { u ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("@${u.username}") },
            text = { Text(stringResource(R.string.user_delete_confirm)) },
            confirmButton = { TextButton(onClick = { viewModel.delete(u); deleting = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
