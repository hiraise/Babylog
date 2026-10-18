package com.kid.babylog.core.ui

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import java.time.Instant
import java.time.LocalDate
import java.time.Period
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

val RU: Locale = Locale.forLanguageTag("ru")
private val TIME = DateTimeFormatter.ofPattern("HH:mm")
private val DATE = DateTimeFormatter.ofPattern("d MMMM, EEEE", RU)
private val FULL_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", RU)
private val SHORT_DATE = DateTimeFormatter.ofPattern("d MMM", RU)
private val SHORT_DATE_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", RU)

fun Long.toLocalDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDate()

fun formatClock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalTime().format(TIME)

fun formatDayHeader(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Сегодня"
    today.minusDays(1) -> "Вчера"
    else -> date.format(DATE)
}

/** «20 мая 2026» */
fun formatDate(millis: Long): String = millis.toLocalDate().format(FULL_DATE)

/** «сегодня», «вчера», «15 окт.», в другом году — «15 окт. 2025» */
fun formatShortDay(date: LocalDate, today: LocalDate): String = when {
    date == today -> "сегодня"
    date == today.minusDays(1) -> "вчера"
    date.year == today.year -> date.format(SHORT_DATE)
    else -> date.format(SHORT_DATE_YEAR)
}

/** «2 ч 15 мин», «45 мин», «меньше минуты» */
fun formatDuration(millis: Long): String {
    val totalMinutes = millis / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        totalMinutes < 1 -> "меньше минуты"
        hours == 0L -> "$minutes мин"
        minutes == 0L -> "$hours ч"
        else -> "$hours ч $minutes мин"
    }
}

/** «только что», «25 мин назад», «3 ч 10 мин назад» */
fun formatAgo(millis: Long): String {
    if (millis < 60_000) return "только что"
    return formatDuration(millis) + " назад"
}

