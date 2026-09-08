package com.keltruc.mymemos.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether map composables may fetch OpenStreetMap tiles. Drawing a memo's location asks
 * openstreetmap.org for the tiles around it, which tells them roughly where the user is, so the
 * setting rides along in the composition rather than being threaded through every call site.
 * With it off the maps still draw their markers and route, just on a blank background.
 */
val LocalMapTiles = compositionLocalOf { false }
