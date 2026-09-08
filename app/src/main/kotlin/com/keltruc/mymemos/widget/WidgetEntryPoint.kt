package com.keltruc.mymemos.widget

import android.content.Context
import com.keltruc.mymemos.data.repository.AccountRepository
import com.keltruc.mymemos.data.repository.MemoRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/** Widgets are not Hilt-injected, so they reach the graph through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun memoRepository(): MemoRepository
    fun accountRepository(): AccountRepository

    companion object {
        fun get(context: Context): WidgetEntryPoint =
            EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    }
}
