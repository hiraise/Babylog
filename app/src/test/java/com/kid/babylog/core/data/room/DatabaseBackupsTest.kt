package com.kid.babylog.core.data.room

import android.database.sqlite.SQLiteDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.kid.babylog.core.data.DatabaseRecovery
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.testing.FakeRecoveryNotices
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Копии базы и восстановление после повреждения (ADR-0033): на настоящих файлах SQLite и с настоящим
 * Room — повреждение ловит SQLite, а не тест.
 */
@RunWith(RobolectricTestRunner::class)
class DatabaseBackupsTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbFile = context.getDatabasePath(AppDatabase.NAME)
    private val notices = FakeRecoveryNotices()
    private var now = 1_786_000_000_000L
    private val backups = DatabaseBackups(dbFile, context.noBackupFilesDir, notices, now = { now })
    private val hour = 60 * 60 * 1000L

    @After
    fun cleanUp() {
        context.noBackupFilesDir.deleteRecursively()
        context.deleteDatabase(AppDatabase.NAME)
    }

    @Test
    fun `a snapshot becomes a verified single-file backup once a day, two latest are kept`() {
        writeDiary(rows = 3)
        backups.snapshot()!!.let(backups::verify)
        assertEquals(listOf(now), backups.backups().map { it.time })
        assertEquals(3, rowsIn(backups.backups().single().file))

        now += 23 * hour
        assertNull(backups.snapshot()) // копии меньше суток
        now += 2 * hour
        backups.snapshot()!!.let(backups::verify)
        now += 25 * hour
        backups.snapshot()!!.let(backups::verify)
        assertEquals(2, backups.backups().size)
        assertEquals(now, backups.backups().first().time)
        // От снимков ничего не осталось, кроме копий
        assertEquals(2, File(context.noBackupFilesDir, "db-backups").listFiles()!!.size)
    }

    @Test
    fun `a damaged snapshot is thrown away and the older backup stays`() {
        writeDiary(rows = 1)
        backups.snapshot()!!.let(backups::verify)
        val good = backups.backups().single()

        now += 25 * hour
        val snapshot = backups.snapshot()!!
        snapshot.resolve(AppDatabase.NAME).writeBytes(ByteArray(8192) { 0x5A })
        backups.verify(snapshot)

        assertEquals(listOf(good), backups.backups())
        assertTrue(!snapshot.exists())
    }

    @Test
    fun `corrupted database is restored from the backup instead of being deleted`() = runBlocking {
        val first = Diaper(childId = 1, start = T1, kind = DiaperKind.WET)
        useApp { it.addEvent(first) }
        // Второй запуск: перед открытием базы — снимок для копии
        useApp { it.addEvent(Diaper(childId = 1, start = T2, kind = DiaperKind.DIRTY)) }
        val backupTime = backupsOf().backups().single().time

        corrupt(dbFile)
        useApp { repo ->
            assertEquals(listOf(Child.DEFAULT_NAME), repo.allChildren().map { it.name })
            // Копия снята до второй записи: она пропала, первая — на месте
            assertEquals(listOf(T1), repo.allEvents().map { it.start })
        }
        assertEquals(DatabaseRecovery(at = notices.pending.value!!.at, backupTime = backupTime), notices.pending.value)
        // Испорченный файл не удалён, а отложен
        assertTrue(File(context.noBackupFilesDir, "db-corrupted").walk().any { it.name == AppDatabase.NAME })
    }

    @Test
    fun `without a backup the diary starts anew and the message says so`() = runBlocking {
        useApp { it.addEvent(Diaper(childId = 1, start = T1, kind = DiaperKind.WET)) }
        File(context.noBackupFilesDir, "db-backups").deleteRecursively()

        corrupt(dbFile)
        useApp { repo -> assertTrue(repo.allEvents().isEmpty()) }
        assertNull(notices.pending.value!!.backupTime)
    }

    /** Один запуск приложения: открыть базу так же, как `AppContainer`, и закрыть. */
    private suspend fun useApp(block: suspend (RoomDiaryRepository) -> Unit) {
        val db = AppDatabase.build(context, notices, Runnable::run, System::currentTimeMillis)
        try {
            block(RoomDiaryRepository(db))
        } finally {
            db.close()
        }
    }

    private fun backupsOf() = DatabaseBackups(dbFile, context.noBackupFilesDir, notices, System::currentTimeMillis)

    /** Сбой памяти: страницы файла превратились в мусор. */
    private fun corrupt(file: File) {
        file.writeBytes(ByteArray(file.length().toInt()) { 0x5A })
        File(file.path + "-wal").delete()
        File(file.path + "-shm").delete()
    }

    private fun writeDiary(rows: Int) {
        dbFile.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(dbFile, null).use { db ->
            db.execSQL("CREATE TABLE notes (id INTEGER PRIMARY KEY, text TEXT NOT NULL)")
            repeat(rows) { db.execSQL("INSERT INTO notes (text) VALUES ('запись $it')") }
        }
    }

    private fun rowsIn(file: File): Int =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            db.rawQuery("SELECT COUNT(*) FROM notes", null).use { it.moveToFirst(); it.getInt(0) }
        }

    private companion object {
        const val T1 = 1_786_000_000_000L
        const val T2 = 1_786_003_600_000L
    }
}
