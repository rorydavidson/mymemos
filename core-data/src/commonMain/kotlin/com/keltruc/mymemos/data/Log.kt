package com.keltruc.mymemos.data

/** Whatever the platform calls its log. Android's Logcat; a Mac's console. */
internal expect object Log {
    fun w(tag: String, message: String, error: Throwable? = null)
    fun e(tag: String, message: String, error: Throwable? = null)
}
