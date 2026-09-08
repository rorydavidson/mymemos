package com.keltruc.mymemos.network

import com.keltruc.mymemos.network.api.MemosApi
import com.keltruc.mymemos.network.auth.BearerInterceptor
import com.keltruc.mymemos.network.auth.PersistentCookieJar
import com.keltruc.mymemos.network.auth.RefreshAuthenticator
import com.keltruc.mymemos.network.auth.TokenStore
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Builds one authenticated client per (server, credential). */
class MemosApiFactory(
    private val baseClient: OkHttpClient = OkHttpClient(),
    private val logBodies: Boolean = false,
) {
    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = false
        coerceInputValues = true
    }

    data class Built(val api: MemosApi, val client: OkHttpClient, val cookieJar: PersistentCookieJar)

    fun create(serverUrl: String, tokenStore: TokenStore): Built {
        val cookieJar = PersistentCookieJar(tokenStore)
        val plain = baseClient.newBuilder()
            .cookieJar(cookieJar)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .apply {
                // Headers only, and never the credential itself: BODY would put the sign-in
                // password and every memo into logcat on a debug build.
                if (logBodies) {
                    addInterceptor(
                        HttpLoggingInterceptor().apply {
                            setLevel(HttpLoggingInterceptor.Level.HEADERS)
                            redactHeader("Authorization")
                            redactHeader("Cookie")
                            redactHeader("Set-Cookie")
                        },
                    )
                }
            }
            .build()
        val authed = plain.newBuilder()
            .addInterceptor(BearerInterceptor(tokenStore))
            .authenticator(RefreshAuthenticator(tokenStore, json) { plain })
            .build()
        val retrofit = Retrofit.Builder()
            .baseUrl(normaliseBaseUrl(serverUrl))
            .client(authed)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return Built(retrofit.create(MemosApi::class.java), authed, cookieJar)
    }

    /** Unauthenticated client, for probing a server before sign-in. */
    fun createAnonymous(serverUrl: String): MemosApi =
        Retrofit.Builder()
            .baseUrl(normaliseBaseUrl(serverUrl))
            .client(baseClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(MemosApi::class.java)

    companion object {
        fun normaliseBaseUrl(input: String): String {
            var url = input.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://$url"
            return url.trimEnd('/') + "/"
        }
    }
}
