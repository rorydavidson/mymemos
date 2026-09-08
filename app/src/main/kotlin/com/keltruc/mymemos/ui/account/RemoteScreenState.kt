package com.keltruc.mymemos.ui.account

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keltruc.mymemos.R

/** Loading, error or data, for screens that read straight from the server. */
sealed interface Remote<out T> {
    data object Loading : Remote<Nothing>
    data class Failed(val message: String) : Remote<Nothing>
    data class Ready<T>(val value: T) : Remote<T>
}

@Composable
fun <T> RemoteContent(state: Remote<T>, onRetry: () -> Unit, content: @Composable (T) -> Unit) {
    when (state) {
        Remote.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is Remote.Failed -> Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(state.message, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.retry)) }
        }
        is Remote.Ready -> content(state.value)
    }
}
