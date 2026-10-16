package com.kid.babylog.feature.transfer

import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.toRecord
import com.kid.babylog.testing.FakeDiaryRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvImportTest {

    private fun ms(s: String): Long = CsvCodec.parseTime(s)!!

    private val legacyFile = "﻿type,start,end,detail,note\r\n" +
        "PROFILE,2026-05-20 00:00:00,,,\r\n" +
        "FEEDING,2026-08-15 10:00:00,,LEFT,\r\n" +
        "SLEEP,2026-08-15 11:00:00,2026-08-15 12:00:00,,\r\n"

    @Test
    fun `legacy file goes to the default child`() = runTest {
        val repo = FakeDiaryRepository(listOf(Child(1, "Маша", null), Child(2, "Петя", null)))
        val result = importCsv(repo, legacyFile, defaultChildId = 2)
        assertEquals(2, result.added)
        assertEquals(1, result.birthDatesSet)
        assertEquals(0, result.childrenAdded)
        assertEquals(setOf(2L), repo.events.value.map { it.childId }.toSet())
        assertEquals(ms("2026-05-20 00:00:00"), repo.children.value.first { it.id == 2L }.birthDate)
        assertNull(repo.children.value.first { it.id == 1L }.birthDate)
    }

    @Test
    fun `child column matches existing names ignoring case and creates new children`() = runTest {
        val repo = FakeDiaryRepository(listOf(Child(1, "Маша", null)))
        val csv = "type,start,end,detail,note,child\r\n" +
            "PROFILE,,,,,Петя\r\n" +
            "DIAPER,2026-08-15 10:00:00,,WET,, маша \r\n" +
            "DIAPER,2026-08-15 10:05:00,,DIRTY,,Петя\r\n" +
            "DIAPER,2026-08-15 10:10:00,,MIXED,,Вася\r\n"
        val result = importCsv(repo, csv, defaultChildId = 1)
        assertEquals(3, result.added)
        assertEquals(2, result.childrenAdded)
        assertEquals(0, result.errors)
        val byName = repo.children.value.associateBy { it.name }
        assertEquals(listOf("Маша", "Петя", "Вася"), repo.children.value.map { it.name })
        assertNull(byName.getValue("Петя").birthDate)
        assertEquals(
            mapOf(DiaperKind.WET to "Маша", DiaperKind.DIRTY to "Петя", DiaperKind.MIXED to "Вася"),
            repo.events.value.associate { e -> (e as Diaper).kind to repo.children.value.first { it.id == e.childId }.name },
        )
    }

    @Test
    fun `reimporting own export changes nothing`() = runTest {
        val repo = FakeDiaryRepository(listOf(Child(1, "Маша", ms("2026-05-20 00:00:00")), Child(2, "Петя", null)))
        repo.addEvent(Feeding(childId = 1, start = ms("2026-08-15 10:00:00"), breast = Breast.LEFT, note = "a, \"b\""))
        repo.addEvent(Feeding(childId = 2, start = ms("2026-08-15 10:00:00"), breast = Breast.LEFT, note = "a, \"b\""))
        repo.addEvent(Sleep(childId = 2, start = ms("2026-08-15 11:00:00"), end = ms("2026-08-15 12:30:00")))
        val result = importCsv(repo, exportCsv(repo).text, defaultChildId = 1)
        assertEquals(ImportResult(duplicates = 3), result)
        assertEquals(3, repo.events.value.size)
    }

    @Test
    fun `export imports into another phone with the same children and records`() = runTest {
        val source = FakeDiaryRepository(listOf(Child(1, "Маша", ms("2026-05-20 00:00:00")), Child(2, "Петя", null)))
        source.addEvent(Diaper(childId = 2, start = ms("2026-08-16 08:00:00"), kind = DiaperKind.WET))
        source.addEvent(Feeding(childId = 1, start = ms("2026-08-15 10:00:00"), breast = Breast.RIGHT))
        val target = FakeDiaryRepository() // новый телефон: один «Малыш» без записей
        importCsv(target, exportCsv(source).text, defaultChildId = 1)

        fun snapshot(repo: FakeDiaryRepository) = repo.events.value.map { e ->
            repo.children.value.first { it.id == e.childId }.let { it.name to it.birthDate } to e.toRecord()
        }.toSet()
        assertEquals(snapshot(source), snapshot(target))
    }

    @Test
    fun `duplicates inside one file are counted once`() = runTest {
        val repo = FakeDiaryRepository()
        val row = "FEEDING,2026-08-15 10:00:00,,LEFT,\r\n"
        val result = importCsv(repo, row + row, defaultChildId = 1)
        assertEquals(1, result.added)
        assertEquals(1, result.duplicates)
    }

    @Test
    fun `same record for different children is not a duplicate`() = runTest {
        val repo = FakeDiaryRepository(listOf(Child(1, "Маша", null), Child(2, "Петя", null)))
        val csv = "FEEDING,2026-08-15 10:00:00,,LEFT,,Маша\r\nFEEDING,2026-08-15 10:00:00,,LEFT,,Петя\r\n"
        assertEquals(2, importCsv(repo, csv, defaultChildId = 1).added)
    }

    @Test
    fun `active sleep and invalid rows are skipped without creating children`() = runTest {
        val repo = FakeDiaryRepository()
        val csv = "SLEEP,2026-08-15 13:00:00,,,,Вася\r\n" +
            "UNKNOWN,2026-08-15 13:00:00,,,,Вася\r\n" +
            "FEEDING,мусор,,LEFT,,Вася\r\n" +
            "PROFILE,мусор,,,,Вася\r\n" +
            "PROFILE,,,,\r\n"
        val result = importCsv(repo, csv, defaultChildId = 1)
        assertEquals(1, result.skippedActiveSleep)
        assertEquals(4, result.errors)
        assertEquals(0, result.added)
        assertEquals(listOf(Child.DEFAULT_NAME), repo.children.value.map { it.name })
    }

    @Test
    fun `different birth date in file does not overwrite the current one`() = runTest {
        val repo = FakeDiaryRepository(listOf(Child(1, "Маша", ms("2026-05-21 00:00:00"))))
        val result = importCsv(repo, "PROFILE,2026-05-20 00:00:00,,,,Маша\r\n", defaultChildId = 1)
        assertEquals(1, result.birthDateConflicts)
        assertEquals(ms("2026-05-21 00:00:00"), repo.children.value.single().birthDate)
    }
}
