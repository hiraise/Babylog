package com.kid.babylog.core.model

import java.time.LocalDate

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
        const val ILLNESS = "ILLNESS"
        const val TEMPERATURE = "TEMPERATURE"
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
    is Illness -> EventRecord(EventRecord.ILLNESS, start, end, illnessDetail(names), note)
    // Как у бутылочки: число и через двоеточие — код, если отмечен (ADR-0041)
    is Temperature -> EventRecord(EventRecord.TEMPERATURE, start, null, listOfNotNull(tenths.toString(), method?.name).joinToString(":"), note)
}

/**
 * `detail` болезни (ADR-0040): названия через «;», у каждого через «:» — день, с которого оно стоит:
 * `ОРВИ;Отит:2026-10-10` — ОРВИ с начала болезни, отит с 10 октября.
 */
private fun illnessDetail(names: List<IllnessName>): String =
    names.joinToString(";") { name -> name.name + (name.since?.let { ":$it" } ?: "") }

/** `detail` плача (ADR-0030): причины и что помогло через «|», внутри — через «;»: `колики;голод|укачивание`. */
private fun cryingDetail(reasons: List<String>, helped: List<String>): String =
    if (reasons.isEmpty() && helped.isEmpty()) "" else reasons.joinToString(";") + "|" + helped.joinToString(";")

/**
 * Проверяет запись и превращает её в [BabyEvent]; null — запись некорректна: неизвестный тип,
 * неверная деталь, вес вне [Weight.RANGE], рост вне [Height.RANGE], температура вне [Temperature.RANGE],
 * лекарство без названия. Регистр кодов не важен. `end` учитывается только у сна, кормления, плача и болезни.
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
    EventRecord.ILLNESS -> illnessNames(detail)?.let { Illness(id, childId, start, end, it, note) }
    EventRecord.TEMPERATURE -> temperatureOrNull(id, childId, start, detail, note)
    else -> null
}

/**
 * Названия болезни из `detail` ([illnessDetail]): день через «:» необязателен — без него название стоит
 * с начала болезни. Пустые и повторы (без учёта регистра) убираются. Неверный день — запись некорректна (null).
 */
private fun illnessNames(detail: String): List<IllnessName>? {
    val names = mutableListOf<IllnessName>()
    for (part in detail.split(';')) {
        if (part.isBlank()) continue
        val pieces = part.split(':')
        if (pieces.size > 2) return null
        val name = normalizeIllnessName(pieces[0]) ?: continue
        val since = pieces.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it.trim()) }.getOrNull() ?: return null }
        if (names.none { it.name.equals(name, ignoreCase = true) }) names += IllnessName(name, since)
    }
    return names
}

/** Температура из `detail` вида `384` или `384:ARMPIT` (ADR-0041); вне [Temperature.RANGE] или неизвестный код — null. */
private fun temperatureOrNull(id: Long, childId: Long, start: Long, detail: String, note: String): Temperature? {
    val parts = detail.split(':')
    val tenths = parts[0].trim().toIntOrNull()?.takeIf { it in Temperature.RANGE } ?: return null
    val method = when (parts.size) {
        1 -> null
        2 -> enumOrNull<TemperatureMethod>(parts[1]) ?: return null
        else -> return null
    }
    return Temperature(id, childId, start, tenths, method, note)
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
