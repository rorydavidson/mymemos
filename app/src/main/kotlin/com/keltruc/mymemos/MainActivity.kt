package com.keltruc.mymemos

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.keltruc.mymemos.data.prefs.AppPreferences
import com.keltruc.mymemos.data.prefs.Settings
import com.keltruc.mymemos.navigation.IntentRouter
import com.keltruc.mymemos.navigation.MyMemosNavHost
import com.keltruc.mymemos.navigation.PaneLayout
import com.keltruc.mymemos.ui.components.LocalMapTiles
import com.keltruc.mymemos.ui.theme.MyMemosTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.flow.map

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var preferences: AppPreferences
    @Inject lateinit var intentRouter: IntentRouter

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) intentRouter.handle(intent)
        setContent {
            val settings by preferences.settings.collectAsStateWithLifecycle(Settings())
            val sizeClass = calculateWindowSizeClass(this)
            // Only a hinge running top to bottom can sit between a left and a right pane.
            val hinge by remember {
                WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this).map { info ->
                    info.displayFeatures.filterIsInstance<FoldingFeature>()
                        .firstOrNull { it.orientation == FoldingFeature.Orientation.VERTICAL }
                        ?.bounds
                }
            }.collectAsStateWithLifecycle(null)
            MyMemosTheme(dynamicColor = settings.dynamicColour) {
                CompositionLocalProvider(LocalMapTiles provides settings.mapTiles) {
                    // Medium is where an unfolded phone lands; Expanded is tablets and landscape phones.
                    val paneLayout = when (sizeClass.widthSizeClass) {
                        WindowWidthSizeClass.Expanded -> PaneLayout.WideDetail
                        WindowWidthSizeClass.Medium -> PaneLayout.EvenSplit
                        else -> PaneLayout.Single
                    }
                    MyMemosNavHost(paneLayout, hinge = hinge)
                }
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
