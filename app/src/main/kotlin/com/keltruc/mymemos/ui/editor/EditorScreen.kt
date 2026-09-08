package com.keltruc.mymemos.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.Visibility
import com.keltruc.mymemos.ui.components.AttachmentStrip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(onDone: () -> Unit, viewModel: EditorViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    var field by remember { mutableStateOf(TextFieldValue()) }
    var visibilityMenu by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    // Seed the field once the memo has loaded; afterwards the field drives the view model.
    LaunchedEffect(state.loaded) {
        if (state.loaded && field.text.isEmpty() && state.content.isNotEmpty()) {
            field = TextFieldValue(state.content, TextRange(state.content.length))
        }
        if (state.loaded) focus.requestFocus()
    }
    LaunchedEffect(state.saved) { if (state.saved) onDone() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::attach)
    }

    fun update(value: TextFieldValue) {
        field = value
        viewModel.onContent(value.text)
    }

    fun insertAtCursor(prefix: String, suffix: String = "", lineStart: Boolean = false) {
        val text = field.text
        val sel = field.selection
        if (lineStart) {
            val lineBegin = text.lastIndexOf('\n', sel.start - 1).let { if (it < 0) 0 else it + 1 }
            val newText = text.substring(0, lineBegin) + prefix + text.substring(lineBegin)
            update(TextFieldValue(newText, TextRange(sel.start + prefix.length)))
        } else {
            val selected = text.substring(sel.start, sel.end)
            val newText = text.substring(0, sel.start) + prefix + selected + suffix + text.substring(sel.end)
            val cursor = if (selected.isEmpty()) sel.start + prefix.length else sel.end + prefix.length + suffix.length
            update(TextFieldValue(newText, TextRange(cursor)))
        }
    }

    // Word being typed right before the cursor, if it starts with '#'.
    val currentTagPrefix = remember(field) {
        val upto = field.text.substring(0, field.selection.start.coerceIn(0, field.text.length))
        val word = upto.takeLastWhile { !it.isWhitespace() }
        if (word.startsWith("#") && word.length > 1) word.drop(1) else null
    }
    val suggestions = remember(currentTagPrefix, tags) {
        currentTagPrefix?.let { p -> tags.filter { it.startsWith(p, ignoreCase = true) && it != p }.take(8) }.orEmpty()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (state.isNew) R.string.new_memo else R.string.edit_memo)) },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
                },
                actions = {
                    IconToggleButton(checked = state.pinned, onCheckedChange = viewModel::onPinned) {
                        Icon(Icons.Default.PushPin, contentDescription = stringResource(R.string.pin))
                    }
                    IconButton(onClick = { visibilityMenu = true }) {
                        Icon(
                            when (state.visibility) {
                                Visibility.PRIVATE -> Icons.Default.Lock
                                Visibility.PROTECTED -> Icons.Default.Workspaces
                                Visibility.PUBLIC -> Icons.Default.Public
                            },
                            contentDescription = stringResource(R.string.visibility),
                        )
                    }
                    DropdownMenu(expanded = visibilityMenu, onDismissRequest = { visibilityMenu = false }) {
                        Visibility.entries.forEach { v ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        stringResource(
                                            when (v) {
                                                Visibility.PRIVATE -> R.string.visibility_private
                                                Visibility.PROTECTED -> R.string.visibility_protected
                                                Visibility.PUBLIC -> R.string.visibility_public
                                            },
                                        ),
                                    )
                                },
                                onClick = { viewModel.onVisibility(v); visibilityMenu = false },
                            )
                        }
                    }
                    FilledTonalButton(
                        onClick = viewModel::save,
                        enabled = state.content.isNotBlank() || state.attachments.isNotEmpty(),
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null)
                        Spacer(Modifier.padding(2.dp))
                        Text(stringResource(R.string.save))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().imePadding()) {
            TextField(
                value = field,
                onValueChange = ::update,
                placeholder = { Text(stringResource(R.string.editor_hint)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                textStyle = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).fillMaxWidth().focusRequester(focus),
            )
            AttachmentStrip(
                attachments = state.attachments,
                serverUrl = state.serverUrl,
                onRemove = viewModel::removeAttachment,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            if (suggestions.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    suggestions.forEach { tag ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                val prefixLen = currentTagPrefix!!.length
                                val start = field.selection.start - prefixLen
                                val newText = field.text.substring(0, start) + tag + " " + field.text.substring(field.selection.start)
                                update(TextFieldValue(newText, TextRange(start + tag.length + 1)))
                            },
                            label = { Text("#$tag") },
                        )
                    }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                IconButton(onClick = { insertAtCursor("- [ ] ", lineStart = true) }) { Icon(Icons.Default.CheckBox, "Task") }
                IconButton(onClick = { insertAtCursor("- ", lineStart = true) }) { Icon(Icons.Default.FormatListBulleted, "List") }
                IconButton(onClick = { insertAtCursor("**", "**") }) { Icon(Icons.Default.FormatBold, "Bold") }
                IconButton(onClick = { insertAtCursor("`", "`") }) { Icon(Icons.Default.Code, "Code") }
                IconButton(onClick = { insertAtCursor("#") }) { Icon(Icons.Default.Tag, "Tag") }
                IconButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Default.Image, stringResource(R.string.attach_image))
                }
            }
        }
    }
}
