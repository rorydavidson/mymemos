package com.keltruc.mymemos.ui.settings

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Webhook
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.UserRole
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.ui.components.Avatar

data class SettingsNav(
    val onTokens: () -> Unit,
    val onWebhooks: () -> Unit,
    val onNotifications: () -> Unit,
    val onStats: () -> Unit,
    val onAdminUsers: () -> Unit,
    val onAdminInstance: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, nav: SettingsNav, viewModel: SettingsViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val account by viewModel.account.collectAsStateWithLifecycle()
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val serverPrefs by viewModel.serverPrefs.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editProfile by remember { mutableStateOf(false) }
    var editPassword by remember { mutableStateOf(false) }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); viewModel.message.value = null } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            account?.let { acc ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.clickable { editProfile = true },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Avatar(name = acc.displayName, url = profile?.avatarUrl ?: acc.avatarUrl, serverUrl = acc.serverUrl, modifier = Modifier.size(52.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile?.displayName?.ifEmpty { null } ?: acc.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "@${acc.username} · ${acc.serverUrl.removePrefix("https://").trimEnd('/')}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            profile?.description?.takeIf { it.isNotBlank() }?.let {
                                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "Memos ${acc.serverVersion} · ${acc.role.name.lowercase()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Section(stringResource(R.string.settings_server))
            serverPrefs?.let { prefs ->
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.default_visibility), style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Visibility.entries.forEach { v ->
                            FilterChip(
                                selected = prefs.defaultVisibility == v,
                                onClick = { viewModel.setDefaultVisibility(v) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (v) {
                                                Visibility.PRIVATE -> R.string.visibility_private
                                                Visibility.PROTECTED -> R.string.visibility_protected
                                                Visibility.PUBLIC -> R.string.visibility_public
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }
            LinkRow(Icons.Default.Notifications, stringResource(R.string.notifications), nav.onNotifications)
            LinkRow(Icons.Default.BarChart, stringResource(R.string.statistics), nav.onStats)
            LinkRow(Icons.Default.Key, stringResource(R.string.access_tokens), nav.onTokens)
            LinkRow(Icons.Default.Webhook, stringResource(R.string.webhooks), nav.onWebhooks)
            LinkRow(Icons.Default.Key, stringResource(R.string.change_password)) { editPassword = true }

            Section(stringResource(R.string.settings_editor))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_sort_completed)) },
                supportingContent = { Text(stringResource(R.string.setting_sort_completed_hint)) },
                trailingContent = { Switch(checked = settings.sortCompletedTasks, onCheckedChange = viewModel::setSortCompleted) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            Section(stringResource(R.string.settings_appearance))
            ListItem(
                headlineContent = { Text(stringResource(R.string.setting_dynamic_colour)) },
                supportingContent = { Text(stringResource(R.string.setting_dynamic_colour_hint)) },
                trailingContent = { Switch(checked = settings.dynamicColour, onCheckedChange = viewModel::setDynamicColour) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )

            if (account?.role == UserRole.ADMIN) {
                Section(stringResource(R.string.admin))
                LinkRow(Icons.Default.People, stringResource(R.string.admin_users), nav.onAdminUsers)
                LinkRow(Icons.Default.Dns, stringResource(R.string.admin_instance), nav.onAdminInstance)
            }

            OutlinedButton(onClick = viewModel::signOut, modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 32.dp)) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Text("  " + stringResource(R.string.sign_out))
            }
        }
    }

    if (editProfile) {
        var displayName by remember { mutableStateOf(profile?.displayName ?: account?.displayName.orEmpty()) }
        var description by remember { mutableStateOf(profile?.description.orEmpty()) }
        var email by remember { mutableStateOf(profile?.email.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editProfile = false },
            title = { Text(stringResource(R.string.profile_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = displayName, onValueChange = { displayName = it }, label = { Text(stringResource(R.string.display_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text(stringResource(R.string.description)) }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = email, onValueChange = { email = it }, label = { Text(stringResource(R.string.email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.saveProfile(displayName.trim(), description.trim(), email.trim()); editProfile = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editProfile = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (editPassword) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { editPassword = false },
            title = { Text(stringResource(R.string.change_password)) },
            text = {
                OutlinedTextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.new_password)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            },
            confirmButton = { TextButton(enabled = password.length >= 3, onClick = { viewModel.changePassword(password); editPassword = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editPassword = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun LinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Icon(Icons.Default.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.clickable(onClick = onClick),
    )
}
