package com.keltruc.mymemos.network.auth

import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.http.Cookie
import io.ktor.http.Url
import io.ktor.http.parseServerSetCookieHeader
import io.ktor.http.renderSetCookieHeader
import io.ktor.util.date.GMTDate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps cookies across process restarts via [TokenStore]. Only the Memos refresh cookie
 * matters; the storage is per-account because each account has its own client.
 *
 * The serialised form is one `Set-Cookie` line per cookie, which is what the OkHttp jar
 * this replaced already wrote, so an install upgrading in place still finds its refresh
 * cookie rather than being silently signed out.
 */
class PersistentCookiesStorage(private val tokenStore: TokenStore) : CookiesStorage {

    private val mutex = Mutex()
    private val cookies = mutableMapOf<String, Cookie>()
    private var loaded = false

    override suspend fun get(requestUrl: Url): List<Cookie> = mutex.withLock {
        ensureLoaded()
        val now = GMTDate().timestamp
        cookies.values.filter { cookie -> cookie.expires?.let { it.timestamp > now } ?: true }
    }

    override suspend fun addCookie(requestUrl: Url, cookie: Cookie) = mutex.withLock {
        ensureLoaded()
        cookies[cookie.name] = cookie
        persist()
    }

    suspend fun clear() = mutex.withLock {
        loaded = true
        cookies.clear()
        persist()
    }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loaded = true
        val raw = tokenStore.cookies() ?: return
        raw.lineSequence()
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { parseServerSetCookieHeader(it) }.getOrNull() }
            .forEach { cookies[it.name] = it }
    }

    private suspend fun persist() {
        tokenStore.saveCookies(cookies.values.joinToString("\n") { renderSetCookieHeader(it) })
    }

    override fun close() = Unit
}
