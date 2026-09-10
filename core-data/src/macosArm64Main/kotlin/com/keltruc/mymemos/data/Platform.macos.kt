package com.keltruc.mymemos.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSString
import platform.Foundation.URLQueryAllowedCharacterSet
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.posix.ECONNREFUSED
import platform.posix.ENETDOWN
import platform.posix.ENETUNREACH

@OptIn(ExperimentalForeignApi::class)
internal actual fun deviceName(): String = NSProcessInfo.processInfo.hostName

@Suppress("CAST_NEVER_SUCCEEDS")
internal actual fun encodePathSegment(value: String): String =
    (value as NSString).stringByAddingPercentEncodingWithAllowedCharacters(
        NSCharacterSet.URLQueryAllowedCharacterSet,
    ) ?: value

/**
 * Ktor's Darwin engine surfaces a lost connection as its own exception type rather than
 * anything posix, so this is deliberately broad: anything that is not the server answering
 * counts as the network being away, which is what the retry logic above wants to know.
 */
internal actual fun isNetworkFailure(error: Throwable): Boolean =
    error !is com.keltruc.mymemos.network.ApiException
