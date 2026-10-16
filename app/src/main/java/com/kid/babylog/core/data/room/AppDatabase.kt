package com.kid.babylog.core.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kid.babylog.core.model.Child

/**
 * База дневника. Схема меняется только повышением [version] с миграцией в [Migrations.kt];
 * JSON-схемы лежат в `app/schemas/` (ADR-0008).
 */
@Database(entities = [ChildEntity::class, EventEntity::class], version = 4)
abstract class AppDatabase : RoomDatabase() {
    abstract fun diaryDao(): DiaryDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "babylog.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
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
