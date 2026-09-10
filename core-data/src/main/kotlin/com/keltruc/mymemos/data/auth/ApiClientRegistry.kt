package com.keltruc.mymemos.data.auth

import com.keltruc.mymemos.network.MemosApiFactory
import com.keltruc.mymemos.network.api.MemosApi

/** One authenticated [MemosApi] per account, built lazily and reused. */
class ApiClientRegistry(
    private val factory: MemosApiFactory,
    private val secrets: AccountSecretsFactory,
) {
    private class Entry(val api: MemosApi, val tokenStore: AccountSecrets, val built: MemosApiFactory.Built)

    private val entries = mutableMapOf<String, Entry>()

    fun tokenStore(serverUrl: String, userResourceName: String): AccountSecrets =
        entry(serverUrl, userResourceName).tokenStore

    fun api(serverUrl: String, userResourceName: String): MemosApi = entry(serverUrl, userResourceName).api

    fun anonymousApi(serverUrl: String): MemosApi = factory.createAnonymous(serverUrl)

    fun evict(serverUrl: String, userResourceName: String) {
        synchronized(entries) { entries.remove(AccountSecrets.accountKey(serverUrl, userResourceName)) }
    }

    private fun entry(serverUrl: String, userResourceName: String): Entry = synchronized(entries) {
        val key = AccountSecrets.accountKey(serverUrl, userResourceName)
        entries.getOrPut(key) {
            val store = secrets.create(key)
            val built = factory.create(serverUrl, store)
            Entry(built.api, store, built)
        }
    }
}
