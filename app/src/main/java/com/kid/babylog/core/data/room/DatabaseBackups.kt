package com.kid.babylog.core.data.room

import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import com.kid.babylog.core.data.DatabaseRecovery
import com.kid.babylog.core.data.RecoveryNotices
import java.io.File

/**
 * Резервные копии базы и восстановление после её повреждения (ADR-0033).
 *
 * Копия — раз в сутки: [snapshot] копирует файлы базы до того, как процесс её откроет (их ещё никто
 * не пишет), [verify] в фоне проверяет снимок и делает из него один файл в `db-backups/`. [recover]
 * работает вместо обработчика по умолчанию, который удалил бы повреждённую базу: её файлы уходят
 * в `db-corrupted/`, на их место — свежая копия. Обе папки — в `no_backup/`: копии не уходят ни в облако,
 * ни при переносе на новый телефон.
 */
class DatabaseBackups(
    private val database: File,
    noBackupDir: File,
    private val notices: RecoveryNotices,
    private val now: () -> Long,
) {
    private val backupDir = noBackupDir.resolve("db-backups")
    private val corruptedDir = noBackupDir.resolve("db-corrupted")
    private val lock = Any()

    /** Проверенные копии, свежие первыми. */
    fun backups(): List<DatabaseBackup> =
        backupDir.listFiles().orEmpty()
            .mapNotNull { file -> BACKUP_NAME.matchEntire(file.name)?.let { DatabaseBackup(file, it.groupValues[1].toLong()) } }
            .sortedByDescending { it.time }

    /**
     * Снимок файлов базы, если последней копии больше суток; вызывать до открытия базы. Возвращает папку
     * снимка для [verify]; null — копия не нужна, базы ещё нет или копия не влезет в память телефона.
     */
    fun snapshot(): File? = synchronized(lock) {
        if (!database.exists()) return null
        val time = now()
        if (backups().firstOrNull()?.let { time - it.time < BACKUP_INTERVAL_MS } == true) return null
        // -shm не нужен: SQLite построит его заново по журналу
        val files = databaseFiles().filter { it.exists() && !it.name.endsWith("-shm") }
        backupDir.mkdirs()
        // Заполнить память ради копии хуже, чем пропустить день: записи в дневник перестали бы сохраняться
        if (backupDir.usableSpace < files.sumOf(File::length) + FREE_SPACE_RESERVE) return null
        val dir = backupDir.resolve("$SNAPSHOT_PREFIX$time")
        files.forEach { it.copyTo(dir.resolve(it.name), overwrite = true) }
        dir
    }

    /**
     * Снимок [dir] → копия: журнал WAL вливается в файл базы, `PRAGMA quick_check` проверяет её целиком.
     * Испорченный снимок удаляется, а прежние копии остаются; из проверенных хранятся [KEEP_BACKUPS] свежих.
     */
    fun verify(dir: File) {
        val time = dir.name.removePrefix(SNAPSHOT_PREFIX).toLong()
        val file = dir.resolve(database.name)
        val healthy = runCatching { checkAndMerge(file) }.getOrDefault(false)
        synchronized(lock) {
            if (healthy && file.renameTo(backupDir.resolve("babylog-$time.db"))) {
                backups().drop(KEEP_BACKUPS).forEach { it.file.delete() }
            }
            dir.deleteRecursively()
        }
    }

    /**
     * База повреждена: её файлы — в `db-corrupted/` (хранится последний случай, по нему можно разобраться),
     * на их место — свежая копия, пользователю — сообщение ([RecoveryNotices]). Без копии Room создаст
     * пустую базу.
     */
    fun recover(): Unit = synchronized(lock) {
        val time = now()
        corruptedDir.deleteRecursively()
        val aside = corruptedDir.resolve(time.toString()).apply { mkdirs() }
        databaseFiles().filter(File::exists).forEach { file ->
            val target = aside.resolve(file.name)
            if (!file.renameTo(target)) {
                file.copyTo(target, overwrite = true)
                file.delete()
            }
        }
        val backup = backups().firstOrNull()
        backup?.file?.copyTo(database, overwrite = true)
        notices.report(DatabaseRecovery(at = time, backupTime = backup?.time))
    }

    private fun databaseFiles(): List<File> = listOf("", "-wal", "-shm", "-journal").map { File(database.path + it) }

    // Свой обработчик повреждений: обработчик по умолчанию удалил бы испорченный снимок сам
    private fun checkAndMerge(file: File): Boolean =
        SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READWRITE, DatabaseErrorHandler { }).use { db ->
            db.rawQuery("PRAGMA journal_mode = DELETE", null).use { it.moveToFirst() }
            db.rawQuery("PRAGMA quick_check", null).use { it.moveToFirst() && it.getString(0) == "ok" }
        }

    private companion object {
        val BACKUP_NAME = Regex("""babylog-(\d+)\.db""")
        const val SNAPSHOT_PREFIX = "snapshot-"
        const val BACKUP_INTERVAL_MS = 24 * 60 * 60 * 1000L
        const val KEEP_BACKUPS = 2
        const val FREE_SPACE_RESERVE = 100L * 1024 * 1024
    }
}

/** Проверенная копия базы [file], снятая в момент [time]. */
data class DatabaseBackup(val file: File, val time: Long)
