package com.keltruc.mymemos.data.crypto

import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Exactly the primitives this app has always used, moved behind the expect declaration and
 * otherwise untouched, so every memo already locked on a phone still opens.
 */
internal actual object AesGcm {

    actual fun randomBytes(size: Int): ByteArray = ByteArray(size).also { SecureRandom().nextBytes(it) }

    actual fun deriveKey(password: CharArray, salt: ByteArray, iterations: Int, keyBits: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(password, salt, iterations, keyBits)).encoded

    actual fun seal(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return cipher.doFinal(plaintext)
    }

    actual fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray): ByteArray? {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        return try {
            cipher.doFinal(sealed)
        } catch (e: AEADBadTagException) {
            null
        }
    }
}
