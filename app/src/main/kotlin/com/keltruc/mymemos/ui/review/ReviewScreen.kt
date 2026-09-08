package com.keltruc.mymemos.ui.review

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.Memo
import com.keltruc.mymemos.ui.components.MemoCard
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val dayFmt: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    onBack: () -> Unit,
    onOpenMemo: (String) -> Unit,
    onEditMemo: (String) -> Unit,
    viewModel: ReviewViewModel = hiltViewModel(),
) {
    val day by viewModel.day.collectAsStateWithLifecycle()
    val dayMemos by viewModel.dayMemos.collectAsStateWithLifecycle()
    val throwbacks by viewModel.throwbacks.collectAsStateWithLifecycle()
    val nearby by viewModel.nearby.collectAsStateWithLifecycle()
    val nearbyError by viewModel.nearbyError.collectAsStateWithLifecycle()
    val account by viewModel.account_.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) viewModel.loadNearby() }
    LaunchedEffect(tab) { if (tab == 2 && nearby == null) permission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.review)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(stringResource(R.string.review_day)) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(stringResource(R.string.review_on_this_day)) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text(stringResource(R.string.review_nearby)) })
            }
            when (tab) {
                0 -> {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = viewModel::previousDay) { Icon(Icons.Default.ChevronLeft, null) }
                        Text(
                            when (day) {
                                LocalDate.now() -> stringResource(R.string.today)
                                LocalDate.now().minusDays(1) -> stringResource(R.string.yesterday)
                                else -> dayFmt.format(day)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        IconButton(onClick = viewModel::nextDay, enabled = day.isBefore(LocalDate.now())) { Icon(Icons.Default.ChevronRight, null) }
                    }
                    MemoList(dayMemos.map { it to null }, account?.serverUrl.orEmpty(), stringResource(R.string.review_empty_day), viewModel, onOpenMemo, onEditMemo)
                }
                1 -> MemoList(
                    throwbacks.map { t ->
                        t.memo to when {
                            t.yearsAgo == 1 && t.monthsAgo == 12 -> stringResource(R.string.one_year_ago)
                            t.yearsAgo >= 1 && t.monthsAgo % 12 == 0 -> stringResource(R.string.years_ago, t.yearsAgo)
                            t.monthsAgo == 1 -> stringResource(R.string.one_month_ago)
                            else -> stringResource(R.string.months_ago, t.monthsAgo)
                        }
                    },
                    account?.serverUrl.orEmpty(),
                    stringResource(R.string.review_empty_history),
                    viewModel, onOpenMemo, onEditMemo,
                )
                else -> MemoList(
                    nearby.orEmpty().map { n -> n.memo to (if (n.metres < 1000) "%.0f m".format(n.metres) else "%.1f km".format(n.metres / 1000)) },
                    account?.serverUrl.orEmpty(),
                    stringResource(if (nearbyError != null) R.string.review_nearby_empty else R.string.review_nearby_empty),
                    viewModel, onOpenMemo, onEditMemo,
                )
            }
        }
    }
}

@Composable
private fun MemoList(
    items: List<Pair<Memo, String?>>,
    serverUrl: String,
    emptyText: String,
    viewModel: ReviewViewModel,
    onOpenMemo: (String) -> Unit,
    onEditMemo: (String) -> Unit,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp, horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(items, key = { it.first.localId }) { (memo, label) ->
            Column {
                label?.let { Text(it.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)) }
                MemoCard(
                    memo = memo,
                    serverUrl = serverUrl,
                    onClick = { onOpenMemo(memo.localId) },
                    onToggleTask = { _, _ -> },
                    onEdit = { onEditMemo(memo.localId) },
                    onPin = { viewModel.togglePin(memo) },
                    onArchive = { viewModel.archive(memo) },
                    onDelete = { viewModel.delete(memo) },
                    onColour = {},
                )
            }
        }
    }
}
