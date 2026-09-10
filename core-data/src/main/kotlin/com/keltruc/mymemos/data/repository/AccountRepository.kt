package com.keltruc.mymemos.data.repository

import com.keltruc.mymemos.data.attachments.AttachmentStore
import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.data.crypto.PasswordSession
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.database.dao.AttachmentDao
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.database.entity.AccountEntity
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.AuthMethod
import com.keltruc.mymemos.model.ServerProfile
import com.keltruc.mymemos.network.ApiException
import com.keltruc.mymemos.network.MemosApiFactory
import com.keltruc.mymemos.network.dto.CreatePersonalAccessTokenRequestDto
import com.keltruc.mymemos.network.dto.PasswordCredentialsDto
import com.keltruc.mymemos.network.dto.SignInRequestDto
import com.keltruc.mymemos.network.dto.UserDto
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class AccountRepository constructor(
    private val accountDao: AccountDao,
    private val memoDao: MemoDao,
    private val attachmentDao: AttachmentDao,
    private val attachmentStore: AttachmentStore,
    private val passwordSession: PasswordSession,
    private val registry: ApiClientRegistry,
    private val json: Json,
) {
    val activeAccount: Flow<Account?> = accountDao.observeActive().map { it?.toModel() }
    val accounts: Flow<List<Account>> = accountDao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun activeAccountOrNull(): Account? = accountDao.getActive()?.toModel()

    suspend fun probeServer(serverUrl: String): ServerProfile = wrap {
        val dto = registry.anonymousApi(serverUrl).getInstanceProfile()
        ServerProfile(dto.version, dto.instanceUrl, dto.demo, dto.needsSetup)
    }

    /**
     * Password sign-in yields a short-lived access token plus a refresh cookie meant for
     * browsers. A phone is better served by its own long-lived personal access token, so
     * we mint one straight away and use that; the session is only a fallback if minting
     * is refused.
     */
    suspend fun signInWithPassword(serverUrl: String, username: String, password: String): Account = wrap {
        val url = MemosApiFactory.normaliseBaseUrl(serverUrl)
        val pending = "users/$username"
        val api = registry.api(url, pending)
        val response = api.signIn(SignInRequestDto(PasswordCredentialsDto(username, password)))
        val user = response.user
        val store = registry.tokenStore(url, pending)
        store.setPasswordSession(response.accessToken, response.accessTokenExpiresAt)

        val minted = runCatching {
            api.createPersonalAccessToken(
                user.name,
                CreatePersonalAccessTokenRequestDto(description = "MyMemos on $deviceName", expiresInDays = TOKEN_LIFETIME_DAYS),
            )
        }.getOrNull()?.takeIf { it.token.isNotEmpty() }

        val canonical = registry.tokenStore(url, user.name)
        if (minted != null) {
            canonical.setPersonalAccessToken(minted.token, minted.personalAccessToken?.name)
        } else {
            canonical.setPasswordSession(response.accessToken, response.accessTokenExpiresAt)
            store.cookies()?.let { canonical.saveCookies(it) }
        }
        if (user.name != pending) {
            store.clear()
        }
        registry.evict(url, pending)
        registry.evict(url, user.name)
        saveAccount(url, user, if (minted != null) AuthMethod.PERSONAL_ACCESS_TOKEN else AuthMethod.PASSWORD)
    }

    /** Same as [signInWithPassword], for an account whose credential stopped working. */
    suspend fun reauthenticate(account: Account, password: String): Account =
        signInWithPassword(account.serverUrl, account.username, password)

    suspend fun signInWithToken(serverUrl: String, token: String): Account = wrap {
        val url = MemosApiFactory.normaliseBaseUrl(serverUrl)
        val probeKey = "pat:${token.takeLast(8)}"
        registry.tokenStore(url, probeKey).setPersonalAccessToken(token)
        val user = try {
            registry.api(url, probeKey).getCurrentUser().user
        } finally {
            registry.evict(url, probeKey)
        }
        registry.tokenStore(url, user.name).setPersonalAccessToken(token)
        registry.tokenStore(url, probeKey).clear()
        saveAccount(url, user, AuthMethod.PERSONAL_ACCESS_TOKEN)
    }

    suspend fun switchTo(accountId: Long) = accountDao.setActive(accountId)

    suspend fun signOut(account: Account) {
        val api = registry.api(account.serverUrl, account.userResourceName)
        val store = registry.tokenStore(account.serverUrl, account.userResourceName)
        // Revoke what we minted; a user-pasted token is theirs to keep.
        store.mintedTokenName()?.let { runCatching { api.deletePersonalAccessToken(it) } }
        if (account.authMethod == AuthMethod.PASSWORD && store.mintedTokenName() == null) runCatching { api.signOut() }
        store.clear()
        registry.evict(account.serverUrl, account.userResourceName)
        // Cached attachment files are not covered by the database cascade, so remove them by hand
        // before the rows that name them disappear.
        attachmentDao.localIdsForAccount(account.id).forEach { attachmentStore.delete(it) }
        memoDao.deleteAllForAccount(account.id)
        accountDao.delete(account.id)
        // The memo password is app-wide, so it only goes when the last account does.
        if (accountDao.count() == 0) passwordSession.forget()
    }

    private companion object {
        /**
         * Life of the token minted at password sign-in. The password is never kept, so this cannot
         * be renewed in the background: when it lapses the app falls back to the "sign in again"
         * banner and the user retypes their password.
         */
        const val TOKEN_LIFETIME_DAYS = 90
    }

    private val deviceName: String
        get() = listOf(android.os.Build.MANUFACTURER, android.os.Build.MODEL).filter { it.isNotBlank() }
            .joinToString(" ").replaceFirstChar { it.uppercase() }

    private suspend fun saveAccount(serverUrl: String, user: UserDto, method: AuthMethod): Account {
        val version = runCatching { registry.anonymousApi(serverUrl).getInstanceProfile().version }.getOrDefault("")
        val entity = AccountEntity(
            serverUrl = serverUrl,
            userResourceName = user.name,
            username = user.username,
            displayName = user.displayName.ifEmpty { user.username },
            avatarUrl = user.avatarUrl,
            role = user.role,
            authMethod = method.name,
            serverVersion = version,
        )
        val id = accountDao.upsertAndActivate(entity)
        return accountDao.getById(id)!!.toModel()
    }

    private suspend inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: ResponseException) {
        throw ApiException.from(e, json)
    }
}
