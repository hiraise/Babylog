package com.kid.babylog.feature.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.kid.babylog.R
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.DateTimeField
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction

/** Начало сна: запускает таймер. */
@Composable
fun SleepStartSheet(
    childName: String,
    onDismiss: () -> Unit,
    onSave: (time: Long) -> Unit,
) {
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    val resolveTime = rememberTimeResolver()
    AppSheet(
        onDismiss = onDismiss,
        title = "Сон · $childName",
        icon = R.drawable.ic_sleep,
        confirm = SheetAction("Запустить таймер") { onSave(resolveTime(false, minutesAgo, 0L)) },
    ) {
        TimeAgoPicker(minutesAgo) { minutesAgo = it }
    }
}

/** Правка записи сна: начало и (для завершённого) конец. */
@Composable
internal fun SleepEditSheet(
    initial: Sleep,
    sex: Sex?,
    onDismiss: () -> Unit,
    onSave: (start: Long, end: Long?, note: String) -> Unit,
) {
    var start by rememberSaveable { mutableLongStateOf(initial.start) }
    // -1 = активный сон без конца (mutableLongStateOf не хранит null)
    var end by rememberSaveable { mutableLongStateOf(initial.end ?: -1L) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    val hasEnd = end >= 0
    val valid = !hasEnd || end >= start
    AppSheet(
        onDismiss = onDismiss,
        title = "Сон — правка",
        icon = R.drawable.ic_sleep,
        confirm = SheetAction("Сохранить", enabled = valid) { onSave(start, if (hasEnd) end else null, note.trim()) },
    ) {
        DateTimeField(bySex(sex, "Уснул:", "Уснула:"), start) { start = it }
        if (hasEnd) {
            DateTimeField(bySex(sex, "Проснулся:", "Проснулась:"), end) { end = it }
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
}
