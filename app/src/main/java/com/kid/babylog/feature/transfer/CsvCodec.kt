package com.kid.babylog.feature.transfer

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.EventRecord
import com.kid.babylog.core.model.toRecord
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * CSV-формат (ADR-0009): `type,start,end,detail,note,child`.
 * Время — локальное, "yyyy-MM-dd HH:mm:ss" (при импорте допускается 'T' как разделитель
 * и время без секунд). Экранирование по RFC 4180. Строка PROFILE описывает ребёнка:
 * дата рождения в `start` (может быть пустой), имя в `child`.
 */
object CsvCodec {

    const val HEADER = "type,start,end,detail,note,child"
    const val PROFILE_ROW = "PROFILE"
    private val FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun formatTime(millis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime().format(FORMAT)

    fun parseTime(raw: String, zone: ZoneId = ZoneId.systemDefault()): Long? {
        var s = raw.trim().replace('T', ' ')
        if (s.isEmpty()) return null
        if (s.length == 16) s += ":00" // yyyy-MM-dd HH:mm
        return try {
            LocalDateTime.parse(s, FORMAT).atZone(zone).toInstant().toEpochMilli()
        } catch (e: Exception) {
            null
        }
    }

    /** Весь дневник: строка PROFILE на каждого ребёнка, затем записи в переданном порядке. */
    fun encode(children: List<Child>, events: List<BabyEvent>): String {
        val names = children.associate { it.id to it.name }
        val sb = StringBuilder()
        sb.append('\uFEFF') // BOM, чтобы Excel корректно открывал кириллицу
        sb.append(HEADER).append("\r\n")
        for (child in children) {
            sb.append(PROFILE_ROW).append(',')
                .append(child.birthDate?.let { formatTime(it) } ?: "")
                .append(",,,,")
                .append(escape(child.name)).append("\r\n")
        }
        for (event in events) {
            val r = event.toRecord()
            sb.append(r.type).append(',')
                .append(formatTime(r.start)).append(',')
                .append(r.end?.let { formatTime(it) } ?: "").append(',')
                .append(escape(r.detail)).append(',')
                .append(escape(r.note)).append(',')
                .append(escape(names[event.childId] ?: "")).append("\r\n")
        }
        return sb.toString()
    }

    private fun escape(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + s.replace("\"", "\"\"") + "\""
        } else s

    /** Разбирает CSV в строки-записи с учётом кавычек и переносов внутри кавычек. */
    fun parseRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        val src = text.removePrefix("\uFEFF")
        while (i < src.length) {
            val c = src[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < src.length && src[i + 1] == '"' -> { field.append('"'); i++ }
                    c == '"' -> inQuotes = false
                    else -> field.append(c)
                }
                // Режим кавычек — только с начала поля; кавычка в середине — литерал (как в Excel)
                c == '"' && field.isEmpty() -> inQuotes = true
                c == ',' -> { row.add(field.toString()); field.clear() }
                c == '\r' -> { /* пропускаем, строку завершит \n */ }
                c == '\n' -> {
                    row.add(field.toString()); field.clear()
                    if (row.any { it.isNotBlank() }) rows.add(row.toList())
                    row.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        row.add(field.toString())
        if (row.any { it.isNotBlank() }) rows.add(row.toList())
        return rows
    }

    /**
     * Строка CSV → плоская запись; null — нет обязательных колонок или время не разбирается.
     * Тип и детали проверяет [com.kid.babylog.core.model.toEvent].
     */
    fun rowToRecord(row: List<String>): EventRecord? {
        if (row.size < 4) return null
        val start = parseTime(row[1]) ?: return null
        val end = if (row[2].isBlank()) null else parseTime(row[2]) ?: return null
        return EventRecord(
            type = row[0].trim(),
            start = start,
            end = end,
            detail = row[3].trim(),
            note = row.getOrElse(4) { "" }.trim(),
        )
    }

    /** Имя ребёнка из колонки `child`; пусто — колонки нет (файл старой версии) или она не заполнена. */
    fun childName(row: List<String>): String = row.getOrElse(5) { "" }.trim()
}
