package com.kid.babylog.core.data.room

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.EventRecord

/** v1 → v2: профиль малыша (дата рождения). */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `profile` (`id` INTEGER NOT NULL, `birthDate` INTEGER, PRIMARY KEY(`id`))"
        )
    }
}

/**
 * v2 → v3: несколько детей. Профиль становится первым ребёнком (id = 1, имя [Child.DEFAULT_NAME]),
 * все записи привязываются к нему. Таблица `events` пересоздаётся: SQLite не умеет добавлять
 * колонку с внешним ключом. SQL совпадает с `app/schemas/.../3.json`.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `children` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`name` TEXT NOT NULL, `birthDate` INTEGER)"
        )
        db.execSQL(
            "INSERT INTO `children` (`id`, `name`, `birthDate`) " +
                "VALUES (1, ?, (SELECT `birthDate` FROM `profile` WHERE `id` = 1))",
            arrayOf(Child.DEFAULT_NAME),
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `events_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`childId` INTEGER NOT NULL, `type` TEXT NOT NULL, `startTime` INTEGER NOT NULL, " +
                "`endTime` INTEGER, `detail` TEXT NOT NULL, `note` TEXT NOT NULL, " +
                "FOREIGN KEY(`childId`) REFERENCES `children`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )"
        )
        db.execSQL(
            "INSERT INTO `events_new` (`id`, `childId`, `type`, `startTime`, `endTime`, `detail`, `note`) " +
                "SELECT `id`, 1, `type`, `startTime`, `endTime`, `detail`, `note` FROM `events`"
        )
        db.execSQL("DROP TABLE `events`")
        db.execSQL("ALTER TABLE `events_new` RENAME TO `events`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_childId` ON `events` (`childId`)")
        db.execSQL("DROP TABLE `profile`")
    }
}

/**
 * v3 → v4: кормление с длительностью (ADR-0015). Теперь `endTime = NULL` у кормления значит
 * «идёт сейчас», а у прежних кормлений конца не было: закрываем их моментом начала — это
 * кормления без длительности. Таблицы не меняются.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE `events` SET `endTime` = `startTime` WHERE `type` = ? AND `endTime` IS NULL",
            arrayOf(EventRecord.FEEDING),
        )
    }
}

/** v4 → v5: пол ребёнка (ADR-0016). У прежних детей он не указан — `NULL`. */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `children` ADD COLUMN `sex` TEXT")
    }
}

/** v5 → v6: фото ребёнка (ADR-0023). У прежних детей фото нет — `NULL`. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `children` ADD COLUMN `photo` BLOB")
    }
}
