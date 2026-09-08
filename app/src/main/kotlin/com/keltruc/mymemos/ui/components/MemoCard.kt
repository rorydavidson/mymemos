package com.keltruc.mymemos.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.model.Visibility
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

@Composable
fun MemoCard(memo: Memo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (memo.pinned) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    formatter.format(memo.createTime.atZone(ZoneId.systemDefault())),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                if (memo.isPendingLocalChange) SmallIcon(Icons.Default.CloudOff)
                if (memo.attachments.isNotEmpty()) SmallIcon(Icons.Default.Attachment)
                when (memo.visibility) {
                    Visibility.PRIVATE -> SmallIcon(Icons.Default.Lock)
                    Visibility.PUBLIC -> SmallIcon(Icons.Default.Public)
                    Visibility.PROTECTED -> Unit
                }
                if (memo.pinned) SmallIcon(Icons.Default.PushPin)
            }
            Text(
                memo.content,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis,
            )
            if (memo.tags.isNotEmpty()) {
                Text(
                    memo.tags.joinToString(" ") { "#$it" },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun SmallIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.size(16.dp),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
