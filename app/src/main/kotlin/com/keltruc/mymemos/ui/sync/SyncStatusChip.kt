package com.keltruc.mymemos.ui.sync

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keltruc.mymemos.R
import com.keltruc.mymemos.data.sync.SyncState

@Composable
fun SyncStatusChip(state: SyncState, onClick: () -> Unit) {
    val (label, icon) = when {
        state.running -> stringResource(R.string.sync_running) to null
        state.authExpired -> stringResource(R.string.sign_in_again) to Icons.Default.ErrorOutline
        state.failedCount > 0 -> stringResource(R.string.sync_failed, state.failedCount) to Icons.Default.ErrorOutline
        state.pendingCount > 0 -> stringResource(R.string.sync_pending, state.pendingCount) to Icons.Default.CloudUpload
        state.lastError != null -> "Offline" to Icons.Default.CloudOff
        else -> stringResource(R.string.sync_idle) to Icons.Default.CloudDone
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            if (icon == null) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = stringResource(R.string.sync_status), Modifier.size(16.dp))
            }
        },
    )
}
