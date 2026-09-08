package com.keltruc.mymemos.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.ui.components.Avatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            account?.let { acc ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Avatar(name = acc.displayName, url = acc.avatarUrl, serverUrl = acc.serverUrl, modifier = Modifier.size(52.dp))
                        Column(Modifier.weight(1f)) {
                            Text(acc.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "@${acc.username} · ${acc.serverUrl.removePrefix("https://").trimEnd('/')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Memos ${acc.serverVersion} · ${acc.role.name.lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Text(stringResource(R.string.settings_editor), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_sort_completed)) },
                supportingContent = { Text(stringResource(R.string.setting_sort_completed_hint)) },
                trailingContent = { Switch(checked = settings.sortCompletedTasks, onCheckedChange = viewModel::setSortCompleted) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            Text(stringResource(R.string.settings_appearance), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_dynamic_colour)) },
                supportingContent = { Text(stringResource(R.string.setting_dynamic_colour_hint)) },
                trailingContent = { Switch(checked = settings.dynamicColour, onCheckedChange = viewModel::setDynamicColour) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("  " + stringResource(R.string.sign_out))
            }
        }
    }
}
