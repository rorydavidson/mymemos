package com.keltruc.mymemos.network.auth

import com.keltruc.mymemos.network.dto.RefreshTokenResponseDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * On 401, calls `auth/refresh` once (the refresh cookie rides in the shared jar) and
 * retries with the new access token. Gives up if the credential is a PAT, if the failing
 * request was itself the refresh, or if a retry already happened.
 */
class RefreshAuthenticator(
    private val tokenStore: TokenStore,
    private val json: Json,
    /** Client without this authenticator, to avoid recursion. */
    private val refreshClient: () -> OkHttpClient,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        if (response.request.url.encodedPath.endsWith("/auth/refresh")) return null
        if (responseCount(response) >= 2) return null
        if (runBlocking { tokenStore.isPersonalAccessToken() }) return null

        val refreshUrl = response.request.url.newBuilder()
            .encodedPath("/api/v1/auth/refresh").query(null).build()
        val refreshRequest = Request.Builder()
            .url(refreshUrl)
            .post("{}".toRequestBody("application/json".toMediaTypeOrNull()))
            .build()

        val newToken = runCatching {
            refreshClient().newCall(refreshRequest).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                json.decodeFromString<RefreshTokenResponseDto>(r.body.string())
            }
        }.getOrNull() ?: return null
        if (newToken.accessToken.isEmpty()) return null

        runBlocking { tokenStore.updateAccessToken(newToken.accessToken, newToken.expiresAt) }
        return response.request.newBuilder()
            .header("Authorization", "Bearer ${newToken.accessToken}")
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
