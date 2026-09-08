package com.keltruc.mymemos.ui.detail

import android.Manifest
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AddReaction
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.ui.components.AttachmentStrip
import com.keltruc.mymemos.ui.components.Avatar
import com.keltruc.mymemos.ui.components.ColourPickerDialog
import com.keltruc.mymemos.ui.components.DateTimePickerDialog
import com.keltruc.mymemos.ui.components.tint
import com.keltruc.mymemos.ui.components.MapPreview
import com.keltruc.mymemos.ui.components.MemoContent
import com.keltruc.mymemos.ui.components.MemoPasswordDialog
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy, HH:mm")
private val shortFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
private val reminderFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm")
private val quickReactions = listOf("👍", "❤️", "😂", "😮", "😢", "🎉", "👀", "🔥", "✅")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MemoDetailScreen(
    localId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenMemo: (String) -> Unit,
    showBack: Boolean = true,
    viewModel: MemoDetailViewModel = hiltViewModel<MemoDetailViewModel, MemoDetailViewModel.Factory>(key = localId) { it.create(localId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val unlockedText by viewModel.unlockedText.collectAsStateWithLifecycle()
    val askPassword by viewModel.askPassword.collectAsStateWithLifecycle()
    val passwordError by viewModel.passwordError.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var overflow by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var showReferencePicker by remember { mutableStateOf(false) }
    var showColour by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    val reminders by viewModel.reminders.collectAsStateWithLifecycle()
    val exactHint by viewModel.exactAlarmHint.collectAsStateWithLifecycle()
    val sortByModified by viewModel.sortByModified.collectAsStateWithLifecycle()
    val exactHintText = stringResource(R.string.reminder_exact_hint)
    LaunchedEffect(exactHint) {
        if (exactHint) {
            val result = snackbar.showSnackbar(exactHintText, actionLabel = "Settings", duration = androidx.compose.material3.SnackbarDuration.Long)
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed && android.os.Build.VERSION.SDK_INT >= 31) {
                runCatching { context.startActivity(Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + context.packageName))) }
            }
            viewModel.exactAlarmHint.value = false
        }
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) showReminder = true
    }
    var comment by remember { mutableStateOf("") }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.captureLocation()
    }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.dismissMessage() }
    }

    Scaffold(
        containerColor = state.memo?.colour?.tint() ?: MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = state.memo?.colour?.tint() ?: MaterialTheme.colorScheme.background),
                navigationIcon = {
                    if (showBack) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    val m = state.memo ?: return@TopAppBar
                    IconButton(onClick = viewModel::togglePin) {
                        Icon(Icons.Default.PushPin, stringResource(R.string.pin), tint = if (m.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, stringResource(R.string.edit_memo)) }
                    IconButton(onClick = viewModel::loadShares) { Icon(Icons.Default.Share, stringResource(R.string.share_link)) }
                    IconButton(onClick = { overflow = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = overflow, onDismissRequest = { overflow = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(if (m.state == MemoState.ARCHIVED) R.string.unarchive else R.string.archive)) },
                            leadingIcon = { Icon(if (m.state == MemoState.ARCHIVED) Icons.Default.Unarchive else Icons.Default.Archive, null) },
                            onClick = { overflow = false; viewModel.toggleArchive() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.add_reference)) },
                            leadingIcon = { Icon(Icons.Default.Link, null) },
                            onClick = { overflow = false; showReferencePicker = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (m.location == null) R.string.location_add else R.string.location_remove)) },
                            leadingIcon = { Icon(Icons.Default.AddLocationAlt, null) },
                            onClick = {
                                overflow = false
                                if (m.location != null) viewModel.clearLocation() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.remind_me)) },
                            leadingIcon = { Icon(Icons.Default.Alarm, null) },
                            onClick = {
                                overflow = false
                                if (android.os.Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS) else showReminder = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (m.isLocked) R.string.unlock else R.string.lock)) },
                            leadingIcon = { Icon(if (m.isLocked) Icons.Default.LockOpen else Icons.Default.Lock, null) },
                            onClick = { overflow = false; if (m.isLocked) viewModel.requestRemoveLock() else viewModel.lockNow() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.colour)) },
                            leadingIcon = { Icon(Icons.Default.Palette, null) },
                            onClick = { overflow = false; showColour = true },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete)) },
                            leadingIcon = { Icon(Icons.Default.Delete, null) },
                            onClick = { overflow = false; confirmDelete = true },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = state.memo ?: return@Scaffold
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
                formatter.format(m.timelineTime(sortByModified).atZone(ZoneId.systemDefault())),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                !m.isLocked -> MemoContent(content = m.displayContent, onToggleTask = viewModel::toggleTask, modifier = Modifier.fillMaxWidth())
                unlockedText != null -> {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.LockOpen, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.locked_memo), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    MemoContent(content = unlockedText!!, onToggleTask = viewModel::toggleTask, modifier = Modifier.fillMaxWidth())
                }
                else -> Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.locked_memo), style = MaterialTheme.typography.titleMedium)
                        }
                        Text(stringResource(R.string.locked_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                        TextButton(onClick = { viewModel.askPassword.value = MemoDetailViewModel.PasswordPurpose.UNLOCK_VIEW }) { Text(stringResource(R.string.unlock_button)) }
                    }
                }
            }
            if (m.attachments.isNotEmpty()) {
                AttachmentStrip(attachments = m.attachments, serverUrl = state.account?.serverUrl.orEmpty(), thumbSize = 140)
            }
            reminders.forEach { r ->
                AssistChip(
                    onClick = { viewModel.removeReminder(r.id) },
                    leadingIcon = { Icon(Icons.Default.Alarm, null, Modifier.size(16.dp)) },
                    trailingIcon = { Icon(Icons.Default.Close, stringResource(R.string.reminder_remove), Modifier.size(16.dp)) },
                    label = { Text(stringResource(R.string.reminder_set, reminderFormatter.format(java.time.Instant.ofEpochMilli(r.atEpochMs).atZone(ZoneId.systemDefault())))) },
                )
            }
            m.location?.let { loc -> MapPreview(loc) }
            m.location?.let { loc ->
                AssistChip(
                    onClick = {
                        val uri = Uri.parse("geo:${loc.latitude},${loc.longitude}?q=${loc.latitude},${loc.longitude}(${Uri.encode(loc.placeholder.ifEmpty { "Memo" })})")
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
                    },
                    leadingIcon = { Icon(Icons.Default.Place, null, Modifier.size(16.dp)) },
                    label = { Text(loc.placeholder.ifEmpty { "%.4f, %.4f".format(loc.latitude, loc.longitude) }) },
                )
            }

            // Reactions
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val me = state.account?.userResourceName
                state.reactions.groupBy { it.reactionType }.forEach { (type, list) ->
                    FilterChip(
                        selected = list.any { it.creator == me },
                        onClick = { viewModel.react(type) },
                        label = { Text("$type ${list.size}") },
                        shape = CircleShape,
                    )
                }
                IconButton(onClick = { showReactions = !showReactions }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.AddReaction, stringResource(R.string.reactions_add), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showReactions) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    quickReactions.forEach { emoji ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.clickable { viewModel.react(emoji); showReactions = false },
                        ) { Text(emoji, Modifier.padding(horizontal = 10.dp, vertical = 6.dp), style = MaterialTheme.typography.titleMedium) }
                    }
                }
            }

            // References and backlinks
            if (state.references.isNotEmpty()) {
                Section(stringResource(R.string.references))
                state.references.forEach { ref ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Link, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            ref.relatedSnippet.ifEmpty { ref.relatedRemoteName },
                            Modifier.weight(1f).clickable { viewModel.openReference(ref)?.let(onOpenMemo) },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        IconButton(onClick = { viewModel.removeReference(ref) }, Modifier.size(28.dp)) { Icon(Icons.Default.Close, null, Modifier.size(16.dp)) }
                    }
                }
            }
            if (state.backlinks.isNotEmpty()) {
                Section(stringResource(R.string.referenced_by))
                state.backlinks.forEach { b ->
                    Text(
                        b.snippet.ifEmpty { b.content.lineSequence().first() },
                        Modifier.fillMaxWidth().clickable { onOpenMemo(b.localId) }.padding(vertical = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            // Comments
            HorizontalDivider(Modifier.padding(top = 8.dp))
            Section(stringResource(R.string.comments) + if (state.comments.isEmpty()) "" else " (${state.comments.size})")
            state.comments.forEach { c -> CommentRow(c, state.account?.serverUrl.orEmpty()) }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
                OutlinedTextField(
                    value = comment,
                    onValueChange = { comment = it },
                    placeholder = { Text(stringResource(R.string.comment_hint)) },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    maxLines = 4,
                )
                IconButton(onClick = { if (comment.isNotBlank()) { viewModel.addComment(comment); comment = "" } }, enabled = comment.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.comment_send))
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(bottom = 24.dp),
            ) {
                // Both, always, whichever one the date at the top of the screen is showing.
                Text(
                    stringResource(R.string.created_on, formatter.format(m.createTime.atZone(ZoneId.systemDefault()))),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.last_modified, formatter.format(m.updateTime.atZone(ZoneId.systemDefault()))),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${m.visibility.name.lowercase().replaceFirstChar(Char::uppercase)} · ${m.remoteName ?: "not yet synced"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    state.shares?.let { shares ->
        ShareSheet(
            shares = shares,
            onCreate = viewModel::createShare,
            onRevoke = viewModel::revokeShare,
            onCopy = { url ->
                val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Memo link", url))
            },
            onShareExternally = { url ->
                val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, url) }
                context.startActivity(Intent.createChooser(send, null))
            },
            onDismiss = viewModel::closeShares,
        )
    }

    askPassword?.let { purpose ->
        MemoPasswordDialog(
            title = stringResource(
                when (purpose) {
                    MemoDetailViewModel.PasswordPurpose.LOCK -> R.string.lock
                    MemoDetailViewModel.PasswordPurpose.REMOVE_LOCK -> R.string.unlock
                    MemoDetailViewModel.PasswordPurpose.UNLOCK_VIEW -> R.string.locked_memo
                },
            ),
            hint = stringResource(if (purpose == MemoDetailViewModel.PasswordPurpose.LOCK) R.string.lock_confirm else R.string.memo_password_hint),
            confirmLabel = stringResource(if (purpose == MemoDetailViewModel.PasswordPurpose.LOCK) R.string.lock else R.string.unlock_button),
            error = passwordError,
            initialRemember = viewModel.passwordRemembered,
            onConfirm = viewModel::submitPassword,
            onDismiss = viewModel::dismissPassword,
        )
    }

    if (showReminder) {
        DateTimePickerDialog(onPicked = { viewModel.addReminder(it); showReminder = false }, onDismiss = { showReminder = false })
    }

    if (showColour) {
        ColourPickerDialog(
            current = state.memo?.colour,
            onPick = { viewModel.setColour(it); showColour = false },
            onDismiss = { showColour = false },
        )
    }

    if (showReferencePicker) {
        ReferencePickerDialog(
            search = viewModel::referenceCandidates,
            onPick = { viewModel.addReference(it); showReferencePicker = false },
            onDismiss = { showReferencePicker = false },
        )
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

@Composable
private fun Section(title: String) {
    Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
}

@Composable
private fun CommentRow(comment: Memo, serverUrl: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Avatar(name = comment.creator?.substringAfterLast('/') ?: "?", url = "", serverUrl = serverUrl, modifier = Modifier.size(32.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(comment.creator?.substringAfterLast('/') ?: "", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    shortFormatter.format(comment.createTime.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (comment.isPendingLocalChange) {
                    Spacer(Modifier.width(6.dp))
                    Text("· pending", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            MemoContent(content = comment.content, onToggleTask = null)
        }
    }
}
