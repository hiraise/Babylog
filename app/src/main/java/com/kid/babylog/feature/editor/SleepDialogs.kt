package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.DateTimeField
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Начало сна: запускает таймер. */
@Composable
fun SleepStartDialog(
    childName: String,
    onDismiss: () -> Unit,
    onSave: (time: Long) -> Unit,
) {
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    val resolveTime = rememberTimeResolver()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(R.drawable.ic_sleep) },
        title = { Text("Сон · $childName") },
        text = { TimeAgoPicker(minutesAgo) { minutesAgo = it } },
        confirmButton = {
            TextButton(onClick = { onSave(resolveTime(false, minutesAgo, 0L)) }) {
                Text("Запустить таймер")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Правка записи сна: начало и (для завершённого) конец. */
@Composable
internal fun SleepEditDialog(
    initial: Sleep,
    onDismiss: () -> Unit,
    onSave: (start: Long, end: Long?, note: String) -> Unit,
) {
    var start by rememberSaveable { mutableLongStateOf(initial.start) }
    // -1 = активный сон без конца (mutableLongStateOf не хранит null)
    var end by rememberSaveable { mutableLongStateOf(initial.end ?: -1L) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    val hasEnd = end >= 0
    val valid = !hasEnd || end >= start
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(R.drawable.ic_sleep) },
        title = { Text("Сон — правка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
                DateTimeField("Уснул:", start) { start = it }
                if (hasEnd) {
                    DateTimeField("Проснулся:", end) { end = it }
                    if (!valid) {
                        Text(
                            "Конец сна не может быть раньше начала",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                NoteField(note, { note = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onSave(start, if (hasEnd) end else null, note.trim()) },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
