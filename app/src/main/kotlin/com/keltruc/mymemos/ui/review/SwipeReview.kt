package com.keltruc.mymemos.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.ui.components.MemoCard

/**
 * One memo at a time. Swipe left to archive, right to keep and move on; buttons for pin,
 * tag, edit and delete. Index is by position so the list shrinking underneath (archive
 * removes the memo from the day) keeps the stack moving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeReview(
    memos: List<Memo>,
    serverUrl: String,
    onPin: (Memo) -> Unit,
    onArchive: (Memo) -> Unit,
    onDelete: (Memo) -> Unit,
    onAddTag: (Memo, String) -> Unit,
    onOpen: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    var index by remember(memos.size) { mutableIntStateOf(0) }
    var tagging by remember { mutableStateOf<Memo?>(null) }
    val total = memos.size
    val memo = memos.getOrNull(index)

    if (memo == null) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(if (total == 0) R.string.review_empty_day else R.string.review_done), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onArchive(memo); true }
                SwipeToDismissBoxValue.StartToEnd -> { index++; true }
                else -> false
            }
        },
    )
    LaunchedEffect(memo.localId) { dismiss.reset() }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.review_progress, (index + 1).coerceAtMost(total), total), style = MaterialTheme.typography.labelLarge)
            Text("  ·  " + stringResource(R.string.review_swipe_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        SwipeToDismissBox(
            state = dismiss,
            backgroundContent = {
                val archive = dismiss.dismissDirection == SwipeToDismissBoxValue.EndToStart
                Box(
                    Modifier.fillMaxSize().background(
                        if (archive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.shapes.medium,
                    ).padding(24.dp),
                    contentAlignment = if (archive) Alignment.CenterEnd else Alignment.CenterStart,
                ) {
                    Icon(if (archive) Icons.Default.Archive else Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            },
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Box(Modifier.verticalScroll(rememberScrollState())) {
                MemoCard(
                    memo = memo,
                    serverUrl = serverUrl,
                    onClick = { onOpen(memo.localId) },
                    onToggleTask = { _, _ -> },
                    onEdit = { onEdit(memo.localId) },
                    onPin = { onPin(memo) },
                    onArchive = { onArchive(memo) },
                    onDelete = { onDelete(memo) },
                    onColour = {},
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            FilledTonalIconButton(onClick = { onPin(memo) }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Default.PushPin, stringResource(if (memo.pinned) R.string.unpin else R.string.pin), tint = if (memo.pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            }
            FilledTonalIconButton(onClick = { tagging = memo }, enabled = !memo.isLocked, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Tag, stringResource(R.string.review_add_tag)) }
            FilledTonalIconButton(onClick = { onEdit(memo.localId) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Edit, stringResource(R.string.edit_memo)) }
            FilledTonalIconButton(onClick = { onArchive(memo) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Archive, stringResource(R.string.archive)) }
            IconButton(onClick = { onDelete(memo) }, modifier = Modifier.size(56.dp)) { Icon(Icons.Default.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
            FilledTonalIconButton(onClick = { index++ }, modifier = Modifier.size(56.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.review_keep)) }
        }
    }

    tagging?.let { m ->
        var tag by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { tagging = null },
            title = { Text(stringResource(R.string.review_add_tag)) },
            text = { OutlinedTextField(value = tag, onValueChange = { tag = it.trimStart('#').replace(" ", "") }, prefix = { Text("#") }, singleLine = true) },
            confirmButton = { TextButton(enabled = tag.isNotBlank(), onClick = { onAddTag(m, tag); tagging = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { tagging = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
