package com.keltruc.mymemos.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.keltruc.mymemos.data.widget.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetRefresher @Inject constructor(@ApplicationContext private val context: Context) : WidgetRefresher {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun refresh() {
        scope.launch {
            runCatching { TasksWidget().updateAll(context) }
            runCatching { RecentWidget().updateAll(context) }
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    abstract fun widgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher
}
