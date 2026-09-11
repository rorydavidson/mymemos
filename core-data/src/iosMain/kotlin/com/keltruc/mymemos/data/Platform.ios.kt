package com.keltruc.mymemos.data

import platform.UIKit.UIDevice

/**
 * Names the token minted at sign-in. The host name of a phone is meaningless, so this is the
 * name the owner gave it, which is what the server's token list should say.
 */
internal actual fun deviceName(): String = UIDevice.currentDevice.name
