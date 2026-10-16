package com.kid.babylog.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.theme.BabyLogTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CHIP_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", RU)
private val CHIP_TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Поле «дата + время» для редактирования записи: подпись и под ней два чипа, открывающие
 * системные Material-пикеры. [value] — epoch millis в системной таймзоне.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(label: String, value: Long, onChange: (Long) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(value).atZone(zone)

    // Подпись над чипами: с иконками подпись и два чипа в одну строку не влезают в диалог на экране 360 dp
    Column {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
            AssistChip(
                onClick = { showDate = true },
                label = { Text(dt.toLocalDate().format(CHIP_DATE)) },
                leadingIcon = { ChipIcon(R.drawable.ic_calendar) },
            )
            AssistChip(
                onClick = { showTime = true },
                label = { Text(dt.toLocalTime().format(CHIP_TIME)) },
                leadingIcon = { ChipIcon(R.drawable.ic_schedule) },
            )
        }
    }

    if (showDate) {
        // DatePicker работает в UTC-миллисекундах «полуночи выбранного дня»
        val state = rememberDatePickerState(
            initialSelectedDateMillis = dt.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { sel ->
                        val date = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate()
                        onChange(date.atTime(dt.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
                    }
                    showDate = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Отмена") } },
        ) {
            DatePicker(state = state)
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(dt.hour, dt.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onChange(
                        dt.toLocalDate().atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli()
                    )
                    showTime = false
                }) { Text("ОК") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Отмена") } },
        )
    }
}

/** Выбор даты рождения малыша. [initial]/результат — epoch millis локальной полуночи. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthDatePickerDialog(initial: Long?, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.let {
            Instant.ofEpochMilli(it).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = state.selectedDateMillis != null,
                onClick = {
                    val date = Instant.ofEpochMilli(state.selectedDateMillis!!)
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    onSave(date.atStartOfDay(zone).toInstant().toEpochMilli())
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    ) {
        DatePicker(state = state)
    }
}

/** Иконка в начале чипа даты или времени — размера, который задаёт Material 3. */
@Composable
internal fun ChipIcon(@DrawableRes id: Int) {
    AppIcon(id, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
}
