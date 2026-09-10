package com.keltruc.mymemos.data.account

import kotlin.io.encoding.Base64

/**
 * Where an account's avatar actually lives.
 *
 * Memos stores it as a `data:` URI, a path on the server, or an absolute URL to somewhere
 * else entirely, and the three want different handling. Shared rather than written per
 * platform so an avatar that shows on the phone shows on the Mac.
 */
sealed interface AvatarSource {

    /** Nothing to show; the caller falls back to an initial. */
    data object None : AvatarSource

    /** Already in hand, from a `data:` URI. */
    data class Bytes(val bytes: ByteArray) : AvatarSource {
        override fun equals(other: Any?): Boolean =
            this === other || (other is Bytes && bytes.contentEquals(other.bytes))

        override fun hashCode(): Int = bytes.contentHashCode()
    }

    /**
     * Fetch it. [sameOrigin] says whether it is the account's own server, and so whether the
     * credential may go with the request: an avatar URL pointing somewhere else is a way to
     * collect bearer tokens from anyone who renders it.
     */
    data class Url(val url: String, val sameOrigin: Boolean) : AvatarSource

    companion object {
        fun of(url: String, serverUrl: String): AvatarSource = when {
            url.isBlank() -> None

            url.startsWith("data:") -> {
                val encoded = url.substringAfter(",", "")
                val bytes = runCatching { Base64.Default.decode(encoded) }.getOrNull()
                if (bytes == null || bytes.isEmpty()) None else Bytes(bytes)
            }

            url.startsWith("http://") || url.startsWith("https://") ->
                Url(url, sameOrigin = origin(url) != null && origin(url) == origin(serverUrl))

            else -> Url(serverUrl.trimEnd('/') + "/" + url.trimStart('/'), sameOrigin = true)
        }

        /**
         * Scheme, host and port. Compared whole rather than by prefix, because
         * `https://server.example.evil.net` starts with `https://server.example` and a prefix
         * check would hand it the token.
         */
        private fun origin(url: String): String? {
            val scheme = url.substringBefore("://", "").lowercase()
            if (scheme.isEmpty()) return null
            val authority = url.substringAfter("://", "").substringBefore('/')
            if (authority.isEmpty()) return null
            val host = authority.substringBefore(':').lowercase()
            val port = authority.substringAfter(':', "").ifEmpty {
                if (scheme == "https") "443" else "80"
            }
            return "$scheme://$host:$port"
        }
    }
}
