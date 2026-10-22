package com.kid.babylog.feature.transfer

import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.EventRecord
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.isValidChildName
import com.kid.babylog.core.model.sameChildName
import com.kid.babylog.core.model.toEvent
import com.kid.babylog.core.model.toRecord

data class ImportResult(
    val added: Int = 0,
    val duplicates: Int = 0,
    val errors: Int = 0,
    val skippedActiveSleep: Int = 0,
    /** Незавершённый плач (ADR-0030) не импортируется: он стал бы «фантомным» таймером, как сон. */
    val skippedActiveCrying: Int = 0,
    /** Идущая болезнь из файла, когда у ребёнка уже идёт своя (ADR-0040): идущая у ребёнка одна. */
    val skippedActiveIllness: Int = 0,
    val childrenAdded: Int = 0,
    val birthDatesSet: Int = 0,
    val birthDateConflicts: Int = 0,
    val sexesSet: Int = 0,
    val sexConflicts: Int = 0,
    /** Фото записей из архива (ADR-0045). */
    val photosAdded: Int = 0,
    /** Из них — к записям, которые уже были в дневнике без фото. */
    val photosToExisting: Int = 0,
    val childPhotosSet: Int = 0,
    /** Фото, которые не загрузились: файла нет в архиве, он не JPEG или больше [EventPhoto.MAX_BYTES]; у CSV — все. */
    val photosSkipped: Int = 0,
    /** Импортировали архив экспорта, а не CSV. */
    val archive: Boolean = false,
)

/**
 * Импорт CSV — слияние по правилам ADR-0009, в одной транзакции; ничего не удаляет и не перезаписывает.
 * Строки без колонки `child` (файлы старых версий) относятся к ребёнку [defaultChildId]. Фото из колонки
 * `photos` без архива не загрузить — они считаются не загруженными.
 */
suspend fun importCsv(repository: DiaryRepository, text: String, defaultChildId: Long): ImportResult =
    repository.inTransaction { CsvImporter(repository, defaultChildId).apply { run(text) }.finish(archive = false) }

/** Куда встанет фото из архива: к записи на место [position] или ребёнку. */
private sealed interface PhotoTarget {
    /** [existing] — запись уже была в дневнике, импорт её не добавлял. */
    data class Event(val eventId: Long, val position: Int, val existing: Boolean) : PhotoTarget

    data class ChildAvatar(val childId: Long) : PhotoTarget
}

/**
 * Импорт строк CSV и фото к ним (ADR-0045): [run] разбирает строки и запоминает, какие файлы фото нужны,
 * [addPhoto] ставит прочитанное из архива фото, [finish] подводит итог.
 */
