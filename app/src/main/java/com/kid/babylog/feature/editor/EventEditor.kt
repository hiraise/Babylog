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

/** Кнопки быстрого добавления. Сон и кормление начинаются отдельно — через [SleepStartSheet] и [FeedingStartSheet]. */
enum class NewEventKind { DIAPER, MEDICINE, WEIGHT }

/** Лист новой записи для ребёнка [child]. [pastMedicines] — подсказки в листе лекарства. */
@Composable
fun NewEventSheet(
    kind: NewEventKind,
    child: Child,
    pastMedicines: List<PastMedicine>,
    onDismiss: () -> Unit,
    onSave: (BabyEvent) -> Unit,
) {
    val childId = child.id
    when (kind) {
        NewEventKind.DIAPER -> DiaperSheet(null, child.sex, onDismiss) { diaperKind, time, note ->
            onSave(Diaper(childId = childId, start = time, kind = diaperKind, note = note))
        }
        NewEventKind.MEDICINE -> MedicineSheet(null, pastMedicines, onDismiss) { name, time, note ->
            onSave(Medicine(childId = childId, start = time, name = name, note = note))
        }
        NewEventKind.WEIGHT -> WeightSheet(null, onDismiss) { grams, time, note ->
            onSave(Weight(childId = childId, start = time, grams = grams, note = note))
        }
    }
}

/**
 * Лист правки существующей записи; id и ребёнок сохраняются. [sex] — пол ребёнка, чья запись,
 * [pastMedicines] — подсказки в листе лекарства.
 */
@Composable
fun EditEventSheet(
    event: BabyEvent,
    sex: Sex?,
    pastMedicines: List<PastMedicine>,
    onDismiss: () -> Unit,
    onSave: (BabyEvent) -> Unit,
) {
    when (event) {
        is Feeding -> FeedingEditSheet(event, onDismiss) { breast, start, end, note ->
            onSave(event.copy(breast = breast, start = start, end = end, note = note))
        }
        is Diaper -> DiaperSheet(event, sex, onDismiss) { kind, time, note ->
            onSave(event.copy(kind = kind, start = time, note = note))
        }
        is Medicine -> MedicineSheet(event, pastMedicines, onDismiss) { name, time, note ->
            onSave(event.copy(name = name, start = time, note = note))
        }
        is Weight -> WeightSheet(event, onDismiss) { grams, time, note ->
            onSave(event.copy(grams = grams, start = time, note = note))
        }
        is Sleep -> SleepEditSheet(event, sex, onDismiss) { start, end, note ->
            onSave(event.copy(start = start, end = end, note = note))
        }
    }
}
