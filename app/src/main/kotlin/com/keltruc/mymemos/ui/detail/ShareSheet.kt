package com.keltruc.mymemos.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.MemoShare
import com.keltruc.mymemos.ui.format.atZone
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShareSheet(
    shares: List<MemoShare>,
    onCreate: () -> Unit,
    onRevoke: (MemoShare) -> Unit,
    onCopy: (String) -> Unit,
    onShareExternally: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.share_links), style = MaterialTheme.typography.titleLarge)
            if (shares.isEmpty()) {
                Text(stringResource(R.string.share_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            shares.forEach { share ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text(share.url, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Created " + DateTimeFormatter.ofPattern("d MMM yyyy").format(share.createTime.atZone(ZoneId.systemDefault())),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onCopy(share.url) }) { Icon(Icons.Default.ContentCopy, stringResource(R.string.share_copy)) }
                    IconButton(onClick = { onShareExternally(share.url) }) { Icon(Icons.Default.Share, null) }
                    IconButton(onClick = { onRevoke(share) }) { Icon(Icons.Default.Delete, stringResource(R.string.share_revoke), tint = MaterialTheme.colorScheme.error) }
                }
            }
            Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.share_create)) }
        }
    }
}
