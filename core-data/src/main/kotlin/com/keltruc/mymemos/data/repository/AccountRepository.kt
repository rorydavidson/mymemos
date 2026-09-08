package com.keltruc.mymemos.data.repository

import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.data.mapper.toModel
import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.database.entity.AccountEntity
import com.keltruc.mymemos.model.Account
import com.keltruc.mymemos.model.AuthMethod
import com.keltruc.mymemos.model.ServerProfile
import com.keltruc.mymemos.network.ApiException
import com.keltruc.mymemos.network.MemosApiFactory
import com.keltruc.mymemos.network.dto.PasswordCredentialsDto
import com.keltruc.mymemos.network.dto.SignInRequestDto
import com.keltruc.mymemos.network.dto.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(
    private val accountDao: AccountDao,
    private val memoDao: MemoDao,
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

    suspend fun signInWithPassword(serverUrl: String, username: String, password: String): Account = wrap {
        val url = MemosApiFactory.normaliseBaseUrl(serverUrl)
        // Sign in through a throwaway client keyed by username so the refresh cookie lands
        // in the right jar once we know the server's resource name for the user.
        val pending = "users/$username"
        val api = registry.api(url, pending)
        val response = api.signIn(SignInRequestDto(PasswordCredentialsDto(username, password)))
        val user = response.user
        val store = registry.tokenStore(url, pending)
        store.setPasswordSession(response.accessToken, response.accessTokenExpiresAt)
        if (user.name != pending) {
            // Move the credential under the canonical key and drop the temporary client.
            val canonical = registry.tokenStore(url, user.name)
            canonical.setPasswordSession(response.accessToken, response.accessTokenExpiresAt)
            store.cookies()?.let { canonical.saveCookies(it) }
            store.clear()
            registry.evict(url, pending)
        }
        saveAccount(url, user, AuthMethod.PASSWORD)
    }

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
        if (account.authMethod == AuthMethod.PASSWORD) runCatching { api.signOut() }
        registry.tokenStore(account.serverUrl, account.userResourceName).clear()
        registry.evict(account.serverUrl, account.userResourceName)
        memoDao.deleteAllForAccount(account.id)
        accountDao.delete(account.id)
        accountDao.observeAll()
    }

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

    private inline fun <T> wrap(block: () -> T): T = try {
        block()
    } catch (e: HttpException) {
        throw ApiException.from(e, json)
    }
}