internal class CsvImporter(
    private val repository: DiaryRepository,
    private val defaultChildId: Long,
) {
    private val children = mutableListOf<Child>()

    /** Записи дневника и добавленные импортом → id записи: по ним ищутся дубликаты. */
    private val known = HashMap<Pair<Long, EventRecord>, Long>()

    /** Записи, у которых фото уже есть или ждут их из архива: второй раз фото к ним не добавляются. */
    private val withPhotos = HashSet<Long>()
    private val wanted = LinkedHashMap<String, MutableList<PhotoTarget>>()
    private var result = ImportResult()

    /** Файлы фото, которых ждут строки; пустеет по мере [addPhoto]. */
    val wantedPhotos: Set<String> get() = wanted.keys

    suspend fun run(text: String) {
        children += repository.allChildren()
        repository.allEvents().forEach { known.putIfAbsent(it.childId to it.toRecord(), it.id) }
        withPhotos += repository.photoIds().keys
        for (row in CsvCodec.parseRows(text)) {
            val type = row[0].trim()
            when {
                row.any { it.length > CsvCodec.MAX_FIELD_LENGTH } -> result = result.copy(errors = result.errors + 1)
                type.equals("type", ignoreCase = true) -> Unit // заголовок
                type.equals(CsvCodec.PROFILE_ROW, ignoreCase = true) -> importProfile(row)
                else -> importEvent(row)
            }
        }
    }

    /**
     * Фото [name] из архива; null — файл не JPEG или больше [EventPhoto.MAX_BYTES]. Фото записи встаёт на своё
     * место по колонке `photos`, фото ребёнка — только если у него своего нет.
     */
    suspend fun addPhoto(name: String, jpeg: ByteArray?) {
        val targets = wanted.remove(name) ?: return
        if (jpeg == null) {
            result = result.copy(photosSkipped = result.photosSkipped + targets.size)
            return
        }
        for (target in targets) when (target) {
            is PhotoTarget.Event -> {
                repository.addEventPhoto(target.eventId, target.position, jpeg)
                result = result.copy(
                    photosAdded = result.photosAdded + 1,
                    photosToExisting = result.photosToExisting + if (target.existing) 1 else 0,
                )
            }
            is PhotoTarget.ChildAvatar -> {
                val index = children.indexOfFirst { it.id == target.childId }
                val updated = children[index].copy(photo = ChildPhoto(jpeg))
                repository.updateChild(updated)
                children[index] = updated
                result = result.copy(childPhotosSet = result.childPhotosSet + 1)
            }
        }
    }

    /** Итог; фото, которых так и не нашлось в архиве (или файл — CSV без архива), — не загруженные. */
    fun finish(archive: Boolean): ImportResult =
        result.copy(photosSkipped = result.photosSkipped + wanted.values.sumOf { it.size }, archive = archive)

    /**
     * Строка PROFILE: создаёт ребёнка, если его нет; дату рождения и пол ставит, только если они
     * не заданы. Пустой пол — файл до ADR-0016 или пол не указан: его не трогаем.
     */
    private suspend fun importProfile(row: List<String>) {
        val name = CsvCodec.childName(row)
        val rawBirth = row.getOrElse(1) { "" }
        val birth = CsvCodec.parseTime(rawBirth)
        val badBirth = rawBirth.isNotBlank() && birth == null
        val rawSex = row.getOrElse(3) { "" }
        val sex = Sex.fromCode(rawSex)
        val badSex = rawSex.isNotBlank() && sex == null
        // В файлах старых версий PROFILE без имени и всегда с датой рождения
        if (badBirth || badSex || (name.isEmpty() && birth == null) || !isAcceptableName(name)) {
            result = result.copy(errors = result.errors + 1)
            return
        }
        val child = findChild(name) ?: run {
            wantChildPhoto(createChild(name, birth, sex), row)
            return
        }
        wantChildPhoto(child, row)
        // Отличающиеся дату и пол не применяем: старый бэкап не должен молча откатить исправление
        var updated = child
        when (child.birthDate) {
            null -> if (birth != null) {
                updated = updated.copy(birthDate = birth)
                result = result.copy(birthDatesSet = result.birthDatesSet + 1)
            }
            birth -> Unit
            else -> if (birth != null) result = result.copy(birthDateConflicts = result.birthDateConflicts + 1)
        }
        when (child.sex) {
            null -> if (sex != null) {
                updated = updated.copy(sex = sex)
                result = result.copy(sexesSet = result.sexesSet + 1)
            }
            sex -> Unit
            else -> if (sex != null) result = result.copy(sexConflicts = result.sexConflicts + 1)
        }
        if (updated != child) {
            repository.updateChild(updated)
            children[children.indexOf(child)] = updated
        }
    }

    private suspend fun importEvent(row: List<String>) {
        val name = CsvCodec.childName(row)
        val record = CsvCodec.rowToRecord(row)
        // Проверяем запись до того, как создавать ради неё ребёнка
        val probe = record?.toEvent(childId = 0)
        if (record == null || probe == null || !isAcceptableName(name)) {
            result = result.copy(errors = result.errors + 1)
            return
        }
        // Незавершённый сон не импортируем: он стал бы «фантомным» активным таймером
        if (probe is Sleep && probe.isActive) {
            result = result.copy(skippedActiveSleep = result.skippedActiveSleep + 1)
            return
        }
        if (probe is Crying && probe.isActive) {
            result = result.copy(skippedActiveCrying = result.skippedActiveCrying + 1)
            return
        }
        val child = findChild(name) ?: createChild(name, birthDate = null, sex = null)
        val parsed = record.toEvent(childId = child.id) ?: return
        // Идущая болезнь переносится (перенос на новый телефон посреди болезни), но вторая идущая — нет (ADR-0040)
        val ownActiveIllness = known.keys.any { (id, other) -> id == child.id && other.type == EventRecord.ILLNESS && other.end == null }
        if (parsed is Illness && parsed.isActive && ownActiveIllness && (child.id to parsed.toRecord()) !in known) {
            result = result.copy(skippedActiveIllness = result.skippedActiveIllness + 1)
            return
        }
        val event = parsed.withLegacyFeedingEnd()
        // Идущее кормление из собственного экспорта уже есть в базе — без конца, как в файле
        val existing = known[child.id to parsed.toRecord()] ?: known[child.id to event.toRecord()]
        if (existing != null) {
            result = result.copy(duplicates = result.duplicates + 1)
            wantEventPhotos(existing, row, existing = true)
            return
        }
        val id = repository.addEvent(event)
        known[child.id to event.toRecord()] = id
        result = result.copy(added = result.added + 1)
        wantEventPhotos(id, row, existing = false)
    }

    /**
     * Фото строки ждут из архива, если у записи их ещё нет: так архив возвращает фото записям, перенесённым
     * раньше через CSV, а фото записи, у которой они есть, не смешивает с чужими (ADR-0045).
     */
    private fun wantEventPhotos(eventId: Long, row: List<String>, existing: Boolean) {
        val names = CsvCodec.photoNames(row).take(EventPhoto.MAX_PER_EVENT)
        if (names.isEmpty() || !withPhotos.add(eventId)) return
        names.forEachIndexed { position, file -> want(file, PhotoTarget.Event(eventId, position, existing)) }
    }

    /** Фото ребёнка из файла ставится, только если своего у него нет: как дата рождения и пол. */
    private fun wantChildPhoto(child: Child, row: List<String>) {
        val file = CsvCodec.photoNames(row).firstOrNull() ?: return
        val target = PhotoTarget.ChildAvatar(child.id)
        if (child.photo != null || wanted.values.any { target in it }) return
        want(file, target)
    }

    private fun want(file: String, target: PhotoTarget) {
        wanted.getOrPut(file) { mutableListOf() } += target
    }

    /**
     * `FEEDING` без `end` — кормление из файла до ADR-0015 (или шедшее во время экспорта):
     * импортируется без длительности, а не «фантомным» таймером.
     */
    private fun BabyEvent.withLegacyFeedingEnd(): BabyEvent = if (this is Feeding && isActive) copy(end = start) else this

    private fun isAcceptableName(name: String) = name.isEmpty() || isValidChildName(name)

    /** Пустое имя — ребёнок по умолчанию (выбранный на экране). */
    private fun findChild(name: String): Child? =
        if (name.isEmpty()) children.find { it.id == defaultChildId } ?: children.firstOrNull()
        else children.find { sameChildName(it.name, name) }

    private suspend fun createChild(name: String, birthDate: Long?, sex: Sex?): Child {
        val child = Child(repository.addChild(name, birthDate, sex, photo = null), name.trim(), birthDate, sex)
        children += child
        result = result.copy(childrenAdded = result.childrenAdded + 1)
        return child
    }
}

