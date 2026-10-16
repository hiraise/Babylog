package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.kid.babylog.R
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.formatWeight
import com.kid.babylog.core.ui.theme.BabyLogTheme

@Composable
internal fun WeightDialog(
    initial: Weight?,
    onDismiss: () -> Unit,
    onSave: (grams: Int, time: Long, note: String) -> Unit,
) {
    val isEdit = initial != null
    var weightText by rememberSaveable { mutableStateOf(initial?.grams?.toString() ?: "") }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var editTime by rememberSaveable { mutableLongStateOf(initial?.start ?: 0L) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    val resolveTime = rememberTimeResolver()
    val grams = weightText.toIntOrNull()?.takeIf { it in Weight.RANGE }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(R.drawable.ic_weight) },
        title = { Text(if (isEdit) "Вес — правка" else "Вес") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it.filter { c -> c.isDigit() }.take(5) },
                    label = { Text("Вес, г") },
                    supportingText = {
                        when {
                            grams != null && grams >= 1000 -> Text("= ${formatWeight(grams)}")
                            grams == null && weightText.isNotEmpty() ->
                                Text("От ${Weight.RANGE.first} до ${Weight.RANGE.last} г")
                            else -> Text("Например, 4350")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                TimeSection(isEdit, minutesAgo, { minutesAgo = it }, editTime, { editTime = it })
                NoteField(note, { note = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = grams != null,
                onClick = {
                    if (grams != null) onSave(grams, resolveTime(isEdit, minutesAgo, editTime), note.trim())
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
