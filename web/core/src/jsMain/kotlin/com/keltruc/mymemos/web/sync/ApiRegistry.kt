package com.keltruc.mymemos.web.sync

import com.keltruc.mymemos.network.MemosApiFactory
import com.keltruc.mymemos.network.api.MemosApi
import com.keltruc.mymemos.network.auth.TokenStore
import io.ktor.client.HttpClient

/**
 * One API client per (server, credential), as ApiClientRegistry does in the apps. The web
 * only ever holds a personal access token, so its [TokenStore] is a fixed string: there is
 * nothing to refresh and no cookie jar to keep (the browser has its own).
 */
class ApiRegistry(private val factory: MemosApiFactory = MemosApiFactory()) {

    class Client(val api: MemosApi, val http: HttpClient)

    private val clients = HashMap<String, Client>()

    fun client(serverUrl: String, token: String): Client =
        clients.getOrPut("$serverUrl|$token") {
            val built = factory.create(serverUrl, FixedToken(token))
            Client(built.api, built.client)
        }

    fun api(serverUrl: String, token: String): MemosApi = client(serverUrl, token).api

    fun anonymous(serverUrl: String): MemosApi = factory.createAnonymous(serverUrl)

    fun evict(serverUrl: String, token: String) {
        clients.remove("$serverUrl|$token")?.http?.close()
    }

    private class FixedToken(private val token: String) : TokenStore {
        override suspend fun accessToken(): String = token
        override suspend fun updateAccessToken(token: String, expiresAt: String?) = Unit
        override suspend fun isPersonalAccessToken(): Boolean = true
        override suspend fun cookies(): String? = null
        override suspend fun saveCookies(serialised: String) = Unit
    }
}
