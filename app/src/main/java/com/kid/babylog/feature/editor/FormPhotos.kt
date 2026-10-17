package com.kid.babylog.feature.editor

import androidx.compose.runtime.saveable.listSaver
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.ui.diaperLabel
import com.kid.babylog.core.ui.formatAge
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatDate
import com.kid.babylog.core.ui.toLocalDate
import java.time.LocalDate

/**
 * Фото в форме записи (ADR-0024): уже в базе ([Saved], по id) или только что добавленное — файл
 * в кэше ([Pending]), в базу оно попадёт по «Сохранить».
 */
internal sealed interface FormPhoto {
    data class Saved(val id: Long) : FormPhoto

    data class Pending(val path: String) : FormPhoto
}

/**
 * Фото формы в сохранённом состоянии — только id и пути. Сами фото в Bundle не кладём: больше 1 МБ
 * в нём роняют приложение, когда оно уходит в фон, — например, пока открыта камера.
 */
internal val FormPhotosSaver = listSaver<List<FormPhoto>, String>(
    save = { photos ->
        photos.map {
            when (it) {
                is FormPhoto.Saved -> "$SAVED${it.id}"
                is FormPhoto.Pending -> "$PENDING${it.path}"
            }
        }
    },
    restore = { saved ->
        saved.map { if (it.startsWith(SAVED)) FormPhoto.Saved(it.removePrefix(SAVED).toLong()) else FormPhoto.Pending(it.removePrefix(PENDING)) }
    },
)

private const val SAVED = "id:"
private const val PENDING = "file:"

/**
 * Фото формы после «Сохранить»: сохранённые остаются в том же порядке, новые встают после них —
 * новое всегда добавляется в конец, так что порядок на экране не меняется. [read] — байты нового фото;
 * null (файл пропал) — фото пропускается.
 */
internal fun List<FormPhoto>.toEdit(read: (path: String) -> ByteArray?): PhotoEdit = PhotoEdit(
    keep = filterIsInstance<FormPhoto.Saved>().map { it.id },
    add = filterIsInstance<FormPhoto.Pending>().mapNotNull { read(it.path) },
)

/**
 * Подпись к фото подгузника для мессенджера — кто, что и когда, чтобы врачу не нужны были пояснения:
 * «Маша, 4 мес 12 дн.», «Подгузник: покакала», «17 октября 2026, 14:30», комментарий.
 */
internal fun diaperShareText(child: Child, kind: DiaperKind, time: Long, note: String, today: LocalDate): String =
    buildList {
        add(listOfNotNull(child.name.trim(), child.birthDate?.let { formatAge(it.toLocalDate(), today, child.sex) }).joinToString(", "))
        add("Подгузник: ${diaperLabel(kind, child.sex)}")
        add("${formatDate(time)}, ${formatClock(time)}")
        note.trim().takeIf { it.isNotEmpty() }?.let { add("Комментарий: $it") }
    }.joinToString("\n")
