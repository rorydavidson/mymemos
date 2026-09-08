package com.keltruc.mymemos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.keltruc.mymemos.R
import com.keltruc.mymemos.model.NoteColour

fun NoteColour.raw(): Color = Color(0xFF000000 or hex)

/** Card background for a tinted memo: the colour washed into the surface so text stays legible. */
@Composable
fun NoteColour.tint(): Color {
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val dark = surface.luminance() < 0.5f
    val strength = when (this) {
        NoteColour.WHITE -> if (dark) 0.10f else 0.0f
        NoteColour.BLACK -> if (dark) 0.35f else 0.12f
        NoteColour.YELLOW, NoteColour.LIME, NoteColour.AQUA -> if (dark) 0.28f else 0.30f
        else -> if (dark) 0.30f else 0.20f
    }
    return raw().copy(alpha = strength).compositeOverColor(surface)
}

private fun Color.compositeOverColor(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1 - a),
        green = green * a + background.green * (1 - a),
        blue = blue * a + background.blue * (1 - a),
        alpha = 1f,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColourPickerDialog(current: NoteColour?, onPick: (NoteColour?) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.colour)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 6) {
                Swatch(
                    colour = MaterialTheme.colorScheme.surfaceContainer,
                    selected = current == null,
                    onClick = { onPick(null) },
                    icon = Icons.Default.Block,
                )
                NoteColour.entries.forEach { c ->
                    Swatch(colour = c.tint(), selected = current == c, onClick = { onPick(c) }, ring = c.raw())
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Swatch(
    colour: Color,
    selected: Boolean,
    onClick: () -> Unit,
    ring: Color = MaterialTheme.colorScheme.outlineVariant,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(colour)
            .border(if (selected) 3.dp else 1.5.dp, if (selected) MaterialTheme.colorScheme.onSurface else ring, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            selected -> Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.onSurface)
            icon != null -> Icon(icon, contentDescription = stringResource(R.string.colour_none), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
