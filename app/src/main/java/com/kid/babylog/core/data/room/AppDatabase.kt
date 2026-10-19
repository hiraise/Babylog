package com.kid.babylog.core.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kid.babylog.core.data.RecoveryNotices
import com.kid.babylog.core.model.Child
import java.util.concurrent.Executor

/**
 * База дневника. Схема меняется только повышением [version] с миграцией в [Migrations.kt];
 * JSON-схемы лежат в `app/schemas/` (ADR-0008).
 */
@Database(entities = [ChildEntity::class, EventEntity::class, EventPhotoEntity::class], version = 7)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        const val NAME = "babylog.db"

        /**
         * База открывается только через [SafeOpenHelperFactory] (ADR-0033): ежедневная копия и восстановление
         * повреждённой базы из неё, а не удаление. [background] — проверка снимков для копий, [now] — часы для копий.
         */
        fun build(context: Context, notices: RecoveryNotices, background: Executor, now: () -> Long): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .openHelperFactory(
                    SafeOpenHelperFactory(DatabaseBackups(context.getDatabasePath(NAME), context.noBackupFilesDir, notices, now), background)
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .addCallback(CreateFirstChild)
                .build()
    }
}

/** В базе всегда есть хотя бы один ребёнок: новая база создаётся сразу с ним. */
private object CreateFirstChild : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        db.execSQL("INSERT INTO `children` (`name`, `birthDate`) VALUES (?, NULL)", arrayOf(Child.DEFAULT_NAME))
    }
}
