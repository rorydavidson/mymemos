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
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask

/**
 * Everything core-data needs, built for a Mac. On Android this is a Hilt module; here it is
 * a handful of constructor calls, which is rather the point of core-data no longer knowing
 * about either.
 */
@OptIn(ExperimentalForeignApi::class)
internal object MacStack {

    private val supportDirectory: String by lazy {
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
     * In memory for now. The Keychain-backed store lands with the rest of the macOS work;
     * until then the app asks for a password each launch, which is honest rather than
     * pretending to remember one.
     */
    private class MemorySecrets : AccountSecrets {
        private var token: String? = null
        private var pat = false
        private var patName: String? = null
        private var cookieBlob: String? = null
        override suspend fun accessToken() = token
        override suspend fun updateAccessToken(token: String, expiresAt: String?) { this.token = token }
        override suspend fun isPersonalAccessToken() = pat
        override suspend fun cookies() = cookieBlob
        override suspend fun saveCookies(serialised: String) { cookieBlob = serialised }
        override suspend fun setPersonalAccessToken(token: String, tokenResourceName: String?) {
            this.token = token
            pat = true
            patName = tokenResourceName
        }
        override suspend fun mintedTokenName() = patName
        override suspend fun setPasswordSession(accessToken: String, expiresAt: String?) {
            token = accessToken
            pat = false
        }
        override suspend fun clear() {
            token = null
            pat = false
            patName = null
            cookieBlob = null
        }
    }

    private val secretsByAccount = mutableMapOf<String, AccountSecrets>()

    val registry: ApiClientRegistry by lazy {
        ApiClientRegistry(
            MemosApiFactory(),
            AccountSecretsFactory { key -> secretsByAccount.getOrPut(key) { MemorySecrets() } },
        )
    }

    /** Attachments are not downloaded on macOS yet, so nothing is in the store to find. */
    val attachments: AttachmentStore = object : AttachmentStore {
        override suspend fun exists(localId: String) = false
        override suspend fun readBytes(localId: String) = ByteArray(0)
        override suspend fun delete(localId: String) = Unit
    }

    /** No background scheduling yet: the app syncs while it is open. */
    val background: BackgroundSync = object : BackgroundSync {
        override fun enqueueRetry(full: Boolean) = Unit
        override fun ensurePeriodic() = Unit
        override fun cancelAll() = Unit
    }

    val widgets: WidgetRefresher = WidgetRefresher.None
}
