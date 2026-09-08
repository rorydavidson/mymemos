package com.keltruc.mymemos.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.keltruc.mymemos.model.Location
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/**
 * A small static map made of OpenStreetMap tiles around a point, with a marker. Tiles come
 * straight from tile.openstreetmap.org (their policy asks for an identifying User-Agent,
 * which is set). Tapping opens the location in whatever maps app is installed.
 */
@Composable
fun MapPreview(location: Location, modifier: Modifier = Modifier, height: Dp = 160.dp, zoom: Int = 15) {
    val context = LocalContext.current
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable {
                val uri = Uri.parse("geo:${location.latitude},${location.longitude}?q=${location.latitude},${location.longitude}(${Uri.encode(location.placeholder.ifEmpty { "Memo" })})")
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
            },
    ) {
        val tilePx = 256
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val n = 1 shl zoom
        // Fractional tile coordinates of the point (Web Mercator).
        val xf = (location.longitude + 180.0) / 360.0 * n
        val latRad = location.latitude * PI / 180.0
        val yf = (1.0 - ln(tan(latRad) + 1 / kotlin.math.cos(latRad)) / PI) / 2.0 * n
        // Pixel position of the point relative to the top-left of tile (floor(xf), floor(yf)).
        val originX = widthPx / 2f - ((xf - floor(xf)) * tilePx).toFloat()
        val originY = heightPx / 2f - ((yf - floor(yf)) * tilePx).toFloat()
        val tx0 = floor(xf).toInt()
        val ty0 = floor(yf).toInt()
        val left = -((originX / tilePx).toInt() + 1)
        val top = -((originY / tilePx).toInt() + 1)
        val right = ((widthPx - originX) / tilePx).toInt() + 1
        val bottom = ((heightPx - originY) / tilePx).toInt() + 1
        for (dy in top..bottom) for (dx in left..right) {
            val tx = Math.floorMod(tx0 + dx, n)
            val ty = ty0 + dy
            if (ty !in 0 until n) continue
            val ox = with(density) { (originX + dx * tilePx).toDp() }
            val oy = with(density) { (originY + dy * tilePx).toDp() }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("https://tile.openstreetmap.org/$zoom/$tx/$ty.png")
                    .httpHeaders(NetworkHeaders.Builder().set("User-Agent", "MyMemos/1.0 (Android; memo location preview)").build())
                    .build(),
                contentDescription = null,
                modifier = Modifier.offset(ox, oy).size(with(density) { tilePx.toDp() }),
            )
        }
        Icon(
            Icons.Default.Place,
            contentDescription = location.placeholder,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.align(Alignment.Center).offset(y = (-14).dp).size(32.dp),
        )
        if (location.placeholder.isNotEmpty()) {
            Text(
                location.placeholder,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.55f), MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        Text(
            "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.White.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
        )
    }
}

@Suppress("unused") private val keepMath = listOf(::atan, ::sinh)
