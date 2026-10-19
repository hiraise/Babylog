package com.kid.babylog.feature.transfer

import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sex
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
        val start = ms("2026-08-15 10:00:00")
        repo.addEvent(Feeding(childId = 1, start = start, end = start + 600_000, breast = Breast.LEFT, note = "a, \"b\""))
        repo.addEvent(Feeding(childId = 2, start = start, end = start, breast = Breast.LEFT, note = "a, \"b\""))
        repo.addEvent(Sleep(childId = 2, start = ms("2026-08-15 11:00:00"), end = ms("2026-08-15 12:30:00")))
        // Кормление идёт во время экспорта: в файле оно без конца, но в базе уже есть
        repo.addEvent(Feeding(childId = 1, start = ms("2026-08-15 13:00:00"), breast = Breast.RIGHT))
        val result = importCsv(repo, exportCsv(repo).text, defaultChildId = 1)
        assertEquals(ImportResult(duplicates = 4), result)
        assertEquals(4, repo.events.value.size)
    }

    @Test
    fun `export imports into another phone with the same children and records`() = runTest {
        val source = FakeDiaryRepository(listOf(Child(1, "Маша", ms("2026-05-20 00:00:00")), Child(2, "Петя", null)))
        source.addEvent(Diaper(childId = 2, start = ms("2026-08-16 08:00:00"), kind = DiaperKind.WET))
        source.addEvent(
            Feeding(childId = 1, start = ms("2026-08-15 10:00:00"), end = ms("2026-08-15 10:20:00"), breast = Breast.RIGHT),
        )
        val target = FakeDiaryRepository() // новый телефон: один «Малыш» без записей
        importCsv(target, exportCsv(source).text, defaultChildId = 1)

        fun snapshot(repo: FakeDiaryRepository) = repo.events.value.map { e ->
            repo.children.value.first { it.id == e.childId }.let { it.name to it.birthDate } to e.toRecord()
        }.toSet()
        assertEquals(snapshot(source), snapshot(target))
    }

    @Test
    fun `feeding without end from an old file has no duration and no timer`() = runTest {
        val repo = FakeDiaryRepository()
        importCsv(repo, legacyFile, defaultChildId = 1)
        val feeding = repo.events.value.filterIsInstance<Feeding>().single()
        assertEquals(ms("2026-08-15 10:00:00"), feeding.end)
        assertEquals(feeding.start, feeding.end)
        // Повторный импорт того же файла находит и кормление, уже закрытое при первом импорте
        assertEquals(2, importCsv(repo, legacyFile, defaultChildId = 1).duplicates)
    }

    @Test
    fun `bottle rows are imported with volume and milk`() = runTest {
        val repo = FakeDiaryRepository()
        val csv = "type,start,end,detail,note,child\n" +
            "BOTTLE,2026-08-15 10:00:00,,120:FORMULA,докорм,Малыш\n" +
            "BOTTLE,2026-08-15 13:00:00,,90,,Малыш\n"
        assertEquals(2, importCsv(repo, csv, defaultChildId = 1).added)
        assertEquals(
            listOf(120 to Milk.FORMULA, 90 to null),
            repo.events.value.filterIsInstance<Bottle>().map { it.volumeMl to it.milk },
        )
    }

    @Test
    fun `crying rows keep reasons and helped, a running crying is skipped`() = runTest {
        val repo = FakeDiaryRepository()
        val csv = "type,start,end,detail,note,child\n" +
            "CRYING,2026-08-15 18:30:00,2026-08-15 18:55:00,колики;голод|укачивание,вечер,Малыш\n" +
            "CRYING,2026-08-15 21:00:00,,,,Малыш\n"
        val result = importCsv(repo, csv, defaultChildId = 1)
        assertEquals(1, result.added)
        // Незавершённый плач стал бы «фантомным» таймером (ADR-0030), как сон
        assertEquals(1, result.skippedActiveCrying)
        val crying = repo.events.value.filterIsInstance<Crying>().single()
        assertEquals(listOf("колики", "голод"), crying.reasons)
        assertEquals(listOf("укачивание"), crying.helped)
        assertEquals(ms("2026-08-15 18:55:00"), crying.end)
    }

    @Test
    fun `note rows keep tags and text with commas`() = runTest {
        val repo = FakeDiaryRepository()
        val csv = "type,start,end,detail,note,child\n" +
            "NOTE,2026-08-15 21:50:00,,питание мамы;колики,\"Днём ела капусту, проверить\",Малыш\n"
        assertEquals(1, importCsv(repo, csv, defaultChildId = 1).added)
        val note = repo.events.value.filterIsInstance<Note>().single()
        assertEquals(listOf("питание мамы", "колики"), note.tags)
        assertEquals("Днём ела капусту, проверить", note.note)
        assertEquals(ms("2026-08-15 21:50:00"), note.start)
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
    fun `sex from file is set only when missing and old files leave it alone`() = runTest {
        val repo = FakeDiaryRepository(listOf(Child(1, "Маша", null), Child(2, "Петя", null, Sex.BOY)))
        val csv = "PROFILE,,,GIRL,,Маша\r\n" + "PROFILE,,,girl,,Петя\r\n" + "PROFILE,,,BOY,,Вася\r\n"
        val result = importCsv(repo, csv, defaultChildId = 1)
        assertEquals(1, result.sexesSet)
        assertEquals(1, result.sexConflicts)
        assertEquals(1, result.childrenAdded)
        assertEquals(listOf(Sex.GIRL, Sex.BOY, Sex.BOY), repo.children.value.map { it.sex })
        // Файл без пола (до ADR-0016) пол не сбрасывает
        importCsv(repo, legacyFile, defaultChildId = 1)
        assertEquals(Sex.GIRL, repo.children.value.first().sex)
    }

    @Test
    fun `unknown sex code is an error and creates no child`() = runTest {
        val repo = FakeDiaryRepository()
        val result = importCsv(repo, "PROFILE,,,MAYBE,,Вася\r\n", defaultChildId = 1)
        assertEquals(1, result.errors)
        assertEquals(listOf(Child.DEFAULT_NAME), repo.children.value.map { it.name })
    }

    @Test
    fun `sex survives export to another phone`() = runTest {
        val source = FakeDiaryRepository(listOf(Child(1, "Маша", null, Sex.GIRL)))
        val target = FakeDiaryRepository()
        importCsv(target, exportCsv(source).text, defaultChildId = 1)
        assertEquals(Sex.GIRL, target.children.value.first { it.name == "Маша" }.sex)
    }

    @Test
    fun `different birth date in file does not overwrite the current one`() = runTest {
        val repo = FakeDiaryRepository(listOf(Child(1, "Маша", ms("2026-05-21 00:00:00"))))
        val result = importCsv(repo, "PROFILE,2026-05-20 00:00:00,,,,Маша\r\n", defaultChildId = 1)
        assertEquals(1, result.birthDateConflicts)
        assertEquals(ms("2026-05-21 00:00:00"), repo.children.value.single().birthDate)
    }
}
