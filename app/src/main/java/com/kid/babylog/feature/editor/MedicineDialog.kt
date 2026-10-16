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
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.kid.babylog.R
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.ui.theme.BabyLogTheme

@Composable
internal fun MedicineDialog(
    initial: Medicine?,
    onDismiss: () -> Unit,
    onSave: (name: String, time: Long, note: String) -> Unit,
) {
    val isEdit = initial != null
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var editTime by rememberSaveable { mutableLongStateOf(initial?.start ?: 0L) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    val resolveTime = rememberTimeResolver()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(R.drawable.ic_medicine) },
        title = { Text(if (isEdit) "Лекарство — правка" else "Лекарство") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                TimeSection(isEdit, minutesAgo, { minutesAgo = it }, editTime, { editTime = it })
                NoteField(note, { note = it }, label = "Комментарий (доза, причина…)")
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = { onSave(name.trim(), resolveTime(isEdit, minutesAgo, editTime), note.trim()) },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
