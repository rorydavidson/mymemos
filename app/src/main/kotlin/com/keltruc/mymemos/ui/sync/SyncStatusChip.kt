package com.keltruc.mymemos.ui.sync

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keltruc.mymemos.R
import com.keltruc.mymemos.data.sync.SyncState

/** Compact sync indicator: a cloud icon whose colour and badge say what is going on. */
@Composable
fun SyncStatusChip(state: SyncState, onClick: () -> Unit) {
    val (icon, tint, badge) = when {
        state.running -> Triple(null, MaterialTheme.colorScheme.primary, null)
        state.authExpired -> Triple(Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error, null)
        state.failedCount > 0 -> Triple(Icons.Default.ErrorOutline, MaterialTheme.colorScheme.error, state.failedCount)
        state.pendingCount > 0 -> Triple(Icons.Default.CloudUpload, MaterialTheme.colorScheme.tertiary, state.pendingCount)
        state.lastError != null -> Triple(Icons.Default.CloudOff, MaterialTheme.colorScheme.onSurfaceVariant, null)
        else -> Triple(Icons.Default.CloudDone, MaterialTheme.colorScheme.primary, null)
    }
    IconButton(onClick = onClick) {
        BadgedBox(badge = { if (badge != null) Badge { Text(badge.toString()) } }) {
            if (icon == null) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(icon, contentDescription = stringResource(R.string.sync_status), tint = tint)
            }
        }
    }
}
