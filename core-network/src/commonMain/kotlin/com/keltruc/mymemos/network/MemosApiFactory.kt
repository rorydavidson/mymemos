package com.keltruc.mymemos.network

import com.keltruc.mymemos.network.api.KtorMemosApi
import com.keltruc.mymemos.network.api.MemosApi
import com.keltruc.mymemos.network.auth.PersistentCookiesStorage
import com.keltruc.mymemos.network.auth.TokenStore
import com.keltruc.mymemos.network.dto.RefreshTokenResponseDto
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.http.URLBuilder
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/** Builds one authenticated client per (server, credential). */
class MemosApiFactory(
    private val engine: HttpClientEngine? = null,
    private val logBodies: Boolean = false,
) {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
    }

    data class Built(val api: MemosApi, val client: HttpClient, val cookies: PersistentCookiesStorage)

    fun create(serverUrl: String, tokenStore: TokenStore): Built {
        val baseUrl = normaliseBaseUrl(serverUrl)
        val cookies = PersistentCookiesStorage(tokenStore)

        // No Auth plugin on this one: the refresh call must not recurse into itself.
        val plain = buildClient(baseUrl) {
            install(HttpCookies) { storage = cookies }
        }

        val authed = plain.config {
            install(Auth) {
                bearer {
                    loadTokens { tokenStore.accessToken()?.let { BearerTokens(it, null) } }
                    refreshTokens {
                        // A personal access token is long-lived and cannot be refreshed.
                        if (tokenStore.isPersonalAccessToken()) return@refreshTokens null
                        val response = plain.post("api/v1/auth/refresh")
                        if (!response.status.isSuccess()) return@refreshTokens null
                        val refreshed: RefreshTokenResponseDto = response.body()
                        if (refreshed.accessToken.isEmpty()) return@refreshTokens null
                        tokenStore.updateAccessToken(refreshed.accessToken, refreshed.expiresAt)
                        BearerTokens(refreshed.accessToken, null)
                    }
                    // Otherwise Ktor withholds the header until it has seen a 401.
                    sendWithoutRequest { true }
                }
            }
        }
        return Built(KtorMemosApi(authed), authed, cookies)
    }

    /** Unauthenticated client, for probing a server before sign-in. */
    fun createAnonymous(serverUrl: String): MemosApi = KtorMemosApi(buildClient(normaliseBaseUrl(serverUrl)))

    private fun buildClient(baseUrl: String, extra: HttpClientConfig<*>.() -> Unit = {}): HttpClient {
        val config: HttpClientConfig<*>.() -> Unit = {
            install(ContentNegotiation) { json(json) }
            install(HttpTimeout) {
                connectTimeoutMillis = 20_000
                requestTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
            if (logBodies) {
                install(Logging) {
                    // Headers only, and never the credential itself: BODY would put the
                    // sign-in password and every memo into the log on a debug build.
                    level = LogLevel.HEADERS
                    sanitizeHeader { it.equals("Authorization", ignoreCase = true) }
                    sanitizeHeader { it.equals("Cookie", ignoreCase = true) }
                    sanitizeHeader { it.equals("Set-Cookie", ignoreCase = true) }
                }
            }
            defaultRequest { url.takeFrom(URLBuilder().takeFrom(baseUrl)) }
            // Ktor stays quiet about a non-2xx unless asked. Retrofit threw HttpException and
            // the repositories translated it; they now translate ResponseException the same
            // way, through ApiException.from.
            expectSuccess = true
            extra()
        }
        return if (engine != null) HttpClient(engine, config) else HttpClient(config)
    }

    companion object {
        fun normaliseBaseUrl(input: String): String {
            var url = input.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            return url.trimEnd('/') + "/"
        }
    }
}
