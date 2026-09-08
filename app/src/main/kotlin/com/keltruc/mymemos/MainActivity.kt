package com.keltruc.mymemos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.prefs.Settings
import com.keltruc.mymemos.navigation.IntentRouter
import com.keltruc.mymemos.navigation.MyMemosNavHost
import com.keltruc.mymemos.ui.theme.MyMemosTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var preferences: AppPreferences
    @Inject lateinit var intentRouter: IntentRouter

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intentRouter.handle(intent)
        setContent {
            val settings by preferences.settings.collectAsStateWithLifecycle(Settings())
            MyMemosTheme(dynamicColor = settings.dynamicColour) {
                MyMemosNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intentRouter.handle(intent)
    }

    companion object {
        const val ACTION_NEW_MEMO = IntentRouter.ACTION_NEW_MEMO
        const val ACTION_OPEN_MEMO = IntentRouter.ACTION_OPEN_MEMO
        const val EXTRA_TEXT = IntentRouter.EXTRA_TEXT
        const val EXTRA_MEMO_LOCAL_ID = IntentRouter.EXTRA_MEMO_LOCAL_ID
    }
}
