package com.keltruc.mymemos.ui.timeline

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.Shortcut
import com.keltruc.mymemos.ui.components.Avatar
import com.keltruc.mymemos.ui.components.ColourPickerDialog
import com.keltruc.mymemos.ui.components.MemoCard
import com.keltruc.mymemos.ui.sync.SyncStatusChip
import com.keltruc.mymemos.ui.sync.SyncStatusSheet
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimelineScreen(
    onOpenMemo: (String) -> Unit,
    onNewMemo: () -> Unit,
    onEditMemo: (String) -> Unit,
    onSettings: () -> Unit,
    onManageShortcuts: () -> Unit,
    onNotifications: () -> Unit,
    onReview: () -> Unit,
    viewModel: TimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unread by viewModel.unreadNotifications.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showSync by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Memo?>(null) }
    var showReauth by remember { mutableStateOf(false) }
    var colouring by remember { mutableStateOf<Memo?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    val grouped = remember(state.memos) { groupByDay(state.memos) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNewMemo,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.new_memo)) }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(bottom = padding.calculateBottomPadding()),
        ) {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "header") {
                    Header(
                        state = state,
                        onSettings = onSettings,
                        onSync = { showSync = true },
                        onQuery = viewModel::onQuery,
                        onTag = viewModel::onTag,
                        onToggleArchived = viewModel::toggleArchived,
                        onShortcut = viewModel::onShortcut,
                        onManageShortcuts = onManageShortcuts,
                        unread = unread,
                        onNotifications = onNotifications,
                        onReview = onReview,
                    )
                }
                if (state.sync.authExpired) {
                    item(key = "reauth") {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.session_expired), color = MaterialTheme.colorScheme.onErrorContainer)
                                TextButton(onClick = { showReauth = true }) { Text(stringResource(R.string.sign_in_again)) }
                            }
                        }
                    }
                }
                if (state.memos.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.fillMaxWidth().padding(top = 96.dp), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.timeline_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                grouped.forEach { (label, memos) ->
                    stickyHeader(key = "day-$label") {
                        DayHeader(label)
                    }
                    items(memos, key = { it.localId }) { memo ->
                        MemoCard(
                            memo = memo,
                            serverUrl = state.account?.serverUrl.orEmpty(),
                            onClick = { onOpenMemo(memo.localId) },
                            onToggleTask = { line, checked -> viewModel.toggleTask(memo, line, checked) },
                            onEdit = { onEditMemo(memo.localId) },
                            onPin = { viewModel.togglePin(memo) },
                            onArchive = { viewModel.toggleArchive(memo) },
                            onDelete = { pendingDelete = memo },
                            onColour = { colouring = memo },
                            onTagClick = { viewModel.onTag(it) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }
    }

    if (showSync) {
        SyncStatusSheet(
            state = state.sync,
            failed = state.failedOps,
            onSyncNow = { viewModel.refresh(); showSync = false },
            onRetryFailed = { viewModel.retryFailed(); showSync = false },
            onDismiss = { showSync = false },
        )
    }

    colouring?.let { memo ->
        ColourPickerDialog(
            current = memo.colour,
            onPick = { viewModel.setColour(memo, it); colouring = null },
            onDismiss = { colouring = null },
        )
    }

    if (showReauth) {
        var password by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showReauth = false },
            title = { Text(stringResource(R.string.reauth_title)) },
            text = {
                Column {
                    state.account?.let { Text("${it.username} · ${it.serverUrl.removePrefix("https://").trimEnd('/')}", style = MaterialTheme.typography.bodyMedium) }
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text(stringResource(R.string.signin_password)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = password.isNotBlank(), onClick = { viewModel.reauthenticate(password); showReauth = false }) { Text(stringResource(R.string.signin_button)) }
            },
            dismissButton = { TextButton(onClick = { showReauth = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    pendingDelete?.let { memo ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete)) },
            text = { Text(stringResource(R.string.delete_confirm)) },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(memo); pendingDelete = null }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun Header(
    state: TimelineUiState,
    onSettings: () -> Unit,
    onSync: () -> Unit,
    onQuery: (String) -> Unit,
    onTag: (String?) -> Unit,
    onToggleArchived: () -> Unit,
    onShortcut: (Shortcut?) -> Unit,
    onManageShortcuts: () -> Unit,
    unread: Int,
    onNotifications: () -> Unit,
    onReview: () -> Unit,
) {
    Column(Modifier.statusBarsPadding().padding(top = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.account?.let { acc ->
                Avatar(
                    name = acc.displayName,
                    url = acc.avatarUrl,
                    serverUrl = acc.serverUrl,
                    modifier = Modifier.size(40.dp).clickable(onClick = onSettings),
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(if (state.showArchived) R.string.show_archived else R.string.timeline_title),
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                )
                Text(
                    stringResource(R.string.greeting_memos, state.memos.size) +
                        if (state.streak > 1) " · " + stringResource(R.string.streak, state.streak) else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            SyncStatusChip(state.sync, onClick = onSync)
            IconButton(onClick = onReview) {
                Icon(Icons.Default.CalendarMonth, contentDescription = stringResource(R.string.review), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onNotifications) {
                BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toString()) } }) {
                    Icon(Icons.Default.Notifications, contentDescription = stringResource(R.string.notifications), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onToggleArchived) {
                Icon(
                    if (state.showArchived) Icons.Default.Inbox else Icons.Default.Archive,
                    contentDescription = stringResource(if (state.showArchived) R.string.show_active else R.string.show_archived),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        SearchPill(query = state.query, onQuery = onQuery, modifier = Modifier.padding(horizontal = 16.dp))
        if (state.tags.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.tags) { tag ->
                    FilterChip(
                        selected = state.selectedTag == tag,
                        onClick = { onTag(tag) },
                        label = { Text("#$tag") },
                        shape = CircleShape,
                        border = null,
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.shortcuts, key = { it.name }) { shortcut ->
                FilterChip(
                    selected = state.selectedShortcut?.name == shortcut.name,
                    onClick = { onShortcut(shortcut) },
                    label = { Text(shortcut.title) },
                    leadingIcon = { Icon(Icons.Default.Bookmark, null, Modifier.size(16.dp)) },
                    shape = CircleShape,
                    border = null,
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    ),
                )
            }
            item(key = "manage") {
                AssistChip(
                    onClick = onManageShortcuts,
                    label = { Text(stringResource(if (state.shortcuts.isEmpty()) R.string.shortcut_new else R.string.shortcuts_manage)) },
                    leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(16.dp)) },
                    shape = CircleShape,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SearchPill(query: String, onQuery: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        modifier = modifier.fillMaxWidth().height(50.dp),
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(10.dp))
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        stringResource(R.string.timeline_search),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQuery("") }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String) {
    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp, vertical = 8.dp)) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM")
private val dayYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy")

private fun groupByDay(memos: List<Memo>): List<Pair<String, List<Memo>>> {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val pinned = memos.filter { it.pinned }
    val rest = memos.filterNot { it.pinned }
    val groups = rest.groupBy { it.createTime.atZone(zone).toLocalDate() }.map { (date, list) ->
        val label = when (ChronoUnit.DAYS.between(date, today)) {
            0L -> "Today"
            1L -> "Yesterday"
            in 2L..6L -> dayFormatter.format(date)
            else -> if (date.year == today.year) dayFormatter.format(date) else dayYearFormatter.format(date)
        }
        label to list
    }
    return if (pinned.isEmpty()) groups else listOf("Pinned" to pinned) + groups
}
