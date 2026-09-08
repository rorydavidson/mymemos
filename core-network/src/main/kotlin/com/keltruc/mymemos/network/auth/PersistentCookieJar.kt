package com.keltruc.mymemos.network.auth

import kotlinx.coroutines.runBlocking
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Keeps cookies across process restarts via [TokenStore]. Only the Memos refresh cookie
 * matters; the jar is per-account because each account has its own client.
 */
class PersistentCookieJar(private val tokenStore: TokenStore) : CookieJar {
    private val cookies = mutableMapOf<String, Cookie>()
    private var loaded = false

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this) {
            ensureLoaded(url)
            cookies.forEach { this.cookies[it.name] = it }
            persist()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(this) {
        ensureLoaded(url)
        val now = System.currentTimeMillis()
        cookies.values.filter { it.expiresAt > now && it.matches(url) }
    }

    fun clear() = synchronized(this) {
        cookies.clear()
        persist()
    }

    private fun ensureLoaded(url: HttpUrl) {
        if (loaded) return
        loaded = true
        val raw = runBlocking { tokenStore.cookies() } ?: return
        raw.lineSequence().filter { it.isNotBlank() }
            .mapNotNull { Cookie.parse(url, it) }
            .forEach { cookies[it.name] = it }
    }

    private fun persist() {
        val serialised = cookies.values.joinToString("\n") { it.toString() }
        runBlocking { tokenStore.saveCookies(serialised) }
    }
}
