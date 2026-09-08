package com.keltruc.mymemos.data.auth

import android.content.Context
import com.keltruc.mymemos.network.MemosApiFactory
import com.keltruc.mymemos.network.api.MemosApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** One authenticated [MemosApi] per account, built lazily and reused. */
@Singleton
class ApiClientRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
    private val factory: MemosApiFactory,
) {
    private class Entry(val api: MemosApi, val tokenStore: SecureTokenStore, val built: MemosApiFactory.Built)

    private val entries = mutableMapOf<String, Entry>()

    fun tokenStore(serverUrl: String, userResourceName: String): SecureTokenStore =
        entry(serverUrl, userResourceName).tokenStore

    fun api(serverUrl: String, userResourceName: String): MemosApi = entry(serverUrl, userResourceName).api

    fun anonymousApi(serverUrl: String): MemosApi = factory.createAnonymous(serverUrl)

    fun evict(serverUrl: String, userResourceName: String) {
        synchronized(entries) { entries.remove(SecureTokenStore.accountKey(serverUrl, userResourceName)) }
    }

    private fun entry(serverUrl: String, userResourceName: String): Entry = synchronized(entries) {
        val key = SecureTokenStore.accountKey(serverUrl, userResourceName)
        entries.getOrPut(key) {
            val store = SecureTokenStore(context, key)
            val built = factory.create(serverUrl, store)
            Entry(built.api, store, built)
        }
    }
}
