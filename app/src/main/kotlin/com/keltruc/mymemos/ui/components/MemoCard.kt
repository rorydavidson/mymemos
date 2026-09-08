package com.keltruc.mymemos.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.MemoState
import com.keltruc.mymemos.model.SyncStatus
import com.keltruc.mymemos.model.Visibility
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MemoCard(
    memo: Memo,
    serverUrl: String,
    onClick: () -> Unit,
    onToggleTask: (Int, Boolean) -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
            colors = CardDefaults.cardColors(
                containerColor = when {
                    memo.syncStatus == SyncStatus.CONFLICT -> MaterialTheme.colorScheme.errorContainer
                    memo.pinned -> MaterialTheme.colorScheme.secondaryContainer
                    else -> MaterialTheme.colorScheme.surfaceContainer
                },
            ),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        formatter.format(memo.createTime.atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (memo.syncStatus == SyncStatus.CONFLICT) SmallIcon(Icons.Default.Warning)
                            else if (memo.isPendingLocalChange) SmallIcon(Icons.Default.CloudOff)
                            when (memo.visibility) {
                                Visibility.PRIVATE -> SmallIcon(Icons.Default.Lock)
                                Visibility.PUBLIC -> SmallIcon(Icons.Default.Public)
                                Visibility.PROTECTED -> Unit
                            }
                            if (memo.pinned) SmallIcon(Icons.Default.PushPin)
                        }
                    }
                }
                MemoContent(content = memo.content, onToggleTask = onToggleTask, maxLines = 10)
                AttachmentStrip(attachments = memo.attachments, serverUrl = serverUrl, thumbSize = 72)
            }
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit_memo)) },
                leadingIcon = { Icon(Icons.Default.Edit, null) },
                onClick = { menu = false; onEdit() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (memo.pinned) R.string.unpin else R.string.pin)) },
                leadingIcon = { Icon(Icons.Default.PushPin, null) },
                onClick = { menu = false; onPin() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(if (memo.state == MemoState.ARCHIVED) R.string.unarchive else R.string.archive)) },
                leadingIcon = { Icon(if (memo.state == MemoState.ARCHIVED) Icons.Default.Unarchive else Icons.Default.Archive, null) },
                onClick = { menu = false; onArchive() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, null) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

@Composable
private fun SmallIcon(icon: ImageVector) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}
