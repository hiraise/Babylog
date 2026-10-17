package com.kid.babylog.feature.editor

import androidx.compose.runtime.Composable
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight

/** Кнопки быстрого добавления. Сон и кормление начинаются отдельно — через [SleepStartSheet] и [FeedingStartSheet]. */
enum class NewEventKind { DIAPER, MEDICINE, WEIGHT }

/**
 * Лист новой записи для ребёнка [child]. [onSave] получает запись и её фото (ADR-0024); null —
 * у этого типа записей фото нет.
 */
@Composable
fun NewEventSheet(
    kind: NewEventKind,
    child: Child,
    onDismiss: () -> Unit,
    onSave: (BabyEvent, PhotoEdit?) -> Unit,
) {
    val childId = child.id
    when (kind) {
        NewEventKind.DIAPER -> DiaperSheet(null, child, emptyList(), onDismiss) { diaperKind, time, note, photos ->
            onSave(Diaper(childId = childId, start = time, kind = diaperKind, note = note), photos)
        }
        NewEventKind.MEDICINE -> MedicineSheet(null, onDismiss) { name, time, note ->
            onSave(Medicine(childId = childId, start = time, name = name, note = note), null)
        }
        NewEventKind.WEIGHT -> WeightSheet(null, onDismiss) { grams, time, note ->
            onSave(Weight(childId = childId, start = time, grams = grams, note = note), null)
        }
    }
}

/**
 * Лист правки существующей записи ребёнка [child]; id и ребёнок сохраняются. [photos] — фото записи
 * из базы; [onSave] получает их после формы, null — у этого типа записей фото не меняются.
 */
@Composable
fun EditEventSheet(
    event: BabyEvent,
    child: Child,
    photos: List<EventPhoto>,
    onDismiss: () -> Unit,
    onSave: (BabyEvent, PhotoEdit?) -> Unit,
) {
    val sex = child.sex
    val save = { edited: BabyEvent -> onSave(edited, null) }
    when (event) {
        is Feeding -> FeedingEditSheet(event, onDismiss) { breast, start, end, note ->
            save(event.copy(breast = breast, start = start, end = end, note = note))
        }
        is Diaper -> DiaperSheet(event, child, photos, onDismiss) { kind, time, note, edit ->
            onSave(event.copy(kind = kind, start = time, note = note), edit)
        }
        is Medicine -> MedicineSheet(event, onDismiss) { name, time, note ->
            save(event.copy(name = name, start = time, note = note))
        }
        is Weight -> WeightSheet(event, onDismiss) { grams, time, note ->
            save(event.copy(grams = grams, start = time, note = note))
        }
        is Sleep -> SleepEditSheet(event, sex, onDismiss) { start, end, note ->
            save(event.copy(start = start, end = end, note = note))
        }
    }
}
