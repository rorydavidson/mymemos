package com.keltruc.mymemos.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether map composables may draw at all. Rendering a memo's location asks openstreetmap.org for
 * the tiles around it, which tells them roughly where the user is, so the setting rides along in
 * the composition rather than being threaded through every call site. With it off [MapPreview] and
 * [RouteMap] render nothing; the detail screen still shows its place-name chip, and the journey tab
 * still lists the memos in order.
 */
val LocalMapTiles = compositionLocalOf { false }
