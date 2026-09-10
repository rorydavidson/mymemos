package com.keltruc.mymemos.data

/** What to call this device when naming a token it minted for itself. */
internal expect fun deviceName(): String

/** Percent-encoding for a path segment, which every platform spells differently. */
internal expect fun encodePathSegment(value: String): String

/** True when a failure is the network being unavailable rather than the server objecting. */
internal expect fun isNetworkFailure(error: Throwable): Boolean
