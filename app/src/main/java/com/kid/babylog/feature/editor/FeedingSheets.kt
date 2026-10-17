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
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.DateTimeField
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.SheetAction

/**
 * Начало кормления: грудь и «когда» — запускает таймер (ADR-0015); или бутылочка — объём и что
 * внутри, сохраняется сразу, без таймера (ADR-0025). Объём и что внутри — как у прошлой бутылочки
 * [lastBottle]: обычно они день ото дня одни и те же. Идущий [activeSleep] закончится (ADR-0020).
 */
@Composable
fun FeedingStartSheet(
    activeSleep: Sleep?,
    lastBottle: Bottle?,
    onDismiss: () -> Unit,
    onSave: (breast: Breast, time: Long) -> Unit,
    onSaveBottle: (volumeMl: Int, milk: Milk?, time: Long) -> Unit,
) {
    var choice by rememberSaveable { mutableStateOf<FeedingChoice?>(null) }
    var volume by rememberSaveable { mutableStateOf(lastBottle?.volumeMl?.toString() ?: "") }
    var milk by rememberSaveable { mutableStateOf(lastBottle?.milk) }
    var minutesAgo by rememberSaveable { mutableIntStateOf(0) }
    val resolveTime = rememberTimeResolver()
    val volumeMl = bottleVolume(volume)
    val confirm = when (val selected = choice) {
        FeedingChoice.BOTTLE -> SheetAction("Сохранить", enabled = volumeMl != null) {
            if (volumeMl != null) onSaveBottle(volumeMl, milk, resolveTime(false, minutesAgo, 0L))
        }
        else -> SheetAction("Запустить таймер", enabled = selected != null) {
            selected?.breast?.let { onSave(it, resolveTime(false, minutesAgo, 0L)) }
        }
    }
    AppSheet(onDismiss = onDismiss, title = "Кормление", icon = R.drawable.ic_feeding, confirm = confirm) {
        OptionChips(
            options = FeedingChoice.entries.map { it to it.label },
            selected = choice,
            onSelect = { choice = it },
        )
        if (choice == FeedingChoice.BOTTLE) BottleFields(volume, { volume = it }, milk, { milk = it })
        TimeAgoPicker(minutesAgo, { minutesAgo = it }, activeSleep?.let { FinishingTimer("Сон", it.start) })
    }
}

/** Чем кормят: грудью — таймер, бутылочкой — разовая запись. */
private enum class FeedingChoice(val label: String, val breast: Breast?) {
    LEFT("Левая грудь", Breast.LEFT),
    RIGHT("Правая грудь", Breast.RIGHT),
    BOTTLE("Бутылочка", null),
}

/** Правка кормления: грудь, начало и (у законченного) конец. */
@Composable
internal fun FeedingEditSheet(
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
    AppSheet(
        onDismiss = onDismiss,
        title = "Кормление",
        icon = R.drawable.ic_feeding,
        confirm = SheetAction("Сохранить", enabled = valid) {
            onSave(breast, start, if (hasEnd) end else null, note.trim())
        },
    ) {
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
}

@Composable
private fun BreastChips(selected: Breast?, onSelect: (Breast) -> Unit) {
    OptionChips(
        options = listOf(Breast.LEFT to "Левая грудь", Breast.RIGHT to "Правая грудь"),
        selected = selected,
        onSelect = onSelect,
    )
}
