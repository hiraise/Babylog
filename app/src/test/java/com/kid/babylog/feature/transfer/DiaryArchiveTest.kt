package com.kid.babylog.feature.transfer

import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Архив экспорта с фото (ADR-0045): что в нём лежит и как импорт возвращает фото записям и детям. */
class DiaryArchiveTest {

    private fun ms(s: String): Long = CsvCodec.parseTime(s)!!

    /** Дневник на «старом телефоне»: фото у Маши, у подгузника два фото, у заметки Пети — одно. */
    private suspend fun sourceDiary(): FakeDiaryRepository {
        val repo = FakeDiaryRepository(listOf(Child(1, "Маша", null, Sex.GIRL, ChildPhoto(jpeg(1))), Child(2, "Петя", null)))
        repo.addEvent(Sleep(childId = 1, start = ms("2026-08-15 13:00:00"), end = ms("2026-08-15 14:00:00")))
        val diaper = repo.addEvent(Diaper(childId = 1, start = ms("2026-08-15 15:10:00"), kind = DiaperKind.DIRTY))
        repo.setEventPhotos(diaper, PhotoEdit(emptyList(), listOf(jpeg(2), jpeg(3))))
        val note = repo.addEvent(Note(childId = 2, start = ms("2026-08-15 15:10:00"), note = "сыпь, см. фото"))
        repo.setEventPhotos(note, PhotoEdit(emptyList(), listOf(jpeg(4))))
        return repo
    }

    private suspend fun exported(repo: FakeDiaryRepository): FakeDocumentStore =
        FakeDocumentStore().also { store -> store.write(FILE) { exportArchive(repo, it) } }

    private fun FakeDiaryRepository.photosOf(predicate: (BabyEvent) -> Boolean): List<ByteArray> {
        val event = events.value.single(predicate)
        return photos.value[event.id].orEmpty().map { it.jpeg }
    }

    @Test
    fun `archive starts with the CSV and names photos by record time`() = runTest {
        val store = FakeDocumentStore()
        val export = store.write(FILE) { exportArchive(sourceDiary(), it) }
        assertEquals(DiaryExport(events = 3, photos = 4), export)
        assertEquals("Экспортировано записей: 3, фото: 4", export.describe())
        assertEquals(
            listOf(
                "diary.csv",
                "photos/child_1.jpg",
                "photos/2026-08-15_15-10-00_1.jpg",
                "photos/2026-08-15_15-10-00_2.jpg",
                "photos/2026-08-15_15-10-00_3.jpg",
            ),
            entries(store.files.getValue(FILE)).keys.toList(),
        )
        val csv = entries(store.files.getValue(FILE)).getValue("diary.csv").toString(Charsets.UTF_8)
        assertTrue(csv.contains("DIAPER,2026-08-15 15:10:00,,DIRTY,,Маша,photos/2026-08-15_15-10-00_1.jpg;photos/2026-08-15_15-10-00_2.jpg\r\n"))
        assertTrue(csv.contains("PROFILE,,,GIRL,,Маша,photos/child_1.jpg\r\n"))
    }

    @Test
    fun `photos move to another phone with their records`() = runTest {
        val store = exported(sourceDiary())
        val target = FakeDiaryRepository() // новый телефон: один «Малыш» без записей
        val result = importFile(target, store, FILE, defaultChildId = 1)

        assertEquals(3, result.added)
        assertEquals(3, result.photosAdded)
        assertEquals(1, result.childPhotosSet)
        assertEquals(0, result.photosSkipped)
        assertEquals("Импорт: добавлено 3, новых детей 2, фото 3, фото детей 1", result.describe())
        assertArrayEquals(jpeg(1), target.children.value.single { it.name == "Маша" }.photo!!.jpeg)
        assertEquals(listOf(jpeg(2), jpeg(3)).map { it.toList() }, target.photosOf { it is Diaper }.map { it.toList() })
        assertEquals(listOf(jpeg(4).toList()), target.photosOf { it is Note }.map { it.toList() })
        assertEquals("архив читается дважды: CSV, затем фото", 2, store.opened)
    }