/** Итог импорта для пользователя. */
fun ImportResult.describe(): String = buildString {
    append("Импорт: добавлено $added")
    if (duplicates > 0) append(", дубликатов $duplicates")
    if (errors > 0) append(", ошибок $errors")
    if (skippedActiveSleep > 0) append(", пропущено незавершённых снов $skippedActiveSleep")
    if (skippedActiveCrying > 0) append(", пропущено незавершённого плача $skippedActiveCrying")
    if (skippedActiveIllness > 0) append(", пропущено идущих болезней $skippedActiveIllness — у ребёнка уже идёт болезнь")
    if (childrenAdded > 0) append(", новых детей $childrenAdded")
    if (birthDatesSet > 0) append(", дат рождения импортировано $birthDatesSet")
    if (birthDateConflicts > 0) append(", дата рождения в файле отличается — оставлена текущая")
    if (sexesSet > 0) append(", пол указан у детей: $sexesSet")
    if (sexConflicts > 0) append(", пол в файле отличается — оставлен текущий")
    if (photosAdded > 0) {
        append(", фото $photosAdded")
        if (photosToExisting > 0) append(" (к прежним записям — $photosToExisting)")
    }
    if (childPhotosSet > 0) append(", фото детей $childPhotosSet")
    if (photosSkipped > 0) {
        if (archive) append(", не загружено фото $photosSkipped")
        else append(", фото не загружены ($photosSkipped) — они в архиве .zip, импортируйте его")
    }
}
