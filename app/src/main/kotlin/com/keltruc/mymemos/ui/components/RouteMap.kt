package com.keltruc.mymemos.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import com.keltruc.mymemos.model.Location
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * A day's located memos as a route: OpenStreetMap tiles zoomed to fit, a line through the
 * points in time order, and numbered markers. Tapping a marker is left to the caller.
 */
@Composable
fun RouteMap(points: List<Location>, modifier: Modifier = Modifier, height: Dp = 320.dp) {
    if (points.isEmpty()) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val tilesEnabled = LocalMapTiles.current
    val lineColour = MaterialTheme.colorScheme.primary
    val markerColour = MaterialTheme.colorScheme.error

    BoxWithConstraints(
        modifier.fillMaxWidth().height(height).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val tilePx = 256
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        // Fit: pick the largest zoom where all points sit inside the box with some margin.
        fun world(lat: Double, lon: Double, zoom: Int): Offset {
            val n = 1 shl zoom
            val x = (lon + 180.0) / 360.0 * n
            val latRad = lat * PI / 180.0
            val y = (1.0 - ln(tan(latRad) + 1 / cos(latRad)) / PI) / 2.0 * n
            return Offset((x * tilePx).toFloat(), (y * tilePx).toFloat())
        }
        val minLat = points.minOf { it.latitude }; val maxLat = points.maxOf { it.latitude }
        val minLon = points.minOf { it.longitude }; val maxLon = points.maxOf { it.longitude }
        var zoom = 16
        while (zoom > 2) {
            val a = world(minLat, minLon, zoom); val b = world(maxLat, maxLon, zoom)
            if (kotlin.math.abs(a.x - b.x) < widthPx * 0.7f && kotlin.math.abs(a.y - b.y) < heightPx * 0.7f) break
            zoom--
        }
        val centre = world((minLat + maxLat) / 2, (minLon + maxLon) / 2, zoom)
        val origin = Offset(centre.x - widthPx / 2f, centre.y - heightPx / 2f)
        fun project(l: Location): Offset = world(l.latitude, l.longitude, zoom) - origin

        val n = 1 shl zoom
        val tx0 = floor(origin.x / tilePx).toInt(); val ty0 = floor(origin.y / tilePx).toInt()
        val tx1 = floor((origin.x + widthPx) / tilePx).toInt(); val ty1 = floor((origin.y + heightPx) / tilePx).toInt()
        if (tilesEnabled) for (ty in ty0..ty1) for (tx in tx0..tx1) {
            if (ty !in 0 until n) continue
            val ox = with(density) { (tx * tilePx - origin.x).toDp() }
            val oy = with(density) { (ty * tilePx - origin.y).toDp() }
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data("https://tile.openstreetmap.org/$zoom/${Math.floorMod(tx, n)}/$ty.png")
                    .httpHeaders(NetworkHeaders.Builder().set("User-Agent", "MyMemos/1.0 (Android; journey map)").build())
                    .build(),
                contentDescription = null,
                modifier = Modifier.offset(ox, oy).size(with(density) { tilePx.toDp() }),
            )
        }
        Canvas(Modifier.fillMaxSize()) {
            val pts = points.map(::project)
            if (pts.size > 1) {
                val path = Path().apply { moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) } }
                drawPath(path, Color.White, style = Stroke(9f, cap = StrokeCap.Round))
                drawPath(path, lineColour, style = Stroke(5f, cap = StrokeCap.Round))
            }
            pts.forEachIndexed { i, p ->
                drawCircle(Color.White, 20f, p)
                drawCircle(markerColour, 16f, p)
                val label = measurer.measure((i + 1).toString(), TextStyle(color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                drawText(label, topLeft = Offset(p.x - label.size.width / 2f, p.y - label.size.height / 2f))
            }
        }
        Text(
            "© OpenStreetMap",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(Color.White.copy(alpha = 0.6f)).padding(horizontal = 4.dp),
        )
    }
}

