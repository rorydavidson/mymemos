package com.keltruc.mymemos.data.auth

import com.keltruc.mymemos.network.MemosApiFactory
import com.keltruc.mymemos.network.api.MemosApi
import kotlin.concurrent.atomics.AtomicReference

/** One authenticated [MemosApi] per account, built lazily and reused. */
@OptIn(kotlin.concurrent.atomics.ExperimentalAtomicApi::class)
class ApiClientRegistry(
    private val factory: MemosApiFactory,
    private val secrets: AccountSecretsFactory,
) {
    private class Entry(val api: MemosApi, val tokenStore: AccountSecrets, val built: MemosApiFactory.Built)

    // Copy on write rather than a lock: there is no portable `synchronized`, the map is
    // tiny, and it is read far more often than it is written.
    private val entries = AtomicReference<Map<String, Entry>>(emptyMap())

    fun tokenStore(serverUrl: String, userResourceName: String): AccountSecrets =
        entry(serverUrl, userResourceName).tokenStore

    fun api(serverUrl: String, userResourceName: String): MemosApi = entry(serverUrl, userResourceName).api

    fun anonymousApi(serverUrl: String): MemosApi = factory.createAnonymous(serverUrl)

    fun evict(serverUrl: String, userResourceName: String) {
        val key = AccountSecrets.accountKey(serverUrl, userResourceName)
        while (true) {
            val current = entries.load()
            if (key !in current) return
            if (entries.compareAndSet(current, current - key)) return
        }
    }

    private fun entry(serverUrl: String, userResourceName: String): Entry {
        val key = AccountSecrets.accountKey(serverUrl, userResourceName)
        while (true) {
            val current = entries.load()
            current[key]?.let { return it }
            val store = secrets.create(key)
            val built = factory.create(serverUrl, store)
            val entry = Entry(built.api, store, built)
            if (entries.compareAndSet(current, current + (key to entry))) return entry
        }
    }
}
