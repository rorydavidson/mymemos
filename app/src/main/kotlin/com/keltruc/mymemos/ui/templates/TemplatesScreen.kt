package com.keltruc.mymemos.ui.templates

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.Template

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplatesScreen(onBack: () -> Unit, viewModel: TemplatesViewModel = hiltViewModel()) {
    val templates by viewModel.templates.collectAsStateWithLifecycle()
    val recurring by viewModel.recurring.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Template?>(null) }
    var timing by remember { mutableStateOf<Template?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.templates)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = { editing = null; showEditor = true }) { Icon(Icons.Default.Add, stringResource(R.string.template_new)) } },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { Text(stringResource(R.string.template_hint), Modifier.padding(20.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(templates, key = { it.id }) { t ->
                val rec = recurring.firstOrNull { it.templateTitle == t.title }
                ListItem(
                    headlineContent = { Text(t.title) },
                    supportingContent = {
                        Text(
                            (rec?.let { "%s %02d:%02d · ".format(stringResource(R.string.recurring), it.hour, it.minute) } ?: "") + t.body.lineSequence().firstOrNull().orEmpty(),
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { if (rec == null) timing = t else viewModel.setRecurring(t.title, null, null) }) {
                                Icon(Icons.Default.Alarm, stringResource(R.string.recurring), tint = if (rec != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { viewModel.delete(t.id) }) { Icon(Icons.Default.Delete, stringResource(R.string.delete)) }
                        }
                    },
                    modifier = Modifier.clickable { editing = t; showEditor = true },
                )
            }
        }
    }

    timing?.let { t ->
        val time = rememberTimePickerState(initialHour = 21, initialMinute = 0)
        AlertDialog(
            onDismissRequest = { timing = null },
            title = { Text(stringResource(R.string.recurring)) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.recurring_hint)); TimePicker(state = time) } },
            confirmButton = { TextButton(onClick = { viewModel.setRecurring(t.title, time.hour, time.minute); timing = null }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { timing = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showEditor) {
        var title by remember { mutableStateOf(editing?.title.orEmpty()) }
        var body by remember { mutableStateOf(editing?.body.orEmpty()) }
        AlertDialog(
            onDismissRequest = { showEditor = false },
            title = { Text(stringResource(if (editing == null) R.string.template_new else R.string.templates)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.template_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text(stringResource(R.string.template_body)) }, minLines = 6, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(enabled = title.isNotBlank(), onClick = { viewModel.save(editing?.id, title.trim(), body); showEditor = false }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { showEditor = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}
