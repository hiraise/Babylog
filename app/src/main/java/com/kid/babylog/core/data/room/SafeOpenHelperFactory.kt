package com.kid.babylog.core.data.room

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import java.util.concurrent.Executor

/**
 * Открытие базы поверх обычного (ADR-0033): перед первым открытием процессом — снимок для копии
 * ([DatabaseBackups.snapshot]), проверка снимка — в фоне ([background]). Повреждённую базу обработчик
 * Android по умолчанию удалил бы целиком, даже из-за одной испорченной страницы, — здесь она
 * восстанавливается из копии ([DatabaseBackups.recover]).
 */
class SafeOpenHelperFactory(
    private val backups: DatabaseBackups,
    private val background: Executor,
    private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory(),
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val helper = delegate.create(
            SupportSQLiteOpenHelper.Configuration(
                configuration.context,
                configuration.name,
                RecoveringCallback(configuration.callback, backups),
                configuration.useNoBackupDirectory,
                configuration.allowDataLossOnRecovery,
            )
        )
        return SnapshotBeforeOpen(helper, backups, background)
    }
}

/** Колбэк Room как есть, кроме повреждения базы: вместо удаления — восстановление из копии. */
private class RecoveringCallback(
    private val room: SupportSQLiteOpenHelper.Callback,
    private val backups: DatabaseBackups,
) : SupportSQLiteOpenHelper.Callback(room.version) {

    override fun onConfigure(db: SupportSQLiteDatabase) = room.onConfigure(db)

    override fun onCreate(db: SupportSQLiteDatabase) = room.onCreate(db)

    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = room.onUpgrade(db, oldVersion, newVersion)

    override fun onDowngrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = room.onDowngrade(db, oldVersion, newVersion)

    override fun onOpen(db: SupportSQLiteDatabase) = room.onOpen(db)

    override fun onCorruption(db: SupportSQLiteDatabase) {
        runCatching { db.close() }
        backups.recover()
    }
}

/** Перед первым открытием базы — снимок для копии: пока процесс не открыл базу, её файлы никто не пишет. */
private class SnapshotBeforeOpen(
    private val helper: SupportSQLiteOpenHelper,
    private val backups: DatabaseBackups,
    private val background: Executor,
) : SupportSQLiteOpenHelper by helper {

    @Volatile
    private var snapshotTaken = false

    override val writableDatabase: SupportSQLiteDatabase
        get() {
            beforeOpen()
            return helper.writableDatabase
        }

    override val readableDatabase: SupportSQLiteDatabase
        get() {
            beforeOpen()
            return helper.readableDatabase
        }

    private fun beforeOpen() {
        if (snapshotTaken) return
        synchronized(this) {
            if (snapshotTaken) return
            // Копия — не повод не открыть дневник: сбой снимка пропускаем, попробуем при следующем запуске
            runCatching { backups.snapshot() }.getOrNull()?.let { dir -> background.execute { runCatching { backups.verify(dir) } } }
            snapshotTaken = true
        }
    }
}
