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
        const val DIAPER = "DIAPER"
        const val SLEEP = "SLEEP"
        const val MEDICINE = "MEDICINE"
        const val WEIGHT = "WEIGHT"
    }
}

fun BabyEvent.toRecord(): EventRecord = when (this) {
    is Feeding -> EventRecord(EventRecord.FEEDING, start, null, breast.name, note)
    is Diaper -> EventRecord(EventRecord.DIAPER, start, null, kind.name, note)
    is Sleep -> EventRecord(EventRecord.SLEEP, start, end, "", note)
    is Medicine -> EventRecord(EventRecord.MEDICINE, start, null, name, note)
    is Weight -> EventRecord(EventRecord.WEIGHT, start, null, grams.toString(), note)
}

/**
 * Проверяет запись и превращает её в [BabyEvent]; null — запись некорректна: неизвестный тип,
 * неверная деталь, вес вне [Weight.RANGE], лекарство без названия. Регистр кодов не важен.
 * `end` учитывается только у сна.
 */
fun EventRecord.toEvent(id: Long = 0, childId: Long): BabyEvent? = when (type.trim().uppercase()) {
    EventRecord.FEEDING -> enumOrNull<Breast>(detail)?.let { Feeding(id, childId, start, it, note) }
    EventRecord.DIAPER -> enumOrNull<DiaperKind>(detail)?.let { Diaper(id, childId, start, it, note) }
    EventRecord.SLEEP -> Sleep(id, childId, start, end, note)
    EventRecord.MEDICINE -> detail.takeIf { it.isNotBlank() }?.let { Medicine(id, childId, start, it, note) }
    EventRecord.WEIGHT -> detail.trim().toIntOrNull()?.takeIf { it in Weight.RANGE }
        ?.let { Weight(id, childId, start, it, note) }
    else -> null
}

private inline fun <reified T : Enum<T>> enumOrNull(code: String): T? =
    enumValues<T>().firstOrNull { it.name == code.trim().uppercase() }
