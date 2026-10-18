package com.kid.babylog.feature.transfer

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.model.toEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CsvCodecTest {

    private fun ms(s: String): Long = CsvCodec.parseTime(s)!!

    private val masha = Child(1, "Маша", ms("2026-05-20 00:00:00"))
    private val petya = Child(2, "Петя, младший", null)

    /** Строки данных (без заголовка и PROFILE) → записи с id ребёнка по имени. */
    private fun decode(csv: String, children: List<Child>): List<BabyEvent> =
        CsvCodec.parseRows(csv).drop(1)
            .filterNot { it[0] == CsvCodec.PROFILE_ROW }
            .map { row ->
                val child = children.first { it.name == CsvCodec.childName(row) }
                CsvCodec.rowToRecord(row)!!.toEvent(childId = child.id)!!
            }

    @Test
    fun `round trip preserves all fields and children`() {
        val events = listOf(
            Feeding(childId = 1, start = ms("2026-08-15 14:30:00"), end = ms("2026-08-15 14:45:10"), breast = Breast.LEFT),
            Diaper(childId = 2, start = ms("2026-08-15 15:10:05"), kind = DiaperKind.MIXED, note = "обычный"),
            Sleep(childId = 1, start = ms("2026-08-15 13:00:00"), end = ms("2026-08-15 14:20:59")),
            Medicine(childId = 2, start = ms("2026-08-15 09:00:00"), name = "Витамин D", note = "1 капля"),
            Weight(childId = 1, start = ms("2026-08-15 10:00:00"), grams = 4350, note = "после кормления"),
            Height(childId = 1, start = ms("2026-08-15 10:05:00"), millimeters = 545, note = "у врача"),
        )
        val csv = CsvCodec.encode(listOf(masha, petya), events)
        assertEquals(events, decode(csv, listOf(masha, petya)))
    }

    @Test
    fun `export starts with BOM and import skips it`() {
        val csv = CsvCodec.encode(listOf(masha), emptyList())
        // BOM нужен Excel, чтобы он понял UTF-8 и кириллицу
        assertTrue(csv.startsWith("﻿" + CsvCodec.HEADER))
        assertEquals(CsvCodec.HEADER.split(","), CsvCodec.parseRows(csv)[0])
    }

    @Test
    fun `header and profile rows describe every child`() {
        val csv = CsvCodec.encode(listOf(masha, petya), emptyList())
        val rows = CsvCodec.parseRows(csv)
        assertEquals(CsvCodec.HEADER.split(","), rows[0])
        assertEquals(listOf("PROFILE", "2026-05-20 00:00:00", "", "", "", "Маша"), rows[1])
        // Без даты рождения строка всё равно есть: она переносит имя ребёнка
        assertEquals(listOf("PROFILE", "", "", "", "", "Петя, младший"), rows[2])
    }

    @Test
    fun `profile row keeps sex in detail`() {
        val csv = CsvCodec.encode(listOf(masha.copy(sex = Sex.GIRL)), emptyList())
        assertEquals(listOf("PROFILE", "2026-05-20 00:00:00", "", "GIRL", "", "Маша"), CsvCodec.parseRows(csv)[1])
    }

    @Test
    fun `escaping survives commas quotes and newlines`() {
        val tricky = "запятая, \"кавычки\"\nи перенос"
        val event = Medicine(childId = 2, start = ms("2026-08-15 09:00:00"), name = "Нурофен, детский", note = tricky)
        val decoded = decode(CsvCodec.encode(listOf(petya), listOf(event)), listOf(petya)).single() as Medicine
        assertEquals("Нурофен, детский", decoded.name)
        assertEquals(tricky, decoded.note)
    }

    @Test
    fun `parseTime accepts T separator and missing seconds`() {
        assertEquals(ms("2026-08-15 14:30:00"), CsvCodec.parseTime("2026-08-15T14:30:00"))
        assertEquals(ms("2026-08-15 14:30:00"), CsvCodec.parseTime("2026-08-15 14:30"))
        assertNull(CsvCodec.parseTime("не дата"))
        assertNull(CsvCodec.parseTime(""))
    }

    @Test
    fun `rows without required columns or with bad time are rejected`() {
        assertNull(CsvCodec.rowToRecord(listOf("FEEDING", "мусор", "", "LEFT", "")))
        assertNull(CsvCodec.rowToRecord(listOf("SLEEP", "2026-08-15 09:00:00", "мусор", "", "")))
        assertNull(CsvCodec.rowToRecord(listOf("FEEDING")))
    }

    @Test
    fun `legacy file without child column is parsed`() {
        val csv = "﻿type,start,end,detail,note\r\nPROFILE,2026-05-20 00:00:00,,,\r\nfeeding,2026-08-15 10:00:00,,left,\r\n"
        val rows = CsvCodec.parseRows(csv)
        assertEquals(3, rows.size)
        assertEquals("", CsvCodec.childName(rows[2]))
        val event = CsvCodec.rowToRecord(rows[2])!!.toEvent(childId = 1) as Feeding
        assertEquals(Breast.LEFT, event.breast)
    }

    @Test
    fun `quote in the middle of a field is a literal`() {
        val csv = "MEDICINE,2026-08-10 12:00,,Нурофен шприц 5\",температура\r\n" +
            "SLEEP,2026-08-10 13:00,2026-08-10 14:00,,\r\n"
        val rows = CsvCodec.parseRows(csv)
        assertEquals(2, rows.size) // вторая строка не «съедена» кавычкой
        val record = CsvCodec.rowToRecord(rows[0])!!
        assertEquals("Нурофен шприц 5\"", record.detail)
        assertEquals("температура", record.note)
    }

    @Test
    fun `active sleep exports with empty end`() {
        val csv = CsvCodec.encode(listOf(masha), listOf(Sleep(childId = 1, start = ms("2026-08-15 13:00:00"))))
        assertTrue(csv.contains("SLEEP,2026-08-15 13:00:00,,,,Маша\r\n"))
    }
}
