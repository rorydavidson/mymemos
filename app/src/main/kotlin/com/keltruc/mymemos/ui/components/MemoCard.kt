package com.keltruc.mymemos.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

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
    onColour: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menu by remember { mutableStateOf(false) }
    val conflict = memo.syncStatus == SyncStatus.CONFLICT
    Box(modifier) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = when {
                conflict -> MaterialTheme.colorScheme.errorContainer
                memo.colour != null -> memo.colour!!.tint()
                memo.pinned -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainer
            },
            tonalElevation = 0.dp,
            shadowElevation = 3.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = { menu = true }),
        ) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                MemoContent(content = memo.content, onToggleTask = onToggleTask, maxLines = 12)
                AttachmentStrip(attachments = memo.attachments, serverUrl = serverUrl, thumbSize = 88)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        timeFormatter.format(memo.createTime.atZone(ZoneId.systemDefault())),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    if (conflict) SmallIcon(Icons.Default.Warning, MaterialTheme.colorScheme.error)
                    else if (memo.isPendingLocalChange) SmallIcon(Icons.Default.CloudOff)
                    when (memo.visibility) {
                        Visibility.PRIVATE -> SmallIcon(Icons.Default.Lock)
                        Visibility.PUBLIC -> SmallIcon(Icons.Default.Public)
                        Visibility.PROTECTED -> Unit
                    }
                    if (memo.pinned) SmallIcon(Icons.Default.PushPin, MaterialTheme.colorScheme.secondary)
                }
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
                text = { Text(stringResource(R.string.colour)) },
                leadingIcon = { Icon(Icons.Default.Palette, null) },
                onClick = { menu = false; onColour() },
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
private fun SmallIcon(icon: ImageVector, tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(15.dp), tint = tint)
}
