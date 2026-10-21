package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.kid.babylog.R
import com.kid.babylog.core.model.IllnessMedicine
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.ui.LocalClock
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.formatAgo
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatShortDay
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.toLocalDate
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.HintChip
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Лист лекарства. [pastMedicines] — лекарства из прежних записей ребёнка для подсказок. Касание
 * подсказки подставляет и комментарий к этому лекарству, если свой ещё не набран ([noteAfterSuggestion]).
 * [illnessMedicines] — лекарства идущей болезни (ADR-0040): над временем — когда давали каждое и сколько
 * раз, набранное — первым. Ночью это главный вопрос; советов о дозах и интервалах нет — только факты.
 */
@Composable
internal fun MedicineSheet(
    initial: Medicine?,
    pastMedicines: List<PastMedicine>,
    onDismiss: () -> Unit,
    illnessMedicines: List<IllnessMedicine> = emptyList(),
    onSave: (name: String, time: Long, note: String) -> Unit,
) {
    val isEdit = initial != null
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(textWithCursorAtEnd(initial?.name ?: ""))
    }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var editTime by rememberSaveable { mutableLongStateOf(initial?.start ?: 0L) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    // Комментарий, подставленный последней подсказкой: его следующая подсказка может заменить.
    var autoNote by rememberSaveable { mutableStateOf("") }
    val resolveTime = rememberTimeResolver()
    val suggestions = remember(pastMedicines, name.text) { medicineSuggestions(pastMedicines, name.text) }
    AppSheet(
        onDismiss = onDismiss,
        title = "Лекарство",
        icon = R.drawable.ic_medicine,
        confirm = SheetAction("Сохранить", enabled = name.text.isNotBlank()) {
            onSave(name.text.trim(), resolveTime(isEdit, minutesAgo, editTime), note.trim())
        },
    ) {
        MedicineNameField(name, { name = it }, suggestions) { picked ->
            note = noteAfterSuggestion(note, autoNote, picked)
            autoNote = picked.note
        }
        if (!isEdit && illnessMedicines.isNotEmpty()) IllnessMedicinesCard(illnessMedicines, name.text)
        TimeSection(isEdit, minutesAgo, { minutesAgo = it }, editTime, { editTime = it })
        NoteField(note, { note = it }, label = "Комментарий (доза, причина…)")
    }
}

/**
 * Поле названия, под ним — [suggestions] чипами [HintChip]. Не выпадающим меню: их заменяют нижние
 * листы (ADR-0018), а лист поверх формы закрыл бы поле. Касание подсказки подставляет название
 * целиком, курсор встаёт в конец, клавиатура остаётся открытой, затем зовёт [onPick]. Название
 * набрано целиком — его подсказка пропадает, а с ней и ряд, если других нет.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicineNameField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    suggestions: List<PastMedicine>,
    onPick: (PastMedicine) -> Unit,
) {
    val spacing = BabyLogTheme.spacing
    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Название") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        if (suggestions.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                suggestions.forEach { suggestion ->
                    HintChip(label = suggestion.name, onClick = {
                        onValueChange(textWithCursorAtEnd(suggestion.name))
                        onPick(suggestion)
                    })
                }
            }
        }
    }
}

/**
 * «В эту болезнь»: до [MAX_ILLNESS_LINES] лекарств болезни, набранное [typed] — первым. У каждого —
 * сколько прошло с последнего приёма, когда это было, доза из его комментария и сколько раз за болезнь.
 */
@Composable
private fun IllnessMedicinesCard(medicines: List<IllnessMedicine>, typed: String) {
    val colors = MaterialTheme.colorScheme
    val spacing = BabyLogTheme.spacing
    val now = LocalClock.current.millis()
    val key = typed.trim().lowercase().replace('ё', 'е')
    val shown = medicines.sortedByDescending { key.isNotEmpty() && it.name.lowercase().replace('ё', 'е') == key }.take(MAX_ILLNESS_LINES)
    AppCard(Modifier.fillMaxWidth(), containerColor = colors.surfaceContainerHighest) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("В эту болезнь", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("из дневника", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            shown.forEach { medicine ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                    AppIcon(R.drawable.ic_medicine, contentDescription = null, modifier = Modifier.size(IllnessIconSize), tint = colors.primary)
                    Column {
                        Text("${medicine.name} — ${formatAgo(now - medicine.last)}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOfNotNull(
                                "${formatShortDay(medicine.last.toLocalDate(), now.toLocalDate())} в ${formatClock(medicine.last)}",
                                medicine.note.ifBlank { null },
                                "за болезнь ${medicine.count} ${plural(medicine.count, "раз", "раза", "раз")}",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Лекарств болезни в листе — не больше стольких: жаропонижающих обычно два, третье — антибиотик. */
private const val MAX_ILLNESS_LINES = 3
private val IllnessIconSize = 20.dp

/**
 * Текст поля без набираемого клавиатурой слова и с курсором в конце: иначе клавиатура заменит
 * подставленное название своим словом, а курсор останется посередине.
 */
private fun textWithCursorAtEnd(text: String) = TextFieldValue(text, selection = TextRange(text.length))
