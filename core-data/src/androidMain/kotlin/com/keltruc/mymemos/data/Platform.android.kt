package com.keltruc.mymemos.data

import java.io.IOException
import java.net.URLEncoder

internal actual fun deviceName(): String =
    listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replaceFirstChar { it.uppercase() }

internal actual fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, "UTF-8").replace("+", "%20")

internal actual fun isNetworkFailure(error: Throwable): Boolean = error is IOException
