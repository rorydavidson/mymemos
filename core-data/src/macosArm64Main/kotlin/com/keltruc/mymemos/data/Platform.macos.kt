package com.keltruc.mymemos.data

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSProcessInfo

/** Names the token minted at sign-in, so the server's token list says which machine it is. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun deviceName(): String = NSProcessInfo.processInfo.hostName
