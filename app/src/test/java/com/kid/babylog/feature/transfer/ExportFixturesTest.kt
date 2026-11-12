package com.kid.babylog.feature.transfer

import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.TemperatureMethod
import com.kid.babylog.core.model.Weight
import com.kid.babylog.testing.FakeDiaryRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.ZoneId
import java.util.zip.ZipInputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** Общие файлы совместимости Android/iOS (ADR-0049). Обновление: `BABYLOG_UPDATE_EXPORT_FIXTURES=1`. */
class ExportFixturesTest {

    @Test
    fun `shared fixtures are reproducible from the fixed diary`() = runTest {
        val archive = ByteArrayOutputStream()
        exportArchive(fixtureDiary(), archive, ZONE)
        val zip = archive.toByteArray()
        val archiveEntries = entries(zip)
        val csv = archiveEntries.single { it.name == ARCHIVE_CSV }.bytes

        val generated = File("build/export-fixtures").apply { mkdirs() }
        generated.resolve("sample.csv").writeBytes(csv)
        generated.resolve("sample.zip").writeBytes(zip)
        val fixtures = File("../fixtures/export")
        if (System.getenv("BABYLOG_UPDATE_EXPORT_FIXTURES") == "1") {
            fixtures.mkdirs()
            fixtures.resolve("sample.csv").writeBytes(csv)
            fixtures.resolve("sample.zip").writeBytes(zip)
        }

        assertArrayEquals("sample.csv отличается: обнови фикстуры по docs/development.md", fixtures.resolve("sample.csv").readBytes(), csv)
        val expected = entries(fixtures.resolve("sample.zip").readBytes())
        assertEquals("Состав sample.zip", expected.map { it.name to it.method }, archiveEntries.map { it.name to it.method })
        assertArrayEquals("CSV в sample.zip и sample.csv должен совпадать", csv, archiveEntries.first().bytes)
        expected.zip(archiveEntries).forEach { (wanted, actual) ->
            assertArrayEquals("Содержимое ${wanted.name}", wanted.bytes, actual.bytes)
        }
    }

    private suspend fun fixtureDiary(): FakeDiaryRepository {
        val repo = FakeDiaryRepository(
            listOf(
                Child(1, "Маша", ms("2026-05-20 00:00:00"), Sex.GIRL, ChildPhoto(jpeg(1))),
                Child(2, "Петя, младший", null),
            ),
        )
        repo.addEvent(Medicine(childId = 2, start = ms("2026-08-15 09:00:00"), name = "Витамин D", note = "1 капля"))
        repo.addEvent(Weight(childId = 1, start = ms("2026-08-15 10:00:00"), grams = 4350, note = "после кормления"))
        repo.addEvent(Height(childId = 1, start = ms("2026-08-15 10:05:00"), millimeters = 545, note = "у врача"))
        repo.addEvent(Sleep(childId = 1, start = ms("2026-08-15 13:00:00"), end = ms("2026-08-15 14:00:00")))
        repo.addEvent(
            Feeding(
                childId = 1,
                start = ms("2026-08-15 14:30:00"),
                end = ms("2026-08-15 14:45:00"),
                breast = Breast.LEFT,
                note = "a, \"b\"",
            ),
        )
        val diaper = repo.addEvent(Diaper(childId = 2, start = ms("2026-08-15 15:10:00"), kind = DiaperKind.DIRTY))
        repo.setEventPhotos(diaper, PhotoEdit(emptyList(), listOf(jpeg(2), jpeg(3))))
        val note = repo.addEvent(
            Note(
                childId = 2,
                start = ms("2026-08-15 15:10:00"),
                tags = listOf("питание мамы", "колики"),
                note = "Днём ела капусту, проверить",
            ),
        )
        repo.setEventPhotos(note, PhotoEdit(emptyList(), listOf(jpeg(4))))
        repo.addEvent(Bottle(childId = 1, start = ms("2026-08-15 17:00:00"), volumeMl = 120, milk = Milk.FORMULA))
        repo.addEvent(
            Crying(
                childId = 1,
                start = ms("2026-08-15 18:32:00"),
                end = ms("2026-08-15 18:56:00"),
                reasons = listOf("колики", "усталость"),
                helped = listOf("укачивание"),
            ),
        )
        repo.addEvent(
            Illness(
                childId = 1,
                start = ms("2026-10-07 19:30:00"),
                end = ms("2026-10-14 20:00:00"),
                names = listOf(IllnessName("ОРВИ"), IllnessName("Отит", LocalDate.of(2026, 10, 10))),
            ),
        )
        repo.addEvent(
            Temperature(childId = 1, start = ms("2026-10-10 03:10:00"), tenths = 392, method = TemperatureMethod.ARMPIT),
        )
        return repo
    }

    private data class Entry(val name: String, val method: Int, val bytes: ByteArray)

    private fun entries(bytes: ByteArray): List<Entry> {
        val result = mutableListOf<Entry>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                result += Entry(entry.name, entry.method, zip.readBytes())
            }
        }
        return result
    }

    private companion object {
        val ZONE: ZoneId = ZoneId.of("Europe/Moscow")

        fun ms(value: String): Long = CsvCodec.parseTime(value, ZONE)!!

        fun jpeg(tag: Int): ByteArray =
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(64) { tag.toByte() }
    }
}
