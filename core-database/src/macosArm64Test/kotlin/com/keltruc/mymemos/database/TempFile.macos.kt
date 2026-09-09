package com.keltruc.mymemos.database

import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.posix.getpid

@OptIn(ExperimentalForeignApi::class)
fun tempFilePath(name: String): String =
    NSTemporaryDirectory() + "room-kmp-spike-${getpid()}-${counter++}-$name"

private var counter = 0

@OptIn(ExperimentalForeignApi::class)
fun deleteFile(path: String) {
    listOf("", "-wal", "-shm").forEach {
        NSFileManager.defaultManager.removeItemAtPath(path + it, null)
    }
}
