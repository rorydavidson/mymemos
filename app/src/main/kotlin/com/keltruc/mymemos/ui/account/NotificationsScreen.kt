package com.keltruc.mymemos.ui.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MarkEmailRead
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.ui.format.atZone
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val fmt: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenMemo: (remoteName: String) -> Unit,
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val state by viewModel.items.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        RemoteContent(state, onRetry = viewModel::refresh) { list ->
            if (list.isEmpty()) {
                Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.notifications_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(list, key = { it.name }) { n ->
                    val verb = stringResource(if (n.type == "MEMO_MENTION") R.string.notification_mention else R.string.notification_comment)
                    ListItem(
                        headlineContent = { Text("${n.senderUsername} $verb: ${n.relatedSnippet.ifEmpty { n.memoSnippet }}", maxLines = 2, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Text(
                                listOf(fmt.format(n.createTime.atZone(ZoneId.systemDefault())), n.memoSnippet.takeIf { it.isNotEmpty() && it != n.relatedSnippet }.orEmpty())
                                    .filter { it.isNotEmpty() }.joinToString(" · "),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingContent = {
                            if (n.unread) {
                                IconButton(onClick = { viewModel.markRead(n) }) { Icon(Icons.Default.MarkEmailRead, stringResource(R.string.mark_read)) }
                            } else {
                                IconButton(onClick = { viewModel.delete(n) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = if (n.unread) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surface),
                        modifier = Modifier.clickable(enabled = n.memoRemoteName.isNotEmpty()) { onOpenMemo(n.memoRemoteName) }.padding(vertical = 2.dp),
                    )
                }
            }
        }
    }
}
