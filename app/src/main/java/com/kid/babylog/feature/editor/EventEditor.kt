package com.kid.babylog.feature.editor

import androidx.compose.runtime.Composable
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight

/**
 * Кнопки добавления: плитки «Сегодня» и «Заметка» на вкладке «Заметки» (ADR-0031). Сон, кормление
 * и плач начинаются отдельно — через [SleepStartSheet], [FeedingStartSheet] и плитку плача.
 */
enum class NewEventKind { DIAPER, MEDICINE, WEIGHT, HEIGHT, NOTE }

/**
 * Лист новой записи для ребёнка [child]. [pastMedicines] — подсказки в листе лекарства, [noteTags] —
 * метки в листе заметки ([noteTagChoices]). [onSave] получает запись и её фото (ADR-0024); null —
 * у этого типа записей фото нет.
 */
@Composable
fun NewEventSheet(
    kind: NewEventKind,
    child: Child,
    pastMedicines: List<PastMedicine>,
    noteTags: TagChoices,
    onDismiss: () -> Unit,
    onSave: (BabyEvent, PhotoEdit?) -> Unit,
) {
    val childId = child.id
    when (kind) {
        NewEventKind.DIAPER -> DiaperSheet(null, child, emptyList(), onDismiss) { diaperKind, time, note, photos ->
            onSave(Diaper(childId = childId, start = time, kind = diaperKind, note = note), photos)
        }
        NewEventKind.MEDICINE -> MedicineSheet(null, pastMedicines, onDismiss) { name, time, note ->
            onSave(Medicine(childId = childId, start = time, name = name, note = note), null)
        }
        NewEventKind.WEIGHT -> WeightSheet(null, onDismiss) { grams, time, note ->
            onSave(Weight(childId = childId, start = time, grams = grams, note = note), null)
        }
        NewEventKind.HEIGHT -> HeightSheet(null, onDismiss) { millimeters, time, note ->
            onSave(Height(childId = childId, start = time, millimeters = millimeters, note = note), null)
        }
        NewEventKind.NOTE -> NoteSheet(null, noteTags, emptyList(), onDismiss) { text, tags, time, photos ->
            onSave(Note(childId = childId, start = time, tags = tags, note = text), photos)
        }
    }
}

/**
 * Лист правки существующей записи ребёнка [child]; id и ребёнок сохраняются. [photos] — фото записи
 * из базы; [onSave] получает их после формы, null — у этого типа записей фото не меняются.
 * [pastMedicines] — подсказки в листе лекарства, [cryingChoices] — метки в листе плача, [noteTags] — в листе заметки.
 */
@Composable
fun EditEventSheet(
    event: BabyEvent,
    child: Child,
    photos: List<EventPhoto>,
    pastMedicines: List<PastMedicine>,
    cryingChoices: CryingChoices,
    noteTags: TagChoices,
    onDismiss: () -> Unit,
    onSave: (BabyEvent, PhotoEdit?) -> Unit,
) {
    val sex = child.sex
    val save = { edited: BabyEvent -> onSave(edited, null) }
    when (event) {
        is Feeding -> FeedingEditSheet(event, onDismiss) { breast, start, end, note ->
            save(event.copy(breast = breast, start = start, end = end, note = note))
        }
        is Bottle -> BottleSheet(event, onDismiss) { volumeMl, milk, time, note ->
            save(event.copy(volumeMl = volumeMl, milk = milk, start = time, note = note))
        }
        is Diaper -> DiaperSheet(event, child, photos, onDismiss) { kind, time, note, edit ->
            onSave(event.copy(kind = kind, start = time, note = note), edit)
        }
        is Medicine -> MedicineSheet(event, pastMedicines, onDismiss) { name, time, note ->
            save(event.copy(name = name, start = time, note = note))
        }
        is Weight -> WeightSheet(event, onDismiss) { grams, time, note ->
            save(event.copy(grams = grams, start = time, note = note))
        }
        is Height -> HeightSheet(event, onDismiss) { millimeters, time, note ->
            save(event.copy(millimeters = millimeters, start = time, note = note))
        }
        is Sleep -> SleepEditSheet(event, sex, onDismiss) { start, end, note ->
            save(event.copy(start = start, end = end, note = note))
        }
        is Crying -> CryingEditSheet(event, cryingChoices, onDismiss) { start, end, reasons, helped, note ->
            save(event.copy(start = start, end = end, reasons = reasons, helped = helped, note = note))
        }
        is Note -> NoteSheet(event, noteTags, photos, onDismiss) { text, tags, time, edit ->
            onSave(event.copy(note = text, tags = tags, start = time), edit)
        }
    }
}
