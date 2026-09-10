package com.keltruc.mymemos.di

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.room.Room
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.keltruc.mymemos.data.attachments.AttachmentStore
import com.keltruc.mymemos.data.attachments.FileAttachmentStore
import com.keltruc.mymemos.data.auth.ActiveSession
import com.keltruc.mymemos.data.auth.AccountSecretsFactory
import com.keltruc.mymemos.data.auth.ApiClientRegistry
import com.keltruc.mymemos.data.auth.SecureTokenStore
import com.keltruc.mymemos.data.config.ConfigRepository
import com.keltruc.mymemos.data.crypto.PasswordSession
import com.keltruc.mymemos.data.crypto.RememberedPassword
import com.keltruc.mymemos.data.export.MarkdownExporter
import com.keltruc.mymemos.data.imports.MarkdownImporter
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.AccountSettingsRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import com.keltruc.mymemos.data.repository.ShareRepository
import com.keltruc.mymemos.data.repository.ShortcutRepository
import com.keltruc.mymemos.data.repository.TemplateRepository
import com.keltruc.mymemos.data.sync.BackgroundSync
import com.keltruc.mymemos.data.sync.SyncEngine
import com.keltruc.mymemos.data.sync.SyncScheduler
import com.keltruc.mymemos.data.widget.WidgetRefresher
import com.keltruc.mymemos.database.MyMemosDatabase
import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.database.dao.AttachmentDao
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.database.dao.PendingOpDao
import com.keltruc.mymemos.database.dao.ReactionDao
import com.keltruc.mymemos.database.dao.RelationDao
import com.keltruc.mymemos.database.dao.ShortcutDao
import com.keltruc.mymemos.database.dao.TemplateDao
import com.keltruc.mymemos.network.MemosApiFactory
import com.keltruc.mymemos.secrets.KeystoreRememberedPassword
import com.keltruc.mymemos.sync.WorkManagerBackgroundSync
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/**
 * core-data no longer knows about Hilt: its classes are plain constructors so they can be
 * built on a platform that has never heard of Dagger. Wiring them together is this module's
 * job, and it stays on the Android side of the fence.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoreDataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MyMemosDatabase =
        Room.databaseBuilder(context, MyMemosDatabase::class.java, MyMemosDatabase.NAME).build()

    @Provides fun accountDao(db: MyMemosDatabase): AccountDao = db.accountDao()
    @Provides fun memoDao(db: MyMemosDatabase): MemoDao = db.memoDao()
    @Provides fun attachmentDao(db: MyMemosDatabase): AttachmentDao = db.attachmentDao()
    @Provides fun pendingOpDao(db: MyMemosDatabase): PendingOpDao = db.pendingOpDao()
    @Provides fun relationDao(db: MyMemosDatabase): RelationDao = db.relationDao()
    @Provides fun reactionDao(db: MyMemosDatabase): ReactionDao = db.reactionDao()
    @Provides fun shortcutDao(db: MyMemosDatabase): ShortcutDao = db.shortcutDao()
    @Provides fun templateDao(db: MyMemosDatabase): TemplateDao = db.templateDao()

    @Provides
    @Singleton
    fun apiFactory(@ApplicationContext context: Context): MemosApiFactory {
        val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        return MemosApiFactory(logBodies = debuggable)
    }

    @Provides
    @Singleton
    fun json(factory: MemosApiFactory): Json = factory.json

    /** DataStore knows how to be multiplatform; where the file goes is still ours to say. */
    @Provides
    @Singleton
    fun preferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.filesDir.resolve("datastore/settings.preferences_pb") }

    @Provides
    @Singleton
    fun preferences(dataStore: DataStore<Preferences>) = AppPreferences(dataStore)

    @Provides
    @Singleton
    fun rememberedPassword(@ApplicationContext context: Context): RememberedPassword =
        KeystoreRememberedPassword(context)

    @Provides
    @Singleton
    fun passwordSession(remembered: RememberedPassword) = PasswordSession(remembered)

    /** The Keystore-backed credential store, one per account. */
    @Provides
    @Singleton
    fun accountSecrets(@ApplicationContext context: Context) =
        AccountSecretsFactory { key -> SecureTokenStore(context, key) }

    @Provides
    @Singleton
    fun fileAttachmentStore(@ApplicationContext context: Context) = FileAttachmentStore(context)

    @Provides
    fun attachmentStore(store: FileAttachmentStore): AttachmentStore = store

    @Provides
    @Singleton
    fun apiClientRegistry(factory: MemosApiFactory, secrets: AccountSecretsFactory) =
        ApiClientRegistry(factory, secrets)

    @Provides
    @Singleton
    fun activeSession(accountDao: AccountDao, registry: ApiClientRegistry) =
        ActiveSession(accountDao, registry)

    @Provides
    @Singleton
    fun backgroundSync(@ApplicationContext context: Context): BackgroundSync =
        WorkManagerBackgroundSync(context)

    @Provides
    @Singleton
    fun syncEngine(
        db: MyMemosDatabase,
        accountDao: AccountDao,
        memoDao: MemoDao,
        attachmentDao: AttachmentDao,
        pendingOpDao: PendingOpDao,
        relationDao: RelationDao,
        reactionDao: ReactionDao,
        shortcutDao: ShortcutDao,
        registry: ApiClientRegistry,
        attachmentStore: AttachmentStore,
        widgets: WidgetRefresher,
        preferences: AppPreferences,
        json: Json,
    ) = SyncEngine(
        db, accountDao, memoDao, attachmentDao, pendingOpDao, relationDao, reactionDao,
        shortcutDao, registry, attachmentStore, widgets, preferences, json,
    )

    @Provides
    @Singleton
    fun syncScheduler(engine: SyncEngine, accountDao: AccountDao, background: BackgroundSync) =
        SyncScheduler(engine, accountDao, background)

    @Provides
    @Singleton
    fun memoRepository(
        db: MyMemosDatabase,
        memoDao: MemoDao,
        attachmentDao: AttachmentDao,
        pendingOpDao: PendingOpDao,
        relationDao: RelationDao,
        reactionDao: ReactionDao,
        registry: ApiClientRegistry,
        attachmentStore: AttachmentStore,
        engine: SyncEngine,
        scheduler: SyncScheduler,
        preferences: AppPreferences,
        widgets: WidgetRefresher,
        json: Json,
    ) = MemoRepository(
        db, memoDao, attachmentDao, pendingOpDao, relationDao, reactionDao, registry,
        attachmentStore, engine, scheduler, preferences, widgets, json,
    )

    @Provides
    @Singleton
    fun accountRepository(
        accountDao: AccountDao,
        memoDao: MemoDao,
        attachmentDao: AttachmentDao,
        attachmentStore: AttachmentStore,
        passwordSession: PasswordSession,
        registry: ApiClientRegistry,
        json: Json,
    ) = AccountRepository(accountDao, memoDao, attachmentDao, attachmentStore, passwordSession, registry, json)

    @Provides
    @Singleton
    fun accountSettingsRepository(registry: ApiClientRegistry, accountDao: AccountDao, json: Json) =
        AccountSettingsRepository(registry, accountDao, json)

    @Provides
    @Singleton
    fun shareRepository(registry: ApiClientRegistry, json: Json) = ShareRepository(registry, json)

    @Provides
    @Singleton
    fun shortcutRepository(
        db: MyMemosDatabase,
        shortcutDao: ShortcutDao,
        memoDao: MemoDao,
        registry: ApiClientRegistry,
        engine: SyncEngine,
        json: Json,
    ) = ShortcutRepository(db, shortcutDao, memoDao, registry, engine, json)

    @Provides
    @Singleton
    fun templateRepository(dao: TemplateDao) = TemplateRepository(dao)

    @Provides
    @Singleton
    fun configRepository(
        memoDao: MemoDao,
        memoRepository: MemoRepository,
        accountRepository: AccountRepository,
        settingsRepository: AccountSettingsRepository,
    ) = ConfigRepository(memoDao, memoRepository, accountRepository, settingsRepository)

    @Provides
    @Singleton
    fun markdownExporter(memoDao: MemoDao, attachmentStore: FileAttachmentStore) =
        MarkdownExporter(memoDao, attachmentStore)

    @Provides
    @Singleton
    fun markdownImporter(@ApplicationContext context: Context, memoRepository: MemoRepository) =
        MarkdownImporter(context, memoRepository)
}
