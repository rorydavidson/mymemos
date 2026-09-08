package com.keltruc.mymemos.ui.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keltruc.mymemos.R
import com.keltruc.mymemos.data.sync.FailedOp
import com.keltruc.mymemos.data.sync.SyncState
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusSheet(
    state: SyncState,
    failed: List<FailedOp>,
    onSyncNow: () -> Unit,
    onRetryFailed: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.sync_status), style = MaterialTheme.typography.titleLarge)
            Text(
                state.lastSuccess?.let {
                    "Last synced " + DateTimeFormatter.ofPattern("d MMM, HH:mm").format(it.atZone(ZoneId.systemDefault()))
                } ?: stringResource(R.string.sync_never),
            )
            Text(stringResource(R.string.sync_pending, state.pendingCount))
            state.lastError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            if (failed.isNotEmpty()) {
                HorizontalDivider()
                Text(stringResource(R.string.sync_failed, failed.size), style = MaterialTheme.typography.titleSmall)
                failed.forEach { op ->
                    Text("${op.type}: ${op.error ?: "unknown error"}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Button(onClick = onSyncNow, enabled = !state.running) { Text(stringResource(R.string.timeline_refresh)) }
                if (failed.isNotEmpty()) {
                    OutlinedButton(onClick = onRetryFailed) { Text(stringResource(R.string.sync_retry)) }
                }
            }
        }
    }
}
