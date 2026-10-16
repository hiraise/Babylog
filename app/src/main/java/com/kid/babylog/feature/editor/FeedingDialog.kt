package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kid.babylog.R
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.ui.theme.BabyLogTheme

@Composable
internal fun FeedingDialog(
    initial: Feeding?,
    onDismiss: () -> Unit,
    onSave: (breast: Breast, time: Long, note: String) -> Unit,
) {
    val isEdit = initial != null
    var breast by rememberSaveable { mutableStateOf(initial?.breast) }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    var editTime by rememberSaveable { mutableLongStateOf(initial?.start ?: 0L) }
    var note by rememberSaveable { mutableStateOf(initial?.note ?: "") }
    val resolveTime = rememberTimeResolver()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(R.drawable.ic_feeding) },
        title = { Text(if (isEdit) "Кормление — правка" else "Кормление") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
                OptionChips(
                    options = listOf(Breast.LEFT to "Левая грудь", Breast.RIGHT to "Правая грудь"),
                    selected = breast,
                    onSelect = { breast = it },
                )
                TimeSection(isEdit, minutesAgo, { minutesAgo = it }, editTime, { editTime = it })
                NoteField(note, { note = it })
            }
        },
        confirmButton = {
            val selected = breast
            TextButton(
                enabled = selected != null,
                onClick = {
                    if (selected != null) onSave(selected, resolveTime(isEdit, minutesAgo, editTime), note.trim())
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
