package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.kid.babylog.R
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.HintChip
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Лист лекарства. [pastNames] — названия из прежних записей ребёнка для подсказок ([pastMedicineNames]). */
@Composable
internal fun MedicineSheet(
    initial: Medicine?,
    pastNames: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, time: Long, note: String) -> Unit,
) {
    val isEdit = initial != null
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(textWithCursorAtEnd(initial?.name ?: ""))
    }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var editTime by rememberSaveable { mutableLongStateOf(initial?.start ?: 0L) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    val resolveTime = rememberTimeResolver()
    val suggestions = remember(pastNames, name.text) { medicineSuggestions(pastNames, name.text) }
    AppSheet(
        onDismiss = onDismiss,
        title = "Лекарство",
        icon = R.drawable.ic_medicine,
        confirm = SheetAction("Сохранить", enabled = name.text.isNotBlank()) {
            onSave(name.text.trim(), resolveTime(isEdit, minutesAgo, editTime), note.trim())
        },
    ) {
        MedicineNameField(name, { name = it }, suggestions)
        TimeSection(isEdit, minutesAgo, { minutesAgo = it }, editTime, { editTime = it })
        NoteField(note, { note = it }, label = "Комментарий (доза, причина…)")
    }
}

/**
 * Поле названия, под ним — [suggestions] чипами [HintChip]. Не выпадающим меню: их заменяют нижние
 * листы (ADR-0018), а лист поверх формы закрыл бы поле. Касание подсказки подставляет название
 * целиком, курсор встаёт в конец, клавиатура остаётся открытой. Название набрано целиком — его
 * подсказка пропадает, а с ней и ряд, если других нет.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun MedicineNameField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    suggestions: List<String>,
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
                    HintChip(label = suggestion, onClick = { onValueChange(textWithCursorAtEnd(suggestion)) })
                }
            }
        }
    }
}

/**
 * Текст поля без набираемого клавиатурой слова и с курсором в конце: иначе клавиатура заменит
 * подставленное название своим словом, а курсор останется посередине.
 */
private fun textWithCursorAtEnd(text: String) = TextFieldValue(text, selection = TextRange(text.length))
