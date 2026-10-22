package com.kid.babylog.feature.transfer

import androidx.test.platform.app.InstrumentationRegistry
import com.kid.babylog.core.data.room.AppDatabase
import com.kid.babylog.core.data.room.RoomDiaryRepository
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.testing.FakeDocumentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.InputStream
import java.io.OutputStream

/**
 * Архив с фото на настоящей базе Room (ADR-0045): фото пишутся в транзакции импорта, пока файл читается
 * на другом потоке, и оборванный архив не оставляет в дневнике ничего.
 */
@RunWith(RobolectricTestRunner::class)
class DiaryArchiveRoomTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val source = AppDatabase.inMemory(context)
    private val target = AppDatabase.inMemory(context)

    /** Файл читается на Dispatchers.IO, как у `ContentResolverDocumentStore`, — внутри транзакции импорта. */
    private val files = FakeDocumentStore()
    private val store = object : DocumentStore {
        override suspend fun <T> read(uri: String, block: suspend (InputStream) -> T): T =
            withContext(Dispatchers.IO) { files.read(uri, block) }

        override suspend fun <T> write(uri: String, block: suspend (OutputStream) -> T): T =
            withContext(Dispatchers.IO) { files.write(uri, block) }
    }

    @After
    fun close() {
        source.close()
        target.close()
    }

    private suspend fun exportSource(photoSize: Int = 64) {
        val repo = RoomDiaryRepository(source)
        val masha = repo.addChild("Маша", null, null, ChildPhoto(jpeg(1, 64)))
        val diaper = repo.addEvent(Diaper(childId = masha, start = 1_000_000, kind = DiaperKind.DIRTY))
        repo.setEventPhotos(diaper, PhotoEdit(emptyList(), listOf(jpeg(2, photoSize), jpeg(3, photoSize))))
        store.write(FILE) { exportArchive(repo, it) }
    }

    @Test
    fun `photos are written in the import transaction`() = runBlocking {
        exportSource()
        val repo = RoomDiaryRepository(target)
        val result = importFile(repo, store, FILE, defaultChildId = 1)

        assertEquals(2, result.photosAdded)
        val masha = repo.allChildren().single()
        assertArrayEquals(jpeg(1, 64), masha.photo!!.jpeg)
        val diaper = repo.allEvents().single()
        assertEquals(listOf(jpeg(2, 64), jpeg(3, 64)).map { it.toList() }, repo.observeEventPhotos(diaper.id).first().map { it.jpeg.toList() })
    }

    @Test
    fun `broken archive leaves the diary as it was`() = runBlocking {
        exportSource(photoSize = 50_000)
        val whole = files.files.getValue(FILE)
        files.files[FILE] = whole.copyOf(whole.size - 30_000)
        val repo = RoomDiaryRepository(target)

        val error = runCatching { importFile(repo, store, FILE, defaultChildId = 1) }.exceptionOrNull()
        assertTrue("$error", error is IllegalArgumentException)
        assertEquals(emptyList<Child>(), repo.allChildren())
        assertEquals(0, repo.allEvents().size)
        assertEquals(emptyMap<Long, List<Long>>(), repo.photoIds())
    }

    private companion object {
        const val FILE = "baby-diary.zip"

        fun jpeg(tag: Int, size: Int): ByteArray =
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + ByteArray(size) { tag.toByte() }
    }
}
