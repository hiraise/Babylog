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
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.ui.DateTimeField
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Начало кормления (ADR-0015): грудь и «когда»; запускает таймер. */
@Composable
fun FeedingStartDialog(
    childName: String,
    onDismiss: () -> Unit,
    onSave: (breast: Breast, time: Long) -> Unit,
) {
    var breast by rememberSaveable { mutableStateOf<Breast?>(null) }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    val resolveTime = rememberTimeResolver()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(R.drawable.ic_feeding) },
        title = { Text("Кормление · $childName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
                BreastChips(breast) { breast = it }
                TimeAgoPicker(minutesAgo) { minutesAgo = it }
            }
        },
        confirmButton = {
            val selected = breast
            TextButton(
                enabled = selected != null,
                onClick = { if (selected != null) onSave(selected, resolveTime(false, minutesAgo, 0L)) },
            ) { Text("Запустить таймер") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Правка кормления: грудь, начало и (у законченного) конец. */
@Composable
internal fun FeedingEditDialog(
    initial: Feeding,
    onDismiss: () -> Unit,
    onSave: (breast: Breast, start: Long, end: Long?, note: String) -> Unit,
) {
    var breast by rememberSaveable { mutableStateOf(initial.breast) }
    var start by rememberSaveable { mutableLongStateOf(initial.start) }
    // -1 = идущее кормление без конца (mutableLongStateOf не хранит null)
    var end by rememberSaveable { mutableLongStateOf(initial.end ?: -1L) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    val hasEnd = end >= 0
    val valid = !hasEnd || end >= start
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { DialogIcon(R.drawable.ic_feeding) },
        title = { Text("Кормление — правка") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
                BreastChips(breast) { breast = it }
                DateTimeField("Начало:", start) { newStart ->
                    // У кормления без длительности конец едет вместе с началом, иначе правка времени
                    // старой записи упёрлась бы в «конец раньше начала»
                    if (end == start) end = newStart
                    start = newStart
                }
                if (hasEnd) {
                    DateTimeField("Конец:", end) { end = it }
                    if (!valid) {
                        Text(
                            "Конец кормления не может быть раньше начала",
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
                onClick = { onSave(breast, start, if (hasEnd) end else null, note.trim()) },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun BreastChips(selected: Breast?, onSelect: (Breast) -> Unit) {
    OptionChips(
        options = listOf(Breast.LEFT to "Левая грудь", Breast.RIGHT to "Правая грудь"),
        selected = selected,
        onSelect = onSelect,
    )
}
