package com.keltruc.mymemos.network.auth

/**
 * Supplies and persists the credential for one server. Implemented by the data layer so
 * the network module stays free of Android storage APIs.
 */
interface TokenStore {
    /** Bearer token to attach to requests, or null if none yet. */
    suspend fun accessToken(): String?

    /** Called after a successful refresh so the new short-lived token is kept. */
    suspend fun updateAccessToken(token: String, expiresAt: String?)

    /** True when the credential is a long-lived PAT; refresh is then never attempted. */
    suspend fun isPersonalAccessToken(): Boolean

    /** Serialised cookie jar (holds the HttpOnly refresh cookie). */
    suspend fun cookies(): String?

    suspend fun saveCookies(serialised: String)
}
