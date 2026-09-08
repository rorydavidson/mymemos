package com.keltruc.mymemos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.prefs.Settings
import com.keltruc.mymemos.navigation.MyMemosNavHost
import com.keltruc.mymemos.ui.theme.MyMemosTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var preferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            val settings by preferences.settings.collectAsStateWithLifecycle(Settings())
            MyMemosTheme(dynamicColor = settings.dynamicColour) {
                MyMemosNavHost()
            }
        }
    }
}
