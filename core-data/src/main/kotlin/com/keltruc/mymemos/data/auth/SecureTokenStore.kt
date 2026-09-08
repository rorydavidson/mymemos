package com.keltruc.mymemos.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.keltruc.mymemos.network.auth.TokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Credentials for one account, kept in EncryptedSharedPreferences (Keystore-backed).
 * Keys are namespaced by account so several accounts can coexist in one file.
 */
class SecureTokenStore(context: Context, private val accountKey: String) : TokenStore {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override suspend fun accessToken(): String? = withContext(Dispatchers.IO) {
        prefs.getString(key(ACCESS_TOKEN), null)
    }

    override suspend fun updateAccessToken(token: String, expiresAt: String?) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key(ACCESS_TOKEN), token).putString(key(EXPIRES_AT), expiresAt).apply()
    }

    override suspend fun isPersonalAccessToken(): Boolean = withContext(Dispatchers.IO) {
        prefs.getBoolean(key(IS_PAT), false)
    }

    override suspend fun cookies(): String? = withContext(Dispatchers.IO) {
        prefs.getString(key(COOKIES), null)
    }

    override suspend fun saveCookies(serialised: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(key(COOKIES), serialised).apply()
    }

    suspend fun setPersonalAccessToken(token: String) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(key(ACCESS_TOKEN), token)
            .putBoolean(key(IS_PAT), true)
            .remove(key(EXPIRES_AT))
            .apply()
    }

    suspend fun setPasswordSession(accessToken: String, expiresAt: String?) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(key(ACCESS_TOKEN), accessToken)
            .putString(key(EXPIRES_AT), expiresAt)
            .putBoolean(key(IS_PAT), false)
            .apply()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(key(ACCESS_TOKEN)).remove(key(EXPIRES_AT)).remove(key(IS_PAT)).remove(key(COOKIES))
            .apply()
    }

    private fun key(suffix: String) = "$accountKey.$suffix"

    companion object {
        private const val FILE_NAME = "mymemos_secure"
        private const val ACCESS_TOKEN = "access_token"
        private const val EXPIRES_AT = "expires_at"
        private const val IS_PAT = "is_pat"
        private const val COOKIES = "cookies"

        /** Stable key for an account before it has a database id. */
        fun accountKey(serverUrl: String, userResourceName: String): String =
            "${serverUrl.trimEnd('/')}|$userResourceName"
    }
}
