package com.keltruc.mymemos.data.widget

/** Implemented by the app module; called whenever local memo data changes. */
interface WidgetRefresher {
    fun refresh()

    object None : WidgetRefresher {
        override fun refresh() = Unit
    }
}
