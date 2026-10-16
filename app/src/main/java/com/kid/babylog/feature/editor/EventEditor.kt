package com.kid.babylog.feature.editor

import androidx.compose.runtime.Composable
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight

/** Кнопки быстрого добавления. Сон и кормление начинаются отдельно — через [SleepStartDialog] и [FeedingStartDialog]. */
enum class NewEventKind { DIAPER, MEDICINE, WEIGHT }

/** Диалог новой записи для ребёнка [child]. */
@Composable
fun NewEventDialog(
    kind: NewEventKind,
    child: Child,
    onDismiss: () -> Unit,
    onSave: (BabyEvent) -> Unit,
) {
    val childId = child.id
    when (kind) {
        NewEventKind.DIAPER -> DiaperDialog(null, child.sex, onDismiss) { diaperKind, time, note ->
            onSave(Diaper(childId = childId, start = time, kind = diaperKind, note = note))
        }
        NewEventKind.MEDICINE -> MedicineDialog(null, onDismiss) { name, time, note ->
            onSave(Medicine(childId = childId, start = time, name = name, note = note))
        }
        NewEventKind.WEIGHT -> WeightDialog(null, onDismiss) { grams, time, note ->
            onSave(Weight(childId = childId, start = time, grams = grams, note = note))
        }
    }
}

/** Диалог правки существующей записи; id и ребёнок сохраняются. [sex] — пол ребёнка, чья запись. */
@Composable
fun EditEventDialog(
    event: BabyEvent,
    sex: Sex?,
    onDismiss: () -> Unit,
    onSave: (BabyEvent) -> Unit,
) {
    when (event) {
        is Feeding -> FeedingEditDialog(event, onDismiss) { breast, start, end, note ->
            onSave(event.copy(breast = breast, start = start, end = end, note = note))
        }
        is Diaper -> DiaperDialog(event, sex, onDismiss) { kind, time, note ->
            onSave(event.copy(kind = kind, start = time, note = note))
        }
        is Medicine -> MedicineDialog(event, onDismiss) { name, time, note ->
            onSave(event.copy(name = name, start = time, note = note))
        }
        is Weight -> WeightDialog(event, onDismiss) { grams, time, note ->
            onSave(event.copy(grams = grams, start = time, note = note))
        }
        is Sleep -> SleepEditDialog(event, sex, onDismiss) { start, end, note ->
            onSave(event.copy(start = start, end = end, note = note))
        }
    }
}
