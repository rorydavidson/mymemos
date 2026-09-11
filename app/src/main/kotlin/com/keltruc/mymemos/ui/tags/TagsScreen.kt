package com.keltruc.mymemos.ui.tags

import com.keltruc.mymemos.data.text.EmojiCatalogue
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.NoteColour
import com.keltruc.mymemos.ui.components.ColourPickerDialog
import com.keltruc.mymemos.ui.components.tint


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagsScreen(onBack: () -> Unit, initialTag: String? = null, viewModel: TagsViewModel = hiltViewModel()) {
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(initialTag) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tags)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        if (tags.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.tags_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(tags, key = { it.tag }) { row ->
                ListItem(
                    headlineContent = { Text("#${row.tag}") },
                    leadingContent = {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(row.style?.colour?.tint() ?: MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) { Text(row.style?.emoji ?: "#", style = MaterialTheme.typography.titleMedium) }
                    },
                    modifier = Modifier.clickable { editing = row.tag },
                )
            }
        }
    }

    editing?.let { tag ->
        val current = tags.firstOrNull { it.tag == tag }?.style
        var emoji by remember(tag) { mutableStateOf(current?.emoji.orEmpty()) }
        var colour by remember(tag) { mutableStateOf(current?.colour) }
        var showColours by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("#$tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(4) },
                        label = { Text(stringResource(R.string.tag_emoji)) },
                        supportingText = { Text(stringResource(R.string.tag_emoji_hint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(8),
                        modifier = Modifier.fillMaxWidth().height(220.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        EmojiCatalogue.categories.forEach { (name, items) ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                            }
                            items(items) { e ->
                                Text(
                                    e,
                                    Modifier.clip(CircleShape).background(if (emoji == e) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                        .clickable { emoji = e }.padding(6.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.clickable { showColours = true }) {
                        Box(Modifier.size(32.dp).clip(CircleShape).background(colour?.tint() ?: MaterialTheme.colorScheme.surfaceContainerHigh))
                        Text(stringResource(R.string.tag_colour) + ": " + (colour?.name?.lowercase() ?: stringResource(R.string.colour_none)))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.setStyle(tag, emoji.trim(), colour); editing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text(stringResource(R.string.cancel)) } },
        )
        if (showColours) {
            ColourPickerDialog(current = colour, onPick = { colour = it; showColours = false }, onDismiss = { showColours = false })
        }
    }
}

@Suppress("unused") private val keep = NoteColour.entries
