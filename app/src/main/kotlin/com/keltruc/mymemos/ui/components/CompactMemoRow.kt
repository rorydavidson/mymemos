package com.keltruc.mymemos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keltruc.mymemos.R
import com.keltruc.mymemos.data.text.MemoTitle
import com.keltruc.mymemos.model.Memo
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/**
 * One memo as a single line: its title, the time it was written, and the badges that say
 * something the title cannot. For scanning a long timeline rather than reading it.
 */
@Composable
fun CompactMemoRow(memo: Memo, byModified: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // A locked memo's body is ciphertext, so there is no title to pull out of it.
    val title = if (memo.isLocked) null else MemoTitle.of(memo.displayContent)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        if (memo.pinned) {
            Icon(
                Icons.Default.PushPin,
                contentDescription = stringResource(R.string.pin),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        if (memo.isLocked) {
            Icon(
                Icons.Default.Lock,
                contentDescription = stringResource(R.string.locked_memo),
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            title ?: stringResource(if (memo.isLocked) R.string.locked_memo else R.string.untitled),
            style = MaterialTheme.typography.bodyLarge,
            color = if (title == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            timeFormatter.format(memo.timelineTime(byModified).atZone(ZoneId.systemDefault())),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
