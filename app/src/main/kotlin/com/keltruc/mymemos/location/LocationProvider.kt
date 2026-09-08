package com.keltruc.mymemos.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.keltruc.mymemos.model.Location
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.resume

/**
 * Current position via the platform LocationManager, no Play Services. Reverse-geocodes to
 * a short place name for the memo's placeholder when the device can.
 */
class LocationProvider @Inject constructor(@ApplicationContext private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasFine(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    suspend fun current(): Location? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        // GPS needs the fine permission; fused and network work with coarse. Try each enabled
        // provider in turn, so an emulator with only GPS and a phone with only fused both work.
        val candidates = buildList {
            add(LocationManager.FUSED_PROVIDER)
            if (hasFine()) add(LocationManager.GPS_PROVIDER)
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        var fix: android.location.Location? = null
        for (provider in candidates) {
            fix = withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine { cont ->
                    try {
                        @Suppress("MissingPermission")
                        manager.getCurrentLocation(provider, null, context.mainExecutor) { loc -> cont.resume(loc) }
                    } catch (e: SecurityException) {
                        cont.resume(null)
                    }
                }
            } ?: runCatching {
                @Suppress("MissingPermission")
                manager.getLastKnownLocation(provider)
            }.getOrNull()
            if (fix != null) break
        }
        if (fix == null) {
            android.util.Log.w("LocationProvider", "no fix from providers $candidates")
            return null
        }
        val placeholder = placeName(fix.latitude, fix.longitude)
        return Location(placeholder, fix.latitude, fix.longitude)
    }

    private suspend fun placeName(lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext ""
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = if (Build.VERSION.SDK_INT >= 33) {
            withTimeoutOrNull(5_000) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lon, 1) { list -> cont.resume(list.firstOrNull()) }
                }
            }
        } else {
            @Suppress("DEPRECATION")
            runCatching { geocoder.getFromLocation(lat, lon, 1)?.firstOrNull() }.getOrNull()
        } ?: return@withContext ""
        listOfNotNull(address.featureName?.takeIf { it != address.thoroughfare }, address.thoroughfare, address.locality)
            .filter { it.isNotBlank() }.distinct().take(2).joinToString(", ")
    }
}
