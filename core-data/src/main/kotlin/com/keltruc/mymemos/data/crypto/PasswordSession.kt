package com.keltruc.mymemos.data.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The memo password for the current process. Held in memory by default; "remember on this
 * device" keeps it in Keystore-backed encrypted preferences so locked memos open without
 * a prompt. Forgetting clears both.
 */
class PasswordSession constructor(context: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mymemos_memo_password",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _password = MutableStateFlow<CharArray?>(prefs.getString(KEY, null)?.toCharArray())
    val password: StateFlow<CharArray?> = _password
    val isRemembered: Boolean get() = prefs.contains(KEY)

    fun current(): CharArray? = _password.value

    fun set(password: CharArray, remember: Boolean) {
        _password.value = password
        if (remember) prefs.edit().putString(KEY, String(password)).apply() else prefs.edit().remove(KEY).apply()
    }

    fun forget() {
        _password.value?.fill(' ')
        _password.value = null
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val KEY = "memo_password"
    }
}
