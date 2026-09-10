package shared

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.CValuesRef
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * The macOS Keychain, which is where a credential belongs on this platform.
 *
 * Everything is one generic-password item per key under a single service, mirroring how the
 * Android side namespaces its encrypted preferences, so an account's token and its cookies sit
 * beside each other and a sign-out can clear both.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
internal object Keychain {

    private const val SERVICE = "com.keltruc.mymemos"

    fun read(key: String): String? = memScoped {
        val query = query(key)
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)
        val result = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)
        CFRelease(query)
        if (status != errSecSuccess) return@memScoped null
        val data = CFBridgingRelease(result.value) as? NSData ?: return@memScoped null
        NSString.create(data, NSUTF8StringEncoding) as String?
    }

    fun write(key: String, value: String) {
        delete(key)
        val item = query(key)
        val data = (value as NSString).dataUsingEncoding(NSUTF8StringEncoding) ?: return
        CFDictionaryAddValue(item, kSecValueData, CFBridgingRetain(data))
        SecItemAdd(item as CFDictionaryRef, null)
        CFRelease(item)
    }

    fun delete(key: String) {
        val query = query(key)
        SecItemDelete(query as CFDictionaryRef)
        CFRelease(query)
    }

    private fun query(key: String): CFMutableDictionaryRef {
        val dictionary = CFDictionaryCreateMutable(null, 0, null, null)!!
        CFDictionaryAddValue(dictionary, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(dictionary, kSecAttrService, CFBridgingRetain(SERVICE as NSString))
        CFDictionaryAddValue(dictionary, kSecAttrAccount, CFBridgingRetain(key as NSString))
        return dictionary
    }
}

private fun CFDictionaryAddValue(dictionary: CFMutableDictionaryRef, key: Any?, value: Any?) {
    platform.CoreFoundation.CFDictionaryAddValue(
        dictionary,
        key as CValuesRef<*>?,
        value as CValuesRef<*>?,
    )
}
