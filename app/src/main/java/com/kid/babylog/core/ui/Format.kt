package com.kid.babylog.core.ui

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
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

/** Таймер сна: «1:23:45» или «23:45» */
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

fun diaperLabel(kind: DiaperKind): String = when (kind) {
    DiaperKind.WET -> "пописал"
    DiaperKind.DIRTY -> "покакал"
    DiaperKind.MIXED -> "и то, и то"
}

fun eventTitle(event: BabyEvent): String = when (event) {
    is Feeding -> "Кормление · ${breastLabel(event.breast)}"
    is Diaper -> "Подгузник · ${diaperLabel(event.kind)}"
    is Sleep -> if (event.isActive) "Сон · идёт" else "Сон"
    is Medicine -> "Лекарство · ${event.name}"
    is Weight -> "Вес · ${formatWeight(event.grams)}"
}

fun eventTimeLine(event: BabyEvent): String = when (event) {
    is Sleep -> event.end?.let { end ->
        "${formatClock(event.start)}–${formatClock(end)} · ${formatDuration(end - event.start)}"
    } ?: "с ${formatClock(event.start)}"
    is Feeding, is Diaper, is Medicine, is Weight -> formatClock(event.start)
}

/** «850 г» до килограмма, дальше «4,35 кг» (без хвостовых нулей) */
fun formatWeight(grams: Int): String {
    if (grams < 1000) return "$grams г"
    val number = "%.2f".format(RU, grams / 1000.0)
        .removeSuffix("0").removeSuffix("0").removeSuffix(",")
    return "$number кг"
}

/** Возраст по-русски: «12 дн.», «2 мес 10 дн.», «1 г 3 мес» */
fun formatAge(birth: LocalDate, today: LocalDate): String {
    if (birth > today) return "ещё не родился"
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
