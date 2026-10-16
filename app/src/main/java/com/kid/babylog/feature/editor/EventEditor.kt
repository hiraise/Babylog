package com.kid.babylog.feature.editor

import androidx.compose.runtime.Composable
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight

/** Кнопки быстрого добавления. Сон начинается отдельно — через [SleepStartDialog]. */
enum class NewEventKind { FEEDING, DIAPER, MEDICINE, WEIGHT }

/** Диалог новой записи для ребёнка [childId]. */
@Composable
fun NewEventDialog(
    kind: NewEventKind,
    childId: Long,
    onDismiss: () -> Unit,
    onSave: (BabyEvent) -> Unit,
) {
    when (kind) {
        NewEventKind.FEEDING -> FeedingDialog(null, onDismiss) { breast, time, note ->
            onSave(Feeding(childId = childId, start = time, breast = breast, note = note))
        }
        NewEventKind.DIAPER -> DiaperDialog(null, onDismiss) { diaperKind, time, note ->
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

/** Диалог правки существующей записи; id и ребёнок сохраняются. */
@Composable
fun EditEventDialog(
    event: BabyEvent,
    onDismiss: () -> Unit,
    onSave: (BabyEvent) -> Unit,
) {
    when (event) {
        is Feeding -> FeedingDialog(event, onDismiss) { breast, time, note ->
            onSave(event.copy(breast = breast, start = time, note = note))
        }
        is Diaper -> DiaperDialog(event, onDismiss) { kind, time, note ->
            onSave(event.copy(kind = kind, start = time, note = note))
        }
        is Medicine -> MedicineDialog(event, onDismiss) { name, time, note ->
            onSave(event.copy(name = name, start = time, note = note))
        }
        is Weight -> WeightDialog(event, onDismiss) { grams, time, note ->
            onSave(event.copy(grams = grams, start = time, note = note))
        }
        is Sleep -> SleepEditDialog(event, onDismiss) { start, end, note ->
            onSave(event.copy(start = start, end = end, note = note))
        }
    }
}
