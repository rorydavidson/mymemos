package com.keltruc.mymemos.ui.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import com.keltruc.mymemos.model.InstanceGeneral
import com.keltruc.mymemos.ui.account.RemoteContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminInstanceScreen(onBack: () -> Unit, viewModel: AdminInstanceViewModel = hiltViewModel()) {
    val state by viewModel.items.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.message.value = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.admin_instance)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        RemoteContent(state, onRetry = viewModel::refresh) { data ->
            var draft by remember(data.general) { mutableStateOf(data.general) }
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = draft.title, onValueChange = { draft = draft.copy(title = it) }, label = { Text(stringResource(R.string.instance_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = draft.description, onValueChange = { draft = draft.copy(description = it) }, label = { Text(stringResource(R.string.instance_description)) }, modifier = Modifier.fillMaxWidth())
                Toggle(stringResource(R.string.instance_disallow_registration), draft.disallowRegistration) { draft = draft.copy(disallowRegistration = it) }
                Toggle(stringResource(R.string.instance_disallow_password), draft.disallowPasswordAuth) { draft = draft.copy(disallowPasswordAuth = it) }
                Toggle(stringResource(R.string.instance_disallow_username), draft.disallowChangeUsername) { draft = draft.copy(disallowChangeUsername = it) }
                Toggle(stringResource(R.string.instance_disallow_nickname), draft.disallowChangeNickname) { draft = draft.copy(disallowChangeNickname = it) }
                Button(onClick = { viewModel.save(draft) }, enabled = draft != data.general, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text(stringResource(R.string.save)) }

                data.stats?.let { s ->
                    Text(stringResource(R.string.instance_storage), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp))
                    Text("Database: ${s.databaseDriver} · ${formatBytes(s.databaseBytes)}")
                    Text("Local files: ${formatBytes(s.localStorageBytes)}")
                }
            }
        }
    }
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%.0f KB".format(bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

@Suppress("unused")
private val keep = InstanceGeneral::class
