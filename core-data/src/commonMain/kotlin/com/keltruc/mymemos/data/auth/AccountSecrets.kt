package com.keltruc.mymemos.data.auth

import com.keltruc.mymemos.network.auth.TokenStore

/**
 * One account's credentials. Where these actually live is the platform's business: the
 * Android Keystore today, the Keychain on a Mac. Nothing above this line should care.
 */
interface AccountSecrets : TokenStore {

    suspend fun setPersonalAccessToken(token: String, tokenResourceName: String? = null)

    /** Server name of the token this app minted for itself, if any, so sign-out can revoke it. */
    suspend fun mintedTokenName(): String?

    suspend fun setPasswordSession(accessToken: String, expiresAt: String?)

    suspend fun clear()

    companion object {
        /** Stable key for an account before it has a database id. */
        fun accountKey(serverUrl: String, userResourceName: String): String =
            "${serverUrl.trimEnd('/')}|$userResourceName"
    }
}

/** Builds the per-account store. Implemented by whichever platform is hosting the app. */
fun interface AccountSecretsFactory {
    fun create(accountKey: String): AccountSecrets
}
