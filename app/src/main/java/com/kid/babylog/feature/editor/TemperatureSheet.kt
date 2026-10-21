package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.TemperatureMethod
import com.kid.babylog.core.ui.LocalClock
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatShortDay
import com.kid.babylog.core.ui.temperatureMethodLabel
import com.kid.babylog.core.ui.temperatureValue
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate

/** Ширина поля температуры: «38,4» крупно и место под «°C». */
private val TemperatureFieldWidth = 140.dp

/** С чего начинается значение, если прошлого замера нет: обычная температура подмышкой. */
private const val DEFAULT_TENTHS = 366

/** Подписи мест измерения по порядку в листе. */
private val METHODS = TemperatureMethod.entries.map { it to temperatureMethodLabel(it) }

/**
 * Значение из поля температуры (ADR-0041): «38,4», «38.4» и «384» — 38,4 °C, «38» — 38,0 °C. Вне
 * [Temperature.RANGE] или неполное («3», «38,») — null.
 */
fun parseTemperature(text: String): Int? {
    val value = text.trim().replace('.', ',')
    if (value.isEmpty() || value.endsWith(",")) return null
    val tenths = if (',' in value) {
        val (whole, fraction) = value.split(',', limit = 2)
        if (fraction.length != 1) return null
        whole.toIntOrNull()?.let { it * 10 + (fraction.toIntOrNull() ?: return null) }
    } else {
        value.toIntOrNull()?.let { if (it < 100) it * 10 else it }
    }
    return tenths?.takeIf { it in Temperature.RANGE }
}

/** Набор в поле: цифры и одна запятая (точку клавиатуры меняем на запятую), после запятой — одна цифра. */
fun temperatureInput(text: String): String {
    val cleaned = text.replace('.', ',').filter { it.isDigit() || it == ',' }
    val comma = cleaned.indexOf(',')
    if (comma < 0) return cleaned.take(3)
    return cleaned.take(comma).take(2) + "," + cleaned.drop(comma + 1).filter(Char::isDigit).take(1)
}

/**
 * Лист температуры (ADR-0041): число с клавиатуры, «−» и «+» по 0,1, где мерили, когда и комментарий.
 * Значение и место начинаются с прошлого замера [last]: ночью мерят снова и снова тем же градусником.
 *
 * [offerIllness] — у ребёнка нет идущей болезни: переключатель «Заболел(а)» начинает её с этого замера
 * (ADR-0040). Порога нет: приложение не решает, с какой температуры «уже болезнь». [onSaveWithMedicine] —
 * болезнь идёт: «И лекарство» сохраняет замер и сразу открывает лист лекарства; null — кнопки нет.
 */
@Composable
internal fun TemperatureSheet(
    initial: Temperature?,
    last: Temperature?,
    sex: Sex?,
    offerIllness: Boolean,
    onDismiss: () -> Unit,
    onSave: (tenths: Int, method: TemperatureMethod?, time: Long, note: String, startIllness: Boolean) -> Unit,
    onSaveWithMedicine: ((tenths: Int, method: TemperatureMethod?, time: Long, note: String) -> Unit)? = null,
) {
    val isEdit = initial != null
    var text by rememberSaveable { mutableStateOf(temperatureValue(initial?.tenths ?: last?.tenths ?: DEFAULT_TENTHS)) }
    var method by rememberSaveable { mutableStateOf(initial?.method ?: last?.method) }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var editTime by rememberSaveable { mutableLongStateOf(initial?.start ?: 0L) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    var startIllness by rememberSaveable { mutableStateOf(false) }
    val resolveTime = rememberTimeResolver()
    val tenths = parseTemperature(text)
    AppSheet(
        onDismiss = onDismiss,
        title = "Температура",
        icon = R.drawable.ic_thermometer,
        confirm = SheetAction("Сохранить", enabled = tenths != null) {
            if (tenths != null) onSave(tenths, method, resolveTime(isEdit, minutesAgo, editTime), note.trim(), offerIllness && startIllness)
        },
        secondary = onSaveWithMedicine?.takeIf { !isEdit }?.let { save ->
            SheetAction("И лекарство", enabled = tenths != null) {
                if (tenths != null) save(tenths, method, resolveTime(false, minutesAgo, 0L), note.trim())
            }
        },
    ) {
        TemperatureField(text, { text = it }, previous = last.takeIf { !isEdit })
        Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.xs)) {
            Text("Где мерили", style = MaterialTheme.typography.titleSmall)
            OptionChips(METHODS, method, onSelect = { method = if (method == it) null else it })
        }
        TimeSection(isEdit, minutesAgo, { minutesAgo = it }, editTime, { editTime = it })
        if (offerIllness && !isEdit) IllnessSwitch(sex, startIllness) { startIllness = it }
        NoteField(note, { note = it })
    }
}

/**
 * Значение крупно посередине, по краям — «−» и «+» на 0,1. Набирают и с клавиатуры: от 36,6 до 39,2
 * кнопками — двадцать шесть касаний. Под ним — прошлый замер [previous]: «в прошлый раз вчера в 21:00 — 37,6».
 */
@Composable
internal fun TemperatureField(text: String, onText: (String) -> Unit, previous: Temperature?) {
    val spacing = BabyLogTheme.spacing
    val step = { delta: Int ->
        val current = parseTemperature(text) ?: DEFAULT_TENTHS
        onText(temperatureValue((current + delta).coerceIn(Temperature.RANGE)))
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.l)) {
            FilledTonalIconButton(onClick = { step(-1) }) { AppIcon(R.drawable.ic_remove, contentDescription = "Меньше на 0,1") }
            OutlinedTextField(
                value = text,
                onValueChange = { onText(temperatureInput(it)) },
                textStyle = MaterialTheme.typography.headlineMedium.copy(textAlign = TextAlign.Center),
                suffix = { Text("°C") },
                singleLine = true,
                isError = parseTemperature(text) == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.width(TemperatureFieldWidth).semantics { contentDescription = "Температура" },
            )
            FilledTonalIconButton(onClick = { step(1) }) { AppIcon(R.drawable.ic_add, contentDescription = "Больше на 0,1") }
        }
        val hint = when {
            parseTemperature(text) == null -> "От 34,0 до 43,0 °C"
            previous != null -> {
                val now = LocalClock.current.millis()
                "в прошлый раз ${formatShortDay(previous.start.toLocalDate(), now.toLocalDate())} в ${formatClock(previous.start)} — ${temperatureValue(previous.tenths)}"
            }
            else -> null
        }
        hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** «Заболел(а)» — болезнь начнётся с этого замера (ADR-0040). Переключает вся строка. Пол — для слова, имени нет. */
@Composable
private fun IllnessSwitch(sex: Sex?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChecked),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m),
    ) {
        AppIcon(R.drawable.ic_illness, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Column(Modifier.weight(1f)) {
            Text(bySex(sex, "Заболел", "Заболела"), style = MaterialTheme.typography.bodyLarge)
            Text("болезнь начнётся с этого замера", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
