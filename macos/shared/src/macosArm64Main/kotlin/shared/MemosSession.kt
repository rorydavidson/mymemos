package shared

import com.keltruc.mymemos.network.MemosApiFactory
import com.keltruc.mymemos.network.api.MemosApi
import com.keltruc.mymemos.network.auth.TokenStore
import com.keltruc.mymemos.network.dto.PasswordCredentialsDto
import com.keltruc.mymemos.network.dto.SignInRequestDto
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * What the macOS app talks to. Deliberately small and concrete: Swift gets suspend functions
 * as `async` for free, but only off a plain class, and it cannot extend a generic Kotlin type,
 * so everything crossing over is a plain class or a list of them.
 *
 * This is a vertical slice over core-network and core-model. The sync engine and the local
 * database are still Android-only until core-data is ported, so there is no offline store
 * behind this yet: it reads from the server and shows what comes back.
 */
class MemosSession {

    /** In memory only. The Keychain-backed store lands with the rest of core-data. */
    private class MemoryTokenStore : TokenStore {
        var token: String? = null
        var cookieBlob: String? = null
        override suspend fun accessToken() = token
        override suspend fun updateAccessToken(token: String, expiresAt: String?) { this.token = token }
        override suspend fun isPersonalAccessToken() = false
        override suspend fun cookies() = cookieBlob
        override suspend fun saveCookies(serialised: String) { cookieBlob = serialised }
    }

    private val store = MemoryTokenStore()
    private val factory = MemosApiFactory()
    private var api: MemosApi? = null

    var serverVersion: String = ""
        private set
    var displayName: String = ""
        private set

    /** Confirms the address really is a Memos server before anyone types a password at it. */
    suspend fun probe(serverUrl: String): String {
        val version = factory.createAnonymous(serverUrl).getInstanceProfile().version
        serverVersion = version
        return version
    }

    suspend fun signIn(serverUrl: String, username: String, password: String) {
        val built = factory.create(serverUrl, store)
        val response = built.api.signIn(SignInRequestDto(PasswordCredentialsDto(username, password)))
        store.token = response.accessToken
        displayName = response.user.displayName.ifEmpty { response.user.username }
        api = built.api
    }

    suspend fun memos(): List<MemoRow> {
        val current = api ?: error("not signed in")
        return current.listMemos(pageSize = 50, state = "NORMAL").memos.map { dto ->
            MemoRow(
                name = dto.name,
                content = dto.content,
                pinned = dto.pinned,
                locked = dto.content.trimStart().startsWith(LOCKED_PREFIX),
                createdLabel = label(dto.createTime),
            )
        }
    }

    private fun label(rfc3339: String?): String {
        val instant = rfc3339?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return ""
        val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
        return "${local.dayOfMonth} ${local.month.name.lowercase().replaceFirstChar { it.uppercase() }} " +
            "${local.year}, ${local.hour.pad()}:${local.minute.pad()}"
    }

    private fun Int.pad() = toString().padStart(2, '0')

    private companion object {
        const val LOCKED_PREFIX = "mymemos-enc:v1:"
    }
}

/** One row on screen. A plain class so Swift sees plain properties. */
data class MemoRow(
    val name: String,
    val content: String,
    val pinned: Boolean,
    val locked: Boolean,
    val createdLabel: String,
)
