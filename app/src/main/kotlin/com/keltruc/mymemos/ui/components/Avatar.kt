package com.keltruc.mymemos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.keltruc.mymemos.data.account.AvatarSource

/**
 * Circular avatar. Memos stores avatars either as a `data:` URI or as a path on the server;
 * both are handled, and the initial is the fallback.
 */
@Composable
fun Avatar(name: String, url: String, serverUrl: String, modifier: Modifier = Modifier) {
    val model: Any? = remember(url, serverUrl) { avatarModel(url, serverUrl) }
    Box(
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (model != null) {
            AsyncImage(model = model, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

/**
 * Coil takes bytes or a URL; [AvatarSource] decides which, and whether the URL is the
 * account's own server. A foreign one is still shown, but the image loader's interceptor only
 * attaches the token for the account's own origin, so the credential does not travel with it.
 */
private fun avatarModel(url: String, serverUrl: String): Any? =
    when (val source = AvatarSource.of(url, serverUrl)) {
        is AvatarSource.None -> null
        is AvatarSource.Bytes -> source.bytes
        is AvatarSource.Url -> source.url
    }