    @Test
    fun `archive returns photos to records moved earlier without them`() = runTest {
        val source = sourceDiary()
        val target = FakeDiaryRepository()
        // Раньше дневник переносили CSV-файлом — записи есть, фото нет
        importCsv(target, CsvCodec.encode(source.allChildren(), source.allEvents()), defaultChildId = 1)

        val result = importFile(target, exported(source), FILE, defaultChildId = 1)
        assertEquals(0, result.added)
        assertEquals(3, result.duplicates)
        assertEquals(3, result.photosAdded)
        assertEquals(3, result.photosToExisting)
        assertEquals(1, result.childPhotosSet)
        assertEquals("Импорт: добавлено 0, дубликатов 3, фото 3 (к прежним записям — 3), фото детей 1", result.describe())
        assertEquals(listOf(jpeg(2), jpeg(3)).map { it.toList() }, target.photosOf { it is Diaper }.map { it.toList() })
    }

    @Test
    fun `records with photos and children with a photo keep their own`() = runTest {
        val store = exported(sourceDiary())
        val target = FakeDiaryRepository(listOf(Child(1, "Маша", null, photo = ChildPhoto(jpeg(9)))))
        importFile(target, store, FILE, defaultChildId = 1)

        val again = importFile(target, store, FILE, defaultChildId = 1)
        assertEquals(3, again.duplicates)
        assertEquals(0, again.photosAdded)
        assertEquals(0, again.childPhotosSet)
        assertEquals("второй импорт фото не открывает", 3, store.opened)
        assertEquals(2, target.photosOf { it is Diaper }.size)
        assertArrayEquals(jpeg(9), target.children.value.single { it.name == "Маша" }.photo!!.jpeg)
    }

    @Test
    fun `CSV taken out of the archive imports records and asks for the archive`() = runTest {
        val csv = entries(exported(sourceDiary()).files.getValue(FILE)).getValue("diary.csv")
        val store = FakeDocumentStore(mutableMapOf("diary.csv" to csv))
        val result = importFile(FakeDiaryRepository(), store, "diary.csv", defaultChildId = 1)

        assertEquals(3, result.added)
        assertEquals(4, result.photosSkipped)
        assertTrue(result.describe(), result.describe().endsWith(", фото не загружены (4) — они в архиве .zip, импортируйте его"))
        assertEquals(1, store.opened)
    }

    @Test
    fun `archive repacked into a folder still imports`() = runTest {
        val archive = zip(
            "__MACOSX/Дневник/._diary.csv" to byteArrayOf(0, 5, 22, 7),
            "Дневник/diary.csv" to diaryCsv("photos/a.jpg").toByteArray(),
            "Дневник/photos/a.jpg" to jpeg(5),
        )
        val target = FakeDiaryRepository()
        val result = importFile(target, FakeDocumentStore(mutableMapOf(FILE to archive)), FILE, defaultChildId = 1)
        assertEquals(1, result.added)
        assertEquals(1, result.photosAdded)
        assertEquals(listOf(jpeg(5).toList()), target.photosOf { it is Diaper }.map { it.toList() })
    }

    @Test
    fun `archive repacked on Windows with a Cyrillic folder imports`() = runTest {
        // Проводник Windows пишет имена в кодировке DOS без флага UTF-8
        val archive = zip(
            "Дневник/diary.csv" to diaryCsv("photos/a.jpg").toByteArray(),
            "Дневник/photos/a.jpg" to jpeg(5),
            charset = Charset.forName("CP866"),
        )
        val target = FakeDiaryRepository()
        val result = importFile(target, FakeDocumentStore(mutableMapOf(FILE to archive)), FILE, defaultChildId = 1)
        assertEquals(1, result.photosAdded)
    }

    @Test
    fun `photos keep the order of the CSV whatever the order in the archive`() = runTest {
        val archive = zip(
            "diary.csv" to diaryCsv("photos/a.jpg;photos/b.jpg").toByteArray(),
            "photos/b.jpg" to jpeg(7),
            "photos/a.jpg" to jpeg(6),
        )
        val target = FakeDiaryRepository()
        importFile(target, FakeDocumentStore(mutableMapOf(FILE to archive)), FILE, defaultChildId = 1)
        assertEquals(listOf(jpeg(6), jpeg(7)).map { it.toList() }, target.photosOf { it is Diaper }.map { it.toList() })
    }

