package com.keltruc.mymemos.data.crypto

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Where a remembered memo password is kept between launches. Keystore-backed preferences on
 * Android, the Keychain on a Mac; either way it never leaves the device.
 */
interface RememberedPassword {
    fun read(): CharArray?
    fun write(password: CharArray)
    fun forget()
    fun isRemembered(): Boolean
}

/**
 * The memo password for the current process. Held in memory by default; "remember on this
 * device" hands it to [RememberedPassword] so locked memos open without a prompt. Forgetting
 * clears both.
 */
class PasswordSession(private val remembered: RememberedPassword) {

    private val _password = MutableStateFlow(remembered.read())
    val password: StateFlow<CharArray?> = _password

    val isRemembered: Boolean get() = remembered.isRemembered()

    fun current(): CharArray? = _password.value

    fun set(password: CharArray, remember: Boolean) {
        _password.value = password
        if (remember) remembered.write(password) else remembered.forget()
    }

    fun forget() {
        _password.value?.fill(' ')
        _password.value = null
        remembered.forget()
    }
}
