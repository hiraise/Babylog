package com.kid.babylog.core.model

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Длина названия болезни: «кишечная инфекция» с запасом, но не фраза — фразу пишут в комментарий. */
const val MAX_ILLNESS_NAME_LENGTH = 40

/** Новая болезнь в пределах стольких суток после прошлой — лист спросит: новая или прошлая вернулась (ADR-0040). */
const val RELAPSE_DAYS = 7L

/** «Обычно» на экране болезни — средние за столько суток до её начала. */
const val USUAL_DAYS = 14L

/** Лекарство, которого не давали столько суток до болезни, начато в ней: его курс остаётся в болезни и после конца. */
const val COURSE_LOOKBACK_DAYS = 14L

/** Курс идёт, пока приёмы не реже этого: два раза в день — с запасом на пропущенную запись. */
const val COURSE_GAP_MILLIS = 36 * 60 * 60_000L

/** Названия, которые лист предлагает, пока своих мало: что чаще всего бывает у малышей. */
val DEFAULT_ILLNESS_NAMES = listOf("простуда", "ОРВИ", "отит", "кишечная инфекция", "конъюнктивит")

private const val DAY_MILLIS = 86_400_000L
private val NAME_SEPARATORS = Regex("[;:]")
private val NAME_SPACES = Regex("\\s+")

/**
 * Название болезни (ADR-0040) в общем виде: регистр как набрали («ОРВИ»), одиночные пробелы, без «;» и «:»
 * (ими названия разделены в `detail`), не длиннее [MAX_ILLNESS_NAME_LENGTH]. null — пусто.
 */
fun normalizeIllnessName(raw: String): String? =
    raw.replace(NAME_SEPARATORS, " ").replace(NAME_SPACES, " ").trim().take(MAX_ILLNESS_NAME_LENGTH).trim().ifEmpty { null }

/** Идущая болезнь ребёнка (у него она одна, ADR-0040); записи — одного ребёнка. */
fun List<BabyEvent>.activeIllness(): Illness? = filterIsInstance<Illness>().filter { it.isActive }.maxByOrNull { it.start }

/**
 * Прошлая болезнь, если идущей нет, а прошлая кончилась не больше [RELAPSE_DAYS] суток назад: новая
 * может оказаться ею — тогда лист предложит её продолжить.
 */
fun List<BabyEvent>.recentIllness(now: Long): Illness? {
    if (activeIllness() != null) return null
    return filterIsInstance<Illness>().mapNotNull { illness -> illness.end?.let { illness to it } }
        .maxByOrNull { it.second }
        ?.takeIf { (_, end) -> now - end in 0..RELAPSE_DAYS * DAY_MILLIS }?.first
}

fun Illness.startDate(zone: ZoneId): LocalDate = Instant.ofEpochMilli(start).atZone(zone).toLocalDate()

/** Последний день болезни; идущая — [today]. */
fun Illness.lastDate(today: LocalDate, zone: ZoneId): LocalDate =
    end?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() } ?: today

/** День болезни на [date]: день начала — первый. */
fun Illness.dayNumber(date: LocalDate, zone: ZoneId): Int = ChronoUnit.DAYS.between(startDate(zone), date).toInt() + 1

/** Сколько дней длилась (идущая — длится по [today]) болезнь: оба крайних дня считаются. */
fun Illness.days(today: LocalDate, zone: ZoneId): Int = dayNumber(lastDate(today, zone), zone)

/** День, с которого стоит название: без своего — день начала болезни [illness]. */
fun IllnessName.sinceDate(illness: Illness, zone: ZoneId): LocalDate = since ?: illness.startDate(zone)

/** Дни, которые захватила болезнь — по всем болезням ребёнка: их «Аналитика» закрашивает (ADR-0040). */
fun List<BabyEvent>.illnessDates(today: LocalDate, zone: ZoneId): Set<LocalDate> =
    filterIsInstance<Illness>().flatMapTo(HashSet()) { illness ->
        val first = illness.startDate(zone)
        val last = illness.lastDate(today, zone)
        generateSequence(first) { it.plusDays(1) }.takeWhile { it <= last }.toList()
    }

/**
 * Записи болезни (ADR-0040): температура, лекарства и заметки от её начала до конца (идущей — до [now])
 * и курсы лекарств, начатых в ней ([courseAfter]), — по возрастанию времени. Тегов нет: болезнь —
 * это время, а не метка на записях.
 */
fun List<BabyEvent>.illnessRecords(illness: Illness, now: Long): List<BabyEvent> {
    val until = illness.end ?: now
    val inside = filter { event ->
        event.childId == illness.childId && event.start in illness.start..until &&
            when (event) {
                is Temperature, is Medicine, is Note -> true
                is Feeding, is Bottle, is Diaper, is Sleep, is Weight, is Height, is Crying, is Illness -> false
            }
    }
    return (inside + courseAfter(illness)).sortedWith(compareBy<BabyEvent> { it.start }.thenBy { it.id })
}

