package com.keltruc.mymemos.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.Memo
import kotlin.math.sqrt
import kotlin.random.Random

/** Nodes are memos, edges are references. A small force layout run once, then drag to pan. */
@Composable
fun GraphView(memos: List<Memo>, edges: List<Pair<String, String>>, onOpen: (String) -> Unit) {
    if (memos.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.graph_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val positions = remember(memos, edges) { layout(memos.map { it.localId }, edges) }
    var pan by remember { mutableStateOf(Offset.Zero) }
    val measurer = rememberTextMeasurer()
    val nodeColour = MaterialTheme.colorScheme.primary
    val pinnedColour = MaterialTheme.colorScheme.secondary
    val edgeColour = MaterialTheme.colorScheme.outlineVariant
    val labelColour = MaterialTheme.colorScheme.onSurface
    val labels = memos.associate { it.localId to it.displayContent.lineSequence().firstOrNull { l -> l.isNotBlank() }.orEmpty().take(28) }
    val degree = edges.flatMap { listOf(it.first, it.second) }.groupingBy { it }.eachCount()

    Canvas(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectDragGestures { _, drag -> pan += drag } }
            .pointerInput(positions) {
                detectTapGestures { tap ->
                    val centre = Offset(size.width / 2f, size.height / 2f) + pan
                    val hit = positions.minByOrNull { (_, p) -> (centre + p * 90f - tap).getDistance() }
                    if (hit != null && (centre + hit.value * 90f - tap).getDistance() < 60f) onOpen(hit.key)
                }
            },
    ) {
        val centre = Offset(size.width / 2f, size.height / 2f) + pan
        fun at(id: String) = centre + (positions[id] ?: Offset.Zero) * 90f
        edges.forEach { (a, b) -> drawLine(edgeColour, at(a), at(b), strokeWidth = 3f) }
        memos.forEach { memo ->
            val p = at(memo.localId)
            val r = 14f + 5f * (degree[memo.localId] ?: 0)
            drawCircle(if (memo.pinned) pinnedColour else nodeColour, r, p)
            drawCircle(labelColour.copy(alpha = 0.15f), r, p, style = Stroke(2f))
            drawText(measurer, labels[memo.localId].orEmpty(), p + Offset(r + 8f, -18f), TextStyle(color = labelColour, fontSize = 12.sp))
        }
    }
}

/** Fruchterman-Reingold on a unit-ish grid: repulsion between all nodes, springs on edges. */
private fun layout(ids: List<String>, edges: List<Pair<String, String>>): Map<String, Offset> {
    val rnd = Random(42)
    val pos = ids.associateWith { Offset(rnd.nextFloat() * 6f - 3f, rnd.nextFloat() * 6f - 3f) }.toMutableMap()
    val k = 1.6f
    var temperature = 1.0f
    repeat(250) {
        val disp = ids.associateWith { Offset.Zero }.toMutableMap()
        for (i in ids.indices) for (j in i + 1 until ids.size) {
            val a = ids[i]; val b = ids[j]
            var d = pos[a]!! - pos[b]!!
            var dist = d.getDistance()
            if (dist < 0.01f) { d = Offset(0.01f, 0.01f); dist = 0.014f }
            val force = k * k / dist
            disp[a] = disp[a]!! + d / dist * force
            disp[b] = disp[b]!! - d / dist * force
        }
        edges.forEach { (a, b) ->
            val d = pos[a]!! - pos[b]!!
            val dist = d.getDistance().coerceAtLeast(0.01f)
            val force = dist * dist / k
            disp[a] = disp[a]!! - d / dist * force
            disp[b] = disp[b]!! + d / dist * force
        }
        ids.forEach { id ->
            val d = disp[id]!!
            val len = d.getDistance().coerceAtLeast(0.01f)
            pos[id] = pos[id]!! + d / len * minOf(len, temperature)
        }
        temperature *= 0.97f
    }
    return pos
}

@Suppress("unused")
private fun Offset.norm() = sqrt(x * x + y * y)
