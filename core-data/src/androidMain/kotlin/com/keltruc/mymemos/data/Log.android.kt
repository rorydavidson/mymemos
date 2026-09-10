package com.keltruc.mymemos.data

internal actual object Log {
    actual fun w(tag: String, message: String, error: Throwable?) {
        android.util.Log.w(tag, message, error)
    }

    actual fun e(tag: String, message: String, error: Throwable?) {
        android.util.Log.e(tag, message, error)
    }
}
