package com.keltruc.mymemos.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.keltruc.mymemos.data.mapper.remoteUrl
import com.keltruc.mymemos.model.Attachment
import java.io.File

@Composable
fun AttachmentStrip(
    attachments: List<Attachment>,
    serverUrl: String,
    onRemove: ((Attachment) -> Unit)? = null,
    onOpen: ((Attachment) -> Unit)? = null,
    modifier: Modifier = Modifier,
    thumbSize: Int = 96,
) {
    if (attachments.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(attachments, key = { it.localId }) { a ->
            Box {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .size(thumbSize.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(enabled = onOpen != null) { onOpen?.invoke(a) },
                ) {
                    val model: Any? = a.localPath?.let { File(it) } ?: a.remoteUrl(serverUrl, thumbnail = true)
                    if (a.isImage && model != null) {
                        AsyncImage(model = model, contentDescription = a.filename, contentScale = ContentScale.Crop)
                    } else {
                        Column(
                            Modifier.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Icon(Icons.Default.InsertDriveFile, contentDescription = null)
                            Text(a.filename, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(a) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
