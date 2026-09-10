package com.keltruc.mymemos.notify

import android.content.Context
import com.keltruc.mymemos.R
import com.keltruc.mymemos.data.notify.Digest

/** The Android half of [Digest.Labels]. Counts go through plurals so "1 memos" cannot happen. */
class AndroidDigestLabels(private val context: Context) : Digest.Labels {
    override fun written(count: Int): String = context.resources.getQuantityString(R.plurals.digest_written, count, count)
    override fun tasksClosed(count: Int): String = context.resources.getQuantityString(R.plurals.digest_tasks_closed, count, count)
    override fun streak(days: Int): String = context.getString(R.string.streak, days)
    override fun worthALookAgain(): String = context.getString(R.string.digest_revisit)
    override fun nothingThisWeek(): String = context.getString(R.string.digest_nothing)
}
