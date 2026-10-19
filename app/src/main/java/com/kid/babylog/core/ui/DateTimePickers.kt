package com.kid.babylog.core.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.theme.BabyLogTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val CHIP_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", RU)
private val CHIP_TIME = DateTimeFormatter.ofPattern("HH:mm")

/**
 * Поле «дата + время» для редактирования записи: подпись и под ней два чипа, открывающие пикеры
 * Material 3 в нижних листах (ADR-0018). [value] — epoch millis в системной таймзоне.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateTimeField(label: String, value: Long, onChange: (Long) -> Unit) {
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val dt = Instant.ofEpochMilli(value).atZone(zone)

    // Подпись над чипами: с иконками подпись и два чипа в одну строку не влезают на экране 360 dp
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
        DateSheet(initial = dt.toLocalDate(), confirmLabel = "ОК", onDismiss = { showDate = false }) { date ->
            onChange(date.atTime(dt.toLocalTime()).atZone(zone).toInstant().toEpochMilli())
            showDate = false
        }
    }

    if (showTime) {
        val state = rememberTimePickerState(dt.hour, dt.minute, is24Hour = true)
        AppSheet(
            onDismiss = { showTime = false },
            title = "Время",
            icon = R.drawable.ic_schedule,
            scrollable = false,
            confirm = SheetAction("ОК") {
                onChange(dt.toLocalDate().atTime(state.hour, state.minute).atZone(zone).toInstant().toEpochMilli())
                showTime = false
            },
        ) {
            TimePicker(state = state, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

/** Выбор даты рождения малыша. [initial]/результат — epoch millis локальной полуночи. */
@Composable
fun BirthDateSheet(initial: Long?, onDismiss: () -> Unit, onSave: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    DateSheet(
        initial = initial?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() },
        confirmLabel = "Сохранить",
        onDismiss = onDismiss,
    ) { date -> onSave(date.atStartOfDay(zone).toInstant().toEpochMilli()) }
}

/**
 * Календарь Material 3 в нижнем листе. Лист — во всю ширину, без своего заголовка: у календаря
 * есть свой («Выберите дату» и выбранная дата), а шириной он не меньше 360 dp.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateSheet(initial: LocalDate?, confirmLabel: String, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val today = LocalDate.now(LocalClock.current)
    // DatePicker работает в UTC-миллисекундах «полуночи выбранного дня»
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        selectableDates = remember(today) { notAfter(today) },
    )
    AppSheet(
        onDismiss = onDismiss,
        scrollable = false,
        edgeToEdge = true,
        confirm = SheetAction(confirmLabel, enabled = state.selectedDateMillis != null) {
            state.selectedDate()?.let(onPick)
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun DatePickerState.selectedDate(): LocalDate? =
    selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }

/** Иконка в начале чипа даты или времени — размера, который задаёт Material 3. */
@Composable
internal fun ChipIcon(@DrawableRes id: Int) {
    AppIcon(id, contentDescription = null, modifier = Modifier.size(AssistChipDefaults.IconSize))
}

/**
 * Дни не позже [today]: в дневнике — то, что уже было, и рождение тоже. Запись «из будущего» сдвинула бы
 * сводки и «Аналитику».
 */
@OptIn(ExperimentalMaterial3Api::class)
internal fun notAfter(today: LocalDate): SelectableDates = object : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate() <= today

    override fun isSelectableYear(year: Int): Boolean = year <= today.year
}
