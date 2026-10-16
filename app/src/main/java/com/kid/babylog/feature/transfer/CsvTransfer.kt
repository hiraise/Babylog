package com.kid.babylog.feature.transfer

import com.kid.babylog.core.data.DiaryRepository
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.EventRecord
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.isValidChildName
import com.kid.babylog.core.model.sameChildName
import com.kid.babylog.core.model.toEvent
import com.kid.babylog.core.model.toRecord

data class CsvExport(val text: String, val eventCount: Int)

data class ImportResult(
    val added: Int = 0,
    val duplicates: Int = 0,
    val errors: Int = 0,
    val skippedActiveSleep: Int = 0,
    val childrenAdded: Int = 0,
    val birthDatesSet: Int = 0,
    val birthDateConflicts: Int = 0,
    val sexesSet: Int = 0,
    val sexConflicts: Int = 0,
)

/** Весь дневник в CSV: все дети и все их записи по возрастанию времени. */
suspend fun exportCsv(repository: DiaryRepository): CsvExport {
    val (children, events) = repository.inTransaction { repository.allChildren() to repository.allEvents() }
    return CsvExport(CsvCodec.encode(children, events), events.size)
}

/**
 * Импорт — слияние по правилам ADR-0009, в одной транзакции; ничего не удаляет и не перезаписывает.
 * Строки без колонки `child` (файлы старых версий) относятся к ребёнку [defaultChildId].
 */
suspend fun importCsv(repository: DiaryRepository, text: String, defaultChildId: Long): ImportResult =
    repository.inTransaction { CsvImporter(repository, defaultChildId).run(text) }

private class CsvImporter(
    private val repository: DiaryRepository,
    private val defaultChildId: Long,
) {
    private val children = mutableListOf<Child>()
    private val known = HashSet<Pair<Long, EventRecord>>()
    private var result = ImportResult()

    suspend fun run(text: String): ImportResult {
        children += repository.allChildren()
        repository.allEvents().forEach { known += it.childId to it.toRecord() }
        for (row in CsvCodec.parseRows(text)) {
            val type = row[0].trim()
            when {
                type.equals("type", ignoreCase = true) -> Unit // заголовок
                type.equals(CsvCodec.PROFILE_ROW, ignoreCase = true) -> importProfile(row)
                else -> importEvent(row)
            }
        }
        return result
    }

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
            createChild(name, birth, sex)
            return
        }
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
        val child = findChild(name) ?: createChild(name, birthDate = null, sex = null)
        val parsed = record.toEvent(childId = child.id) ?: return
        val event = parsed.withLegacyFeedingEnd()
        // Идущее кормление из собственного экспорта уже есть в базе — без конца, как в файле
        if ((child.id to parsed.toRecord()) in known || !known.add(child.id to event.toRecord())) {
            result = result.copy(duplicates = result.duplicates + 1)
            return
        }
        repository.addEvent(event)
        result = result.copy(added = result.added + 1)
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
        val child = Child(repository.addChild(name, birthDate, sex), name.trim(), birthDate, sex)
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
    if (childrenAdded > 0) append(", новых детей $childrenAdded")
    if (birthDatesSet > 0) append(", дат рождения импортировано $birthDatesSet")
    if (birthDateConflicts > 0) append(", дата рождения в файле отличается — оставлена текущая")
    if (sexesSet > 0) append(", пол указан у детей: $sexesSet")
    if (sexConflicts > 0) append(", пол в файле отличается — оставлен текущий")
}