    @Test
    fun `missing too big or not JPEG photos are skipped and the record stays`() = runTest {
        val archive = zip(
            "diary.csv" to diaryCsv("photos/big.jpg;photos/text.jpg;photos/missing.jpg").toByteArray(),
            "photos/big.jpg" to jpeg(1, size = EventPhoto.MAX_BYTES + 1),
            "photos/text.jpg" to "не фото".toByteArray(),
        )
        val target = FakeDiaryRepository()
        val result = importFile(target, FakeDocumentStore(mutableMapOf(FILE to archive)), FILE, defaultChildId = 1)
        assertEquals(1, result.added)
        assertEquals(0, result.photosAdded)
        assertEquals(3, result.photosSkipped)
        assertTrue(result.describe(), result.describe().endsWith(", не загружено фото 3"))
        assertTrue(target.photos.value.isEmpty())
    }

    @Test
    fun `broken archive stops the import with a clear message`() = runTest {
        val source = sourceDiary()
        source.setEventPhotos(source.events.value.single { it is Note }.id, PhotoEdit(emptyList(), listOf(jpeg(4, size = 50_000))))
        val whole = exported(source).files.getValue(FILE)
        // Файл скачался не до конца: CSV целый, последнее фото оборвано
        val store = FakeDocumentStore(mutableMapOf(FILE to whole.copyOf(whole.size - 30_000)))
        val error = runCatching { importFile(FakeDiaryRepository(), store, FILE, defaultChildId = 1) }.exceptionOrNull()
        assertTrue("$error", error is IllegalArgumentException && error.message!!.startsWith("архив"))
    }

    @Test
    fun `archive without the diary CSV is rejected`() = runTest {
        val store = FakeDocumentStore(mutableMapOf(FILE to zip("photos/a.jpg" to jpeg(1))))
        val error = runCatching { importFile(FakeDiaryRepository(), store, FILE, defaultChildId = 1) }.exceptionOrNull()
        assertTrue("$error", error is IllegalArgumentException)
        assertEquals("в архиве нет diary.csv — это не дневник BabyLog", error!!.message)
    }

    @Test
    fun `fake store keeps photo order`() = runTest {
        // Проверка фейка: импорт ставит фото не по порядку, фейк упорядочивает их по месту, как база
        val repo = FakeDiaryRepository()
        val id = repo.addEvent(Diaper(childId = 1, start = 0, kind = DiaperKind.WET))
        repo.addEventPhoto(id, 1, jpeg(2))
        repo.addEventPhoto(id, 0, jpeg(1))
        assertEquals(listOf(jpeg(1), jpeg(2)).map { it.toList() }, repo.observeEventPhotos(id).first().map { it.jpeg.toList() })
    }

    private fun diaryCsv(photos: String) =
        "${CsvCodec.HEADER}\r\nPROFILE,,,,,Маша,\r\nDIAPER,2026-08-15 15:10:00,,WET,,Маша,$photos\r\n"

    private companion object {
        const val FILE = "baby-diary.zip"

        /** «JPEG» для импорта: начало файла JPEG, затем байты [tag] — само изображение импорт не разбирает. */
        fun jpeg(tag: Int, size: Int = 64): ByteArray =
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(size) { tag.toByte() }

        fun zip(vararg files: Pair<String, ByteArray>, charset: Charset = Charsets.UTF_8): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out, charset).use { zip ->
                files.forEach { (name, bytes) ->
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
            }
            return out.toByteArray()
        }

        /** Файлы архива по порядку: имя → содержимое. */
        fun entries(archive: ByteArray): LinkedHashMap<String, ByteArray> {
            val result = LinkedHashMap<String, ByteArray>()
            ZipInputStream(ByteArrayInputStream(archive)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    result[entry.name] = zip.readBytes()
                }
            }
            return result
        }
    }
}
