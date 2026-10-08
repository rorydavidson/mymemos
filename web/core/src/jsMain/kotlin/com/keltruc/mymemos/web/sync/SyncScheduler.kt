package com.keltruc.mymemos.web.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * When to sync. A write asks for one at once, debounced so a burst of keystrokes is one run;
 * a delete asks for one later so Undo can still work. Background catch-up is the page's
 * business (it calls [syncNow] on load, on reconnect, when the tab comes back, and on a timer),
 * since a browser has no WorkManager and a closed tab runs nothing.
 */
class SyncScheduler(private val scope: CoroutineScope, private val run: suspend () -> Unit) {
    private var pending: Job? = null
    private var dueAt = 0.0

    fun syncNow(afterMs: Long = DEBOUNCE_MS) {
        val at = currentTimeMs() + afterMs
        // A sooner request replaces a later one; a later one never delays a sooner one.
        if (pending?.isActive == true && dueAt <= at) return
        pending?.cancel()
        dueAt = at
        pending = scope.launch {
            delay(afterMs)
            run()
        }
    }

    private fun currentTimeMs(): Double = js("Date.now()").unsafeCast<Double>()

    private companion object {
        const val DEBOUNCE_MS = 800L
    }
}
