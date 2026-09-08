package com.keltruc.mymemos.data.auth

import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.database.entity.AccountEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Snapshot of the active account for callers that cannot suspend, such as the image
 * loader's interceptor.
 */
@Singleton
class ActiveSession @Inject constructor(
    accountDao: AccountDao,
    private val registry: ApiClientRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val account: StateFlow<AccountEntity?> = accountDao.observeActive().stateIn(scope, SharingStarted.Eagerly, null)

    /** Adds the active account's bearer token to requests aimed at its server. */
    fun imageAuthInterceptor(): Interceptor = Interceptor { chain ->
        val acc = account.value
        val request = chain.request()
        if (acc != null && sameOrigin(request.url, acc.serverUrl)) {
            val token = runBlocking { registry.tokenStore(acc.serverUrl, acc.userResourceName).accessToken() }
            if (!token.isNullOrEmpty()) {
                return@Interceptor chain.proceed(request.newBuilder().header("Authorization", "Bearer $token").build())
            }
        }
        chain.proceed(request)
    }

    /**
     * Scheme, host and port must match exactly. A plain string-prefix check would also match
     * `https://server.example.evil.net`, handing the token to whoever controls an image URL.
     */
    companion object {
        internal fun sameOrigin(url: HttpUrl, serverUrl: String): Boolean {
            val server = serverUrl.toHttpUrlOrNull() ?: return false
            return url.scheme == server.scheme && url.host.equals(server.host, ignoreCase = true) && url.port == server.port
        }
    }
}
