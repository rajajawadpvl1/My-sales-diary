package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.util.LocalDateHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalDatePickerModal(
    initialIsoDate: String,
    title: String,
    onDateSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialUtcMillis = LocalDateHelper.isoDateToPickerUtcMillis(initialIsoDate)
        ?: LocalDateHelper.isoDateToPickerUtcMillis(LocalDateHelper.todayIso())
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialUtcMillis,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = datePickerState.selectedDateMillis
                    if (millis != null) {
                        onDateSelected(LocalDateHelper.pickerUtcMillisToIsoDate(millis))
                    }
                    onDismiss()
                },
            ) {
                Text("Select")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp),
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalTimePickerModal(
    initialTime: String?,
    onTimeSelected: (String) -> Unit,
    onClearTime: (() -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    val (initHour, initMinute) = LocalDateHelper.parseTime(initialTime)
    val timePickerState = rememberTimePickerState(
        initialHour = initHour,
        initialMinute = initMinute,
        is24Hour = false,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Follow-up Time",
                style = MaterialTheme.typography.titleMedium,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(modifier = Modifier.height(4.dp))
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onTimeSelected(
                        LocalDateHelper.formatTime(timePickerState.hour, timePickerState.minute)
                    )
                    onDismiss()
                },
            ) {
                Text("Set Time")
            }
        },
        dismissButton = {
            if (onClearTime != null) {
                TextButton(
                    onClick = {
                        onClearTime()
                        onDismiss()
                    },
                ) {
                    Text("No Time")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}