/**
 * Приёмы после конца болезни, которые продолжают курс, начатый в ней: лекарство давали в болезни, но не
 * за [COURSE_LOOKBACK_DAYS] суток до неё (витамин D каждый день — не курс болезни), и следующий приём
 * не позже [COURSE_GAP_MILLIS] после предыдущего. Антибиотик пьют неделю, а выздоравливают раньше.
 */
private fun List<BabyEvent>.courseAfter(illness: Illness): List<Medicine> {
    val end = illness.end ?: return emptyList()
    return filterIsInstance<Medicine>().filter { it.childId == illness.childId }.groupBy { medicineKey(it.name) }.values.flatMap { records ->
        val sorted = records.sortedBy { it.start }
        val inside = sorted.filter { it.start in illness.start..end }
        val usedBefore = sorted.any { it.start in illness.start - COURSE_LOOKBACK_DAYS * DAY_MILLIS until illness.start }
        if (inside.isEmpty() || usedBefore) return@flatMap emptyList()
        var last = inside.last().start
        sorted.filter { it.start > end }.takeWhile { next ->
            (next.start - last <= COURSE_GAP_MILLIS).also { continues -> if (continues) last = next.start }
        }
    }
}

/**
 * Курсы, которые, похоже, ещё идут: лекарства, начатые в болезни (за [COURSE_LOOKBACK_DAYS] суток до неё
 * их не давали) и данные не раньше [COURSE_GAP_MILLIS] до [now]. Лист «Выздоровел(а)» предупреждает:
 * их приёмы до конца курса останутся в болезни ([illnessRecords]).
 */
fun List<BabyEvent>.ongoingCourses(illness: Illness, now: Long): List<String> {
    val until = illness.end ?: now
    return filterIsInstance<Medicine>().filter { it.childId == illness.childId }.groupBy { medicineKey(it.name) }.values.mapNotNull { records ->
        val inside = records.filter { it.start in illness.start..until }
        val usedBefore = records.any { it.start in illness.start - COURSE_LOOKBACK_DAYS * DAY_MILLIS until illness.start }
        val last = records.maxBy { it.start }
        last.name.trim().takeIf { inside.isNotEmpty() && !usedBefore && now - last.start <= COURSE_GAP_MILLIS }
    }
}

/**
 * Лекарство болезни: название из последней записи, когда давали в последний раз, комментарий (доза)
 * последнего приёма, где он был, и сколько раз за болезнь. Для карточки на «Сегодня», листа лекарства
 * и экрана болезни — только факты, без доз и интервалов от приложения (ADR-0040).
 */
data class IllnessMedicine(val name: String, val last: Long, val note: String, val count: Int, val times: List<Long>)

/** Лекарства болезни [illness], последние данные первыми; названия сравниваются без регистра и «ё». */
fun List<BabyEvent>.illnessMedicines(illness: Illness, now: Long): List<IllnessMedicine> =
    illnessRecords(illness, now).filterIsInstance<Medicine>()
        .groupBy { medicineKey(it.name) }.values
        .map { records ->
            val newest = records.maxBy { it.start }
            IllnessMedicine(
                name = newest.name.trim(),
                last = newest.start,
                note = records.sortedByDescending { it.start }.firstOrNull { it.note.isNotBlank() }?.note?.trim().orEmpty(),
                count = records.size,
                times = records.map { it.start }.sorted(),
            )
        }
        .sortedByDescending { it.last }

/**
 * Названия для листов болезни: из прошлых болезней ребёнка — частые первыми, — затем стандартные
 * ([DEFAULT_ILLNESS_NAMES]); без повторов без учёта регистра.
 */
fun List<BabyEvent>.illnessNameChoices(): List<String> {
    // Свежие болезни первыми: у названия остаётся последнее написание
    val past = filterIsInstance<Illness>().sortedByDescending { it.start }.flatMap { it.names }.map { it.name }
    val counted = past.groupBy { it.lowercase() }.values
        .sortedWith(compareByDescending<List<String>> { it.size }.thenBy { it.first().lowercase() })
        .map { it.first() }
    return (counted + DEFAULT_ILLNESS_NAMES).distinctBy { it.lowercase() }
}

/** Названия болезни через запятую: «ОРВИ, отит»; без названий — null. */
fun Illness.title(): String? = names.joinToString(", ") { it.name }.ifEmpty { null }

/** Одно лекарство — названия, которые различаются только регистром или «ё». */
private fun medicineKey(name: String): String = name.trim().lowercase().replace('ё', 'е')
