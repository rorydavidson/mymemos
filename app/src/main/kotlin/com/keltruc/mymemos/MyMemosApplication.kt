package com.keltruc.mymemos

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.keltruc.mymemos.data.auth.ActiveSession
import com.keltruc.mymemos.data.repository.TemplateRepository
import com.keltruc.mymemos.data.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class MyMemosApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var activeSession: ActiveSession
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var templates: TemplateRepository
    @Inject lateinit var notifier: com.keltruc.mymemos.notify.Notifier
    @Inject lateinit var alarmScheduler: com.keltruc.mymemos.notify.AlarmScheduler
    @Inject lateinit var digestScheduler: com.keltruc.mymemos.notify.DigestScheduler
    @Inject lateinit var configRepository: com.keltruc.mymemos.data.config.ConfigRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        syncScheduler.ensurePeriodic()
        CoroutineScope(Dispatchers.IO).launch { templates.seedDefaultsIfEmpty() }
        notifier.ensureChannels()
        alarmScheduler.start()
        CoroutineScope(Dispatchers.IO).launch {
            configRepository.config.collect { if (it.weeklyDigest) digestScheduler.enable() else digestScheduler.disable() }
        }
    }

    override fun newImageLoader(context: Context): ImageLoader {
        val client = OkHttpClient.Builder().addInterceptor(activeSession.imageAuthInterceptor()).build()
        return ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
            .build()
    }
}
