package com.keltruc.mymemos.secrets

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.keltruc.mymemos.data.crypto.RememberedPassword

/**
 * The Android half of [RememberedPassword]: Keystore-backed encrypted preferences.
 *
 * androidx.security.crypto is deprecated upstream and will need replacing, which is a
 * separate job from this one; keeping it behind an interface is what makes that possible
 * without touching anything above.
 */
@Suppress("DEPRECATION")
class KeystoreRememberedPassword(context: Context) : RememberedPassword {

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "mymemos_memo_password",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun read(): CharArray? = prefs.getString(KEY, null)?.toCharArray()

    override fun write(password: CharArray) {
        prefs.edit().putString(KEY, String(password)).apply()
    }

    override fun forget() {
        prefs.edit().remove(KEY).apply()
    }

    override fun isRemembered(): Boolean = prefs.contains(KEY)

    private companion object {
        const val KEY = "memo_password"
    }
}