/** Таймер сна и кормления: «1:23:45» или «23:45» */
fun formatTimer(millis: Long): String {
    val totalSeconds = millis / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

fun breastLabel(breast: Breast): String = when (breast) {
    Breast.LEFT -> "левая"
    Breast.RIGHT -> "правая"
}

/**
 * Слово по полу ребёнка (ADR-0016): женский род — для девочки, мужской — для мальчика и когда пол
 * не указан. Где звучит естественно, пиши без рода: «Маша спит», «Маша ест».
 */
fun bySex(sex: Sex?, masculine: String, feminine: String): String = if (sex == Sex.GIRL) feminine else masculine

fun diaperLabel(kind: DiaperKind, sex: Sex?): String = when (kind) {
    DiaperKind.WET -> bySex(sex, "пописал", "пописала")
    DiaperKind.DIRTY -> bySex(sex, "покакал", "покакала")
    DiaperKind.MIXED -> "и то, и то"
}

/**
 * Заголовок главного экрана: «Дневник Маши» — имя в родительном падеже ([genitiveName]). Имя,
 * которое правила не склоняют уверенно (несколько слов, не кириллица), — «Дневник: Петя, младший».
 */
fun diaryTitle(name: String, sex: Sex?): String {
    val trimmed = name.trim()
    return genitiveName(trimmed, sex)?.let { "Дневник $it" } ?: "Дневник: $trimmed"
}

/** Имена с беглой гласной: «Павел» → «Павла», а не «Павела». Ключ — в нижнем регистре. */
private val GENITIVE_EXCEPTIONS = mapOf("павел" to "павла", "лев" to "льва", "пётр" to "петра", "петр" to "петра")

/**
 * Родительный падеж имени по окончанию: Маша → Маши, Петя → Пети, Мария → Марии, Алиса → Алисы,
 * Андрей → Андрея, Иван → Ивана; у девочек на мягкий знак — Любовь → Любови, на согласную не
 * склоняются — Кармен (пол не указан — как мальчика, ADR-0016). Двойные через дефис склоняются по частям.
 * null — склонять не берёмся: несколько слов, цифры, латиница.
 */
internal fun genitiveName(name: String, sex: Sex?): String? {
    val cyrillic = name.isNotEmpty() && name.all { it == '-' || it in 'а'..'я' || it in 'А'..'Я' || it == 'ё' || it == 'Ё' }
    if (!cyrillic || name.startsWith("-") || name.endsWith("-")) return null
    return name.split("-").joinToString("-") { genitiveWord(it, sex) }
}

private fun genitiveWord(word: String, sex: Sex?): String {
    val lower = word.lowercase()
    GENITIVE_EXCEPTIONS[lower]?.let { return matchCase(it, word) }
    val stem = word.dropLast(1)
    val ending = when (lower.last()) {
        'я' -> "и" // Петя → Пети, Мария → Марии, Илья → Ильи
        'а' -> if (stem.lastOrNull()?.lowercaseChar()?.let { it in "гкхжчшщ" } == true) "и" else "ы" // Маша → Маши, Алиса → Алисы
        'й' -> "я" // Андрей → Андрея
        'ь' -> if (sex == Sex.GIRL) "и" else "я" // Любовь → Любови, Игорь → Игоря
        in "бвгджзклмнпрстфхцчшщ" -> return if (sex == Sex.GIRL) word else matchCase(lower + "а", word) // Иван → Ивана
        else -> return word // Нико, Мари: на гласные о, е, и, у, ю, ы, э имена не склоняются
    }
    return stem + if (word.length > 1 && word == word.uppercase()) ending.uppercase() else ending
}

/** [declined] в регистре исходного слова: «МАША» → «МАШИ», «маша» → «маши», «Маша» → «Маши». */
private fun matchCase(declined: String, original: String): String = when {
    original.length > 1 && original == original.uppercase() -> declined.uppercase()
    original.first().isUpperCase() -> declined.replaceFirstChar(Char::titlecase)
    else -> declined
}

/** Заголовок записи; [sex] — пол ребёнка, чья это запись. */
fun eventTitle(event: BabyEvent, sex: Sex?): String = when (event) {
    is Feeding -> "Кормление · ${breastLabel(event.breast)}" + if (event.isActive) " · идёт" else ""
    is Bottle -> "Кормление · ${bottleLabel(event.volumeMl, event.milk)}"
    is Diaper -> "Подгузник · ${diaperLabel(event.kind, sex)}"
    is Sleep -> if (event.isActive) "Сон · идёт" else "Сон"
    is Medicine -> "Лекарство · ${event.name}"
    is Weight -> "Вес · ${formatWeight(event.grams)}"
    is Height -> "Рост · ${formatHeight(event.millimeters)}"
}

fun eventTimeLine(event: BabyEvent): String = when (event) {
    is Sleep -> timeRange(event.start, event.end)
    // Кормления до ADR-0015 — без длительности: конец равен началу
    is Feeding -> if (event.end == event.start) formatClock(event.start) else timeRange(event.start, event.end)
    is Bottle, is Diaper, is Medicine, is Weight, is Height -> formatClock(event.start)
}

/**
 * «бутылочка 120 мл», «бутылочка 120 мл, смесь». Между числом и «мл» — неразрывный пробел:
 * в узкой строке истории «120» и «мл» иначе расходятся по строкам.
 */
fun bottleLabel(volumeMl: Int, milk: Milk?): String =
    "бутылочка $volumeMl\u00A0мл" + (milk?.let { ", ${milkLabel(it)}" } ?: "")

fun milkLabel(milk: Milk): String = when (milk) {
    Milk.FORMULA -> "смесь"
    Milk.BREAST_MILK -> "сцеженное молоко"
}

/** «13:40–15:05 · 1 ч 25 мин»; идущий таймер — «с 13:40». */
private fun timeRange(start: Long, end: Long?): String = end?.let {
    "${formatClock(start)}–${formatClock(it)} · ${formatDuration(it - start)}"
} ?: "с ${formatClock(start)}"

/** «850 г» до килограмма, дальше «4,35 кг» (без хвостовых нулей) */
fun formatWeight(grams: Int): String {
    if (grams < 1000) return "$grams г"
    val number = "%.2f".format(RU, grams / 1000.0)
        .removeSuffix("0").removeSuffix("0").removeSuffix(",")
    return "$number кг"
}

/** «62 см», «62,5 см» */
fun formatHeight(millimeters: Int): String = "${heightCentimeters(millimeters)} см"

/** Рост в сантиметрах без единиц — «62», «62,5»: так его вводят в листе роста. */
fun heightCentimeters(millimeters: Int): String {
    val tenths = millimeters % 10
    return if (tenths == 0) "${millimeters / 10}" else "${millimeters / 10},$tenths"
}

/** Возраст по-русски: «12 дн.», «2 мес 10 дн.», «1 г 3 мес» */
fun formatAge(birth: LocalDate, today: LocalDate, sex: Sex?): String {
    if (birth > today) return bySex(sex, "ещё не родился", "ещё не родилась")
    val period = Period.between(birth, today)
    val years = period.years
    val months = period.months
    val days = period.days
    return when {
        years == 0 && months == 0 -> "$days дн."
        years == 0 -> if (days > 0) "$months мес $days дн." else "$months мес"
        else -> if (months > 0) "$years г $months мес" else "$years г"
    }
}
