package com.keltruc.mymemos.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.keltruc.mymemos.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Date then time, giving back an instant in the device zone. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimePickerDialog(onPicked: (Instant) -> Unit, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf<LocalDate?>(null) }
    if (date == null) {
        val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = {
                    date = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: LocalDate.now()
                }) { Text(stringResource(R.string.next)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = state) }
    } else {
        val time = rememberTimePickerState(initialHour = 9, initialMinute = 0)
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.pick_time)) },
            text = { TimePicker(state = time) },
            confirmButton = {
                TextButton(onClick = {
                    onPicked(date!!.atTime(LocalTime.of(time.hour, time.minute)).atZone(ZoneId.systemDefault()).toInstant())
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

/**
 * Date only, for a due date written into a task line. The full [DateTimePickerDialog] asks for a
 * time as well, which a `@yyyy-MM-dd` token has nowhere to put.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueDatePickerDialog(onPicked: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = System.currentTimeMillis())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                // The picker works in UTC, so read the day back the same way rather than shifting
                // it into the device zone and landing on the day before.
                val picked = state.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                onPicked(picked ?: LocalDate.now())
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    ) { DatePicker(state = state) }
}
