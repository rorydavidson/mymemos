package com.keltruc.mymemos.navigation

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Where an incoming intent wants the UI to go. Consumed once by the nav host. */
sealed interface Destination {
    data class NewMemo(val text: String = "", val imageUris: List<Uri> = emptyList()) : Destination
    data class OpenMemo(val localId: String) : Destination
}

@Singleton
class IntentRouter @Inject constructor() {
    private val _pending = MutableStateFlow<Destination?>(null)
    val pending: StateFlow<Destination?> = _pending

    fun consume() { _pending.value = null }

    fun handle(intent: Intent?) {
        intent ?: return
        val destination = when (intent.action) {
            Intent.ACTION_SEND -> {
                val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
                    .filter { it.isNotBlank() }.distinct().joinToString("\n")
                val image = if (intent.type?.startsWith("image/") == true) intent.getParcelableExtraCompat(Intent.EXTRA_STREAM) else null
                Destination.NewMemo(text, listOfNotNull(image))
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val images = intent.getParcelableArrayListExtraCompat(Intent.EXTRA_STREAM)
                Destination.NewMemo(intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty(), images)
            }
            ACTION_NEW_MEMO -> Destination.NewMemo(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            ACTION_OPEN_MEMO -> intent.getStringExtra(EXTRA_MEMO_LOCAL_ID)?.let { Destination.OpenMemo(it) }
            else -> null
        } ?: return
        _pending.value = destination
    }

    private fun Intent.getParcelableExtraCompat(key: String): Uri? =
        if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableExtra(key, Uri::class.java) else @Suppress("DEPRECATION") getParcelableExtra(key)

    private fun Intent.getParcelableArrayListExtraCompat(key: String): List<Uri> =
        (if (android.os.Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(key, Uri::class.java) else @Suppress("DEPRECATION") getParcelableArrayListExtra(key)).orEmpty()

    companion object {
        const val ACTION_NEW_MEMO = "com.keltruc.mymemos.action.NEW_MEMO"
        const val ACTION_OPEN_MEMO = "com.keltruc.mymemos.action.OPEN_MEMO"
        const val EXTRA_TEXT = "text"
        const val EXTRA_MEMO_LOCAL_ID = "memoLocalId"
    }
}
