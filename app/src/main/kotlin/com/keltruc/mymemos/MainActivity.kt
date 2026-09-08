package com.keltruc.mymemos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.keltruc.mymemos.navigation.MyMemosNavHost
import com.keltruc.mymemos.ui.theme.MyMemosTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MyMemosTheme {
                MyMemosNavHost()
            }
        }
    }
}
