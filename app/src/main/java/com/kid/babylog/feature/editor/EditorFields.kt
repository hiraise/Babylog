package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.kid.babylog.core.ui.DateTimeField
import com.kid.babylog.core.ui.LocalClock
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.theme.BabyLogTheme
import kotlinx.coroutines.delay

private val TIME_OPTIONS = listOf(
    "Сейчас" to 0,
    "−5 мин" to 5,
    "−15 мин" to 15,
    "−30 мин" to 30,
    "−1 ч" to 60,
)

/** Выбор «когда это было»: сейчас или N минут назад. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeAgoPicker(minutesAgo: Int, onMinutesAgo: (Int) -> Unit) {
    val clock = LocalClock.current
    val spacing = BabyLogTheme.spacing
    // Часы тикают, пока лист открыт, чтобы подпись не расходилась с реально сохраняемым временем
    var now by remember { mutableLongStateOf(clock.millis()) }
    LaunchedEffect(clock) {
        while (true) {
            now = clock.millis()
            delay(1_000)
        }
    }
    Column {
        Text("Когда:", style = MaterialTheme.typography.titleSmall)
        // Между рядами чипов уже есть воздух: каждый чип занимает 48 dp по высоте под палец
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            TIME_OPTIONS.forEach { (label, minutes) ->
                ChoiceChip(
                    selected = minutesAgo == minutes,
                    onClick = { onMinutesAgo(minutes) },
                    label = label,
                )
            }
        }
        Text(
            "Время: " + formatClock(now - minutesAgo * 60_000L),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs),
        )
    }
}

/** Добавление: чипы «N минут назад»; редактирование: полноценные дата и время. */
@Composable
internal fun TimeSection(
    isEdit: Boolean,
    minutesAgo: Int,
    onMinutesAgo: (Int) -> Unit,
    editTime: Long,
    onEditTime: (Long) -> Unit,
) {
    if (isEdit) {
        DateTimeField("Когда:", editTime, onEditTime)
    } else {
        TimeAgoPicker(minutesAgo, onMinutesAgo)
    }
}

/** Время записи: при правке — выбранное, при добавлении — «сейчас минус N минут». */
@Composable
internal fun rememberTimeResolver(): (isEdit: Boolean, minutesAgo: Int, editTime: Long) -> Long {
    val clock = LocalClock.current
    return remember(clock) {
        { isEdit, minutesAgo, editTime -> if (isEdit) editTime else clock.millis() - minutesAgo * 60_000L }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> OptionChips(
    options: List<Pair<T, String>>, // значение и подпись
    selected: T?,
    onSelect: (T) -> Unit,
) {
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
    ) {
        options.forEach { (value, label) ->
            ChoiceChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = label,
            )
        }
    }
}

@Composable
internal fun NoteField(value: String, onChange: (String) -> Unit, label: String = "Комментарий (необязательно)") {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}
