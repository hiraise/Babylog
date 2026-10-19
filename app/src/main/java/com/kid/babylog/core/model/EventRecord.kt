package com.kid.babylog.core.model

/**
 * Плоское представление записи — так она хранится в таблице `events` и в CSV.
 * Строковые коды типов и деталей существуют только здесь; остальной код работает с [BabyEvent].
 */
data class EventRecord(
    val type: String,
    val start: Long,
    val end: Long?,
    val detail: String,
    val note: String,
) {
    companion object {
        const val FEEDING = "FEEDING"
        const val BOTTLE = "BOTTLE"
        const val DIAPER = "DIAPER"
        const val SLEEP = "SLEEP"
        const val MEDICINE = "MEDICINE"
        const val WEIGHT = "WEIGHT"
        const val HEIGHT = "HEIGHT"
        const val CRYING = "CRYING"
        const val NOTE = "NOTE"
    }
}

fun BabyEvent.toRecord(): EventRecord = when (this) {
    is Feeding -> EventRecord(EventRecord.FEEDING, start, end, breast.name, note)
    is Bottle -> EventRecord(EventRecord.BOTTLE, start, null, listOfNotNull(volumeMl.toString(), milk?.name).joinToString(":"), note)
    is Diaper -> EventRecord(EventRecord.DIAPER, start, null, kind.name, note)
    is Sleep -> EventRecord(EventRecord.SLEEP, start, end, "", note)
    is Medicine -> EventRecord(EventRecord.MEDICINE, start, null, name, note)
    is Weight -> EventRecord(EventRecord.WEIGHT, start, null, grams.toString(), note)
    is Height -> EventRecord(EventRecord.HEIGHT, start, null, millimeters.toString(), note)
    is Crying -> EventRecord(EventRecord.CRYING, start, end, cryingDetail(reasons, helped), note)
    // Метки заметки — через «;», как причины плача (ADR-0031)
    is Note -> EventRecord(EventRecord.NOTE, start, null, tags.joinToString(";"), note)
}

/** `detail` плача (ADR-0030): причины и что помогло через «|», внутри — через «;»: `колики;голод|укачивание`. */
private fun cryingDetail(reasons: List<String>, helped: List<String>): String =
    if (reasons.isEmpty() && helped.isEmpty()) "" else reasons.joinToString(";") + "|" + helped.joinToString(";")

/**
 * Проверяет запись и превращает её в [BabyEvent]; null — запись некорректна: неизвестный тип,
 * неверная деталь, вес вне [Weight.RANGE], рост вне [Height.RANGE], лекарство без названия. Регистр кодов не важен.
 * `end` учитывается только у сна, кормления и плача.
 */
fun EventRecord.toEvent(id: Long = 0, childId: Long): BabyEvent? = when (type.trim().uppercase()) {
    EventRecord.FEEDING -> enumOrNull<Breast>(detail)?.let { Feeding(id, childId, start, end, it, note) }
    EventRecord.BOTTLE -> bottleOrNull(id, childId, start, detail, note)
    EventRecord.DIAPER -> enumOrNull<DiaperKind>(detail)?.let { Diaper(id, childId, start, it, note) }
    EventRecord.SLEEP -> Sleep(id, childId, start, end, note)
    EventRecord.MEDICINE -> detail.takeIf { it.isNotBlank() }?.let { Medicine(id, childId, start, it, note) }
    EventRecord.WEIGHT -> detail.trim().toIntOrNull()?.takeIf { it in Weight.RANGE }
        ?.let { Weight(id, childId, start, it, note) }
    EventRecord.HEIGHT -> detail.trim().toIntOrNull()?.takeIf { it in Height.RANGE }
        ?.let { Height(id, childId, start, it, note) }
    EventRecord.CRYING -> cryingOrNull(id, childId, start, end, detail, note)
    EventRecord.NOTE -> Note(id, childId, start, tags(detail), note)
    else -> null
}

/**
 * Плач из `detail` вида `причины|что помогло` ([cryingDetail]): метки приводятся к общему виду
 * ([normalizeTag]), повторы убираются. Пустой `detail` — плач без подробностей; больше одного «|» —
 * запись некорректна.
 */
private fun cryingOrNull(id: Long, childId: Long, start: Long, end: Long?, detail: String, note: String): Crying? {
    val parts = detail.split('|')
    if (parts.size > 2) return null
    return Crying(id, childId, start, end, reasons = tags(parts[0]), helped = tags(parts.getOrNull(1)), note = note)
}

/** Метки из части `detail` через «;»: в общем виде ([normalizeTag]), без пустых и повторов. */
private fun tags(part: String?): List<String> = part.orEmpty().split(';').mapNotNull(::normalizeTag).distinct()

/**
 * `detail` бутылочки (ADR-0025): объём в мл, а если отмечено, что внутри, — через двоеточие код
 * [Milk]: `120`, `120:FORMULA`. Объём вне [Bottle.RANGE] или неизвестный код — запись некорректна.
 */
private fun bottleOrNull(id: Long, childId: Long, start: Long, detail: String, note: String): Bottle? {
    val parts = detail.split(':')
    val volume = parts[0].trim().toIntOrNull()?.takeIf { it in Bottle.RANGE } ?: return null
    val milk = when (parts.size) {
        1 -> null
        2 -> enumOrNull<Milk>(parts[1]) ?: return null
        else -> return null
    }
    return Bottle(id, childId, start, volume, milk, note)
}

private inline fun <reified T : Enum<T>> enumOrNull(code: String): T? =
    enumValues<T>().firstOrNull { it.name == code.trim().uppercase() }
