package shared

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.keltruc.mymemos.data.attachments.AttachmentStore
import com.keltruc.mymemos.data.auth.AccountSecrets
import com.keltruc.mymemos.data.auth.AccountSecretsFactory
import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.sync.BackgroundSync
import com.keltruc.mymemos.data.widget.WidgetRefresher
import com.keltruc.mymemos.database.MyMemosDatabase
import com.keltruc.mymemos.network.MemosApiFactory
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.stringWithContentsOfFile
import platform.Foundation.writeToFile
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Everything core-data needs, built for a Mac. On Android this is a Hilt module; here it is
 * a handful of constructor calls, which is rather the point of core-data no longer knowing
 * about either.
 */
@OptIn(ExperimentalForeignApi::class)
internal object MacStack {

    val supportDirectory: String by lazy {
        val base = NSFileManager.defaultManager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        ) ?: error("no Application Support directory")
        val dir = (base as NSURL).URLByAppendingPathComponent("MyMemos")!!.path!!
        NSFileManager.defaultManager.createDirectoryAtPath(dir, true, null, null)
        dir
    }

    val database: MyMemosDatabase by lazy {
        Room.databaseBuilder<MyMemosDatabase>(name = "$supportDirectory/${MyMemosDatabase.NAME}")
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.Default)
            .build()
    }

    val preferences: AppPreferences by lazy {
        val store: DataStore<Preferences> = PreferenceDataStoreFactory.createWithPath {
            "$supportDirectory/settings.preferences_pb".toPath()
        }
        AppPreferences(store)
    }

    /**
     * The Keychain, keyed the same way the Android side namespaces its encrypted preferences,
     * so an account's token, its cookies and the name of the token it minted sit together and
     * a sign-out clears the lot.
     */
    private class KeychainSecrets(private val accountKey: String) : AccountSecrets {

        private fun key(suffix: String) = "$accountKey.$suffix"

        override suspend fun accessToken(): String? = Keychain.read(key(ACCESS_TOKEN))

        override suspend fun updateAccessToken(token: String, expiresAt: String?) {
            Keychain.write(key(ACCESS_TOKEN), token)
            expiresAt?.let { Keychain.write(key(EXPIRES_AT), it) } ?: Keychain.delete(key(EXPIRES_AT))
        }

        override suspend fun isPersonalAccessToken(): Boolean = Keychain.read(key(IS_PAT)) == "true"

        override suspend fun cookies(): String? = Keychain.read(key(COOKIES))

        override suspend fun saveCookies(serialised: String) = Keychain.write(key(COOKIES), serialised)

        override suspend fun setPersonalAccessToken(token: String, tokenResourceName: String?) {
            Keychain.write(key(ACCESS_TOKEN), token)
            Keychain.write(key(IS_PAT), "true")
            tokenResourceName?.let { Keychain.write(key(PAT_NAME), it) } ?: Keychain.delete(key(PAT_NAME))
            Keychain.delete(key(EXPIRES_AT))
        }

        override suspend fun mintedTokenName(): String? = Keychain.read(key(PAT_NAME))

        override suspend fun setPasswordSession(accessToken: String, expiresAt: String?) {
            Keychain.write(key(ACCESS_TOKEN), accessToken)
            expiresAt?.let { Keychain.write(key(EXPIRES_AT), it) } ?: Keychain.delete(key(EXPIRES_AT))
            Keychain.write(key(IS_PAT), "false")
        }

        override suspend fun clear() {
            listOf(ACCESS_TOKEN, EXPIRES_AT, IS_PAT, COOKIES, PAT_NAME).forEach { Keychain.delete(key(it)) }
        }

        private companion object {
            const val ACCESS_TOKEN = "access_token"
            const val EXPIRES_AT = "expires_at"
            const val IS_PAT = "is_pat"
            const val COOKIES = "cookies"
            const val PAT_NAME = "pat_name"
        }
    }

    private val secretsByAccount = mutableMapOf<String, AccountSecrets>()

    val registry: ApiClientRegistry by lazy {
        ApiClientRegistry(
            MemosApiFactory(),
            AccountSecretsFactory { key -> secretsByAccount.getOrPut(key) { KeychainSecrets(key) } },
        )
    }

    val attachments: MacAttachmentStore by lazy { MacAttachmentStore(supportDirectory) }

    /** No background scheduling yet: the app syncs while it is open. */
    val background: BackgroundSync = object : BackgroundSync {
        override fun enqueueRetry(full: Boolean) = Unit
        override fun ensurePeriodic() = Unit
        override fun cancelAll() = Unit
    }

    val widgets: WidgetRefresher = WidgetRefresher.None

    // MARK: the avatar, kept between launches

    private val avatarFile get() = "$supportDirectory/avatar.bin"
    private val avatarSourceFile get() = "$supportDirectory/avatar.source"

    /** The stored copy, if it came from the same URL the account still points at. */
    fun cachedAvatar(url: String): ByteArray? {
        val storedUrl = NSString.stringWithContentsOfFile(avatarSourceFile, NSUTF8StringEncoding, null)
        if (storedUrl != url) return null
        return NSData.dataWithContentsOfFile(avatarFile)?.toByteArray()?.takeIf { it.isNotEmpty() }
    }

    fun cacheAvatar(url: String, bytes: ByteArray) {
        bytes.toNSData().writeToFile(avatarFile, true)
        (url as NSString).writeToFile(avatarSourceFile, true, NSUTF8StringEncoding, null)
    }
}
