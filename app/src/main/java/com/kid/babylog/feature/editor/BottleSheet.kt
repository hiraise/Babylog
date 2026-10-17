package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.kid.babylog.R
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.ui.DateTimeField
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.milkLabel
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Правка бутылочки (ADR-0025): объём, что внутри, время и комментарий. */
@Composable
internal fun BottleSheet(
    initial: Bottle,
    onDismiss: () -> Unit,
    onSave: (volumeMl: Int, milk: Milk?, time: Long, note: String) -> Unit,
) {
    var volume by rememberSaveable { mutableStateOf(initial.volumeMl.toString()) }
    var milk by rememberSaveable { mutableStateOf(initial.milk) }
    var time by rememberSaveable { mutableLongStateOf(initial.start) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    val volumeMl = bottleVolume(volume)
    AppSheet(
        onDismiss = onDismiss,
        title = "Бутылочка",
        icon = R.drawable.ic_bottle,
        confirm = SheetAction("Сохранить", enabled = volumeMl != null) {
            if (volumeMl != null) onSave(volumeMl, milk, time, note.trim())
        },
    ) {
        BottleFields(volume, { volume = it }, milk, { milk = it })
        DateTimeField("Когда:", time) { time = it }
        NoteField(note, { note = it })
    }
}

/**
 * Объём и что в бутылочке. Объём — числом или быстрой кнопкой; кнопки — частые объёмы, поле —
 * для остальных. Что внутри необязательно: повторное касание выбранного снимает выбор.
 * [volume] — текст поля как есть: пустое поле — ещё не введено, а не ошибка.
 */
@Composable
internal fun BottleFields(volume: String, onVolume: (String) -> Unit, milk: Milk?, onMilk: (Milk?) -> Unit) {
    val spacing = BabyLogTheme.spacing
    val invalid = volume.isNotEmpty() && bottleVolume(volume) == null
    OptionChips(
        options = QUICK_VOLUMES.map { it to "$it мл" },
        selected = volume.toIntOrNull(),
        onSelect = { onVolume(it.toString()) },
    )
    OutlinedTextField(
        value = volume,
        onValueChange = { text -> onVolume(text.filter(Char::isDigit).take(3)) },
        label = { Text("Объём") },
        suffix = { Text("мл") },
        isError = invalid,
        // null, а не пустая лямбда: иначе под полем остаётся пустая строка
        supportingText = if (invalid) {
            { Text("От ${Bottle.RANGE.first} до ${Bottle.RANGE.last} мл") }
        } else null,
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
    Column {
        Text("Что в бутылочке:", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            Milk.entries.forEach { option ->
                ChoiceChip(
                    selected = milk == option,
                    onClick = { onMilk(if (milk == option) null else option) },
                    label = milkLabel(option).replaceFirstChar(Char::titlecase),
                )
            }
        }
    }
}

/** Объём из текста поля; вне [Bottle.RANGE] или не число — null. */
internal fun bottleVolume(text: String): Int? = text.toIntOrNull()?.takeIf { it in Bottle.RANGE }

/** Частые объёмы: от первых недель до полугода. */
private val QUICK_VOLUMES = listOf(60, 90, 120, 150)
