package com.keltruc.mymemos.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.ui.format.atZone
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StatsScreen(onBack: () -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val state by viewModel.items.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.statistics)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        RemoteContent(state, onRetry = viewModel::refresh) { s ->
            Column(
                Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.stats_memos), s.totalMemos, Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_todos), s.todos, Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_undone), s.undone, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatTile(stringResource(R.string.stats_links), s.links, Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_code), s.code, Modifier.weight(1f))
                    StatTile(stringResource(R.string.stats_tags), s.tagCounts.size, Modifier.weight(1f))
                }

                Text(stringResource(R.string.stats_activity), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                Heatmap(days = s.createdTimes.map { it.atZone(ZoneId.systemDefault()).toLocalDate() })

                if (s.tagCounts.isNotEmpty()) {
                    Text(stringResource(R.string.stats_tags), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        s.tagCounts.entries.sortedByDescending { it.value }.forEach { (tag, count) ->
                            AssistChip(onClick = {}, label = { Text("#$tag  $count") })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(14.dp)) {
            Text(value.toString(), style = MaterialTheme.typography.headlineMedium)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** GitHub-style grid: 12 weeks of columns, Monday at the top, darker cells for busier days. */
@Composable
private fun Heatmap(days: List<LocalDate>) {
    val counts = days.groupingBy { it }.eachCount()
    val today = LocalDate.now()
    val start = today.minusWeeks(11).with(java.time.DayOfWeek.MONDAY)
    val max = (counts.values.maxOrNull() ?: 1).coerceAtLeast(1)
    val primary = MaterialTheme.colorScheme.primary
    val empty = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (week in 0 until 12) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (dow in 0 until 7) {
                    val date = start.plusWeeks(week.toLong()).plusDays(dow.toLong())
                    val n = counts[date] ?: 0
                    val alpha = if (n == 0) 0f else 0.25f + 0.75f * (n.toFloat() / max)
                    androidx.compose.foundation.layout.Box(
                        Modifier
                            .size(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (n == 0 || date.isAfter(today)) empty else primary.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}
