package com.keltruc.mymemos.data

internal actual object Log {
    actual fun w(tag: String, message: String, error: Throwable?) = print("W", tag, message, error)

    actual fun e(tag: String, message: String, error: Throwable?) = print("E", tag, message, error)

    private fun print(level: String, tag: String, message: String, error: Throwable?) {
        println("$level/$tag: $message" + (error?.let { ": $it" } ?: ""))
    }
}
