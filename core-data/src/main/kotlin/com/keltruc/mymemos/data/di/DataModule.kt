package com.keltruc.mymemos.data.di

import android.content.Context
import androidx.room.Room
import com.keltruc.mymemos.database.MyMemosDatabase
import com.keltruc.mymemos.database.dao.AccountDao
import com.keltruc.mymemos.database.dao.AttachmentDao
import com.keltruc.mymemos.database.dao.MemoDao
import com.keltruc.mymemos.network.MemosApiFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MyMemosDatabase =
        Room.databaseBuilder(context, MyMemosDatabase::class.java, MyMemosDatabase.NAME).build()

    @Provides fun accountDao(db: MyMemosDatabase): AccountDao = db.accountDao()
    @Provides fun memoDao(db: MyMemosDatabase): MemoDao = db.memoDao()
    @Provides fun attachmentDao(db: MyMemosDatabase): AttachmentDao = db.attachmentDao()

    @Provides
    @Singleton
    fun apiFactory(): MemosApiFactory = MemosApiFactory()

    @Provides
    @Singleton
    fun json(factory: MemosApiFactory): Json = factory.json
}
