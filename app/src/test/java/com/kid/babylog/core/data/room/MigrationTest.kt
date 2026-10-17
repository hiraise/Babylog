package com.kid.babylog.core.data.room

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Обновление приложения поверх старой базы — на JVM вместо ручной проверки на эмуляторе
 * (ADR-0008, ADR-0013, ADR-0015). Схемы версий берутся из `app/schemas/`.
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** База версии 2, какой она была у пользователя до появления нескольких детей. */
    private fun createVersion2(birthDate: Long?) {
        helper.createDatabase(DB_NAME, 2).use { db ->
            if (birthDate != null) db.execSQL("INSERT INTO profile (id, birthDate) VALUES (1, ?)", arrayOf(birthDate))
            val insert = "INSERT INTO events (id, type, startTime, endTime, detail, note) VALUES (?, ?, ?, ?, ?, ?)"
            db.execSQL(insert, arrayOf<Any?>(7, "FEEDING", T1, null, "LEFT", "ночью"))
            db.execSQL(insert, arrayOf<Any?>(8, "SLEEP", T2, T3, "", ""))
            db.execSQL(insert, arrayOf<Any?>(9, "SLEEP", T4, null, "", "спит сейчас"))
        }
    }

    /** База версии 3: кормления ещё без длительности, сон идёт. */
    private fun createVersion3() {
        helper.createDatabase(DB_NAME, 3).use { db ->
            db.execSQL("INSERT INTO children (id, name, birthDate) VALUES (1, 'Маша', NULL)")
            val insert = "INSERT INTO events (id, childId, type, startTime, endTime, detail, note) VALUES (?, 1, ?, ?, ?, ?, ?)"
            db.execSQL(insert, arrayOf<Any?>(1, "FEEDING", T1, null, "RIGHT", "ночью"))
            db.execSQL(insert, arrayOf<Any?>(2, "SLEEP", T2, null, "", ""))
            db.execSQL(insert, arrayOf<Any?>(3, "DIAPER", T3, null, "WET", ""))
        }
    }

    @Test
    fun `migration 4 to 5 produces schema 5 and children have no sex yet`() = runBlocking {
        helper.createDatabase(DB_NAME, 4).use { db ->
            db.execSQL("INSERT INTO children (id, name, birthDate) VALUES (1, 'Маша', ?)", arrayOf<Any?>(BIRTH))
        }
        helper.runMigrationsAndValidate(DB_NAME, 5, true, MIGRATION_4_5).close()
        val db = AppDatabase.build(context)
        try {
            assertEquals(listOf(Child(1, "Маша", BIRTH, sex = null)), RoomDiaryRepository(db).allChildren())
        } finally {
            db.close()
        }
    }

    @Test
    fun `migration 5 to 6 produces schema 6 and children keep their data without a photo`() = runBlocking {
        helper.createDatabase(DB_NAME, 5).use { db ->
            db.execSQL("INSERT INTO children (id, name, birthDate, sex) VALUES (1, 'Маша', ?, 'GIRL')", arrayOf<Any?>(BIRTH))
        }
        helper.runMigrationsAndValidate(DB_NAME, 6, true, MIGRATION_5_6).close()
        val db = AppDatabase.build(context)
        try {
            val repo = RoomDiaryRepository(db)
            assertEquals(listOf(Child(1, "Маша", BIRTH, Sex.GIRL, photo = null)), repo.allChildren())
            // Колонка принимает фото: у прежних детей его можно поставить после обновления
            val photo = ChildPhoto(byteArrayOf(1, 2, 3))
            repo.updateChild(repo.allChildren().single().copy(photo = photo))
            assertEquals(photo, repo.allChildren().single().photo)
        } finally {
            db.close()
        }
    }

    @Test
    fun `migration 6 to 7 adds an empty photo table and keeps the records`() = runBlocking {
        helper.createDatabase(DB_NAME, 6).use { db ->
            db.execSQL("INSERT INTO children (id, name, birthDate, sex, photo) VALUES (1, 'Маша', NULL, NULL, NULL)")
            db.execSQL(
                "INSERT INTO events (id, childId, type, startTime, endTime, detail, note) VALUES (5, 1, 'DIAPER', ?, NULL, 'DIRTY', '')",
                arrayOf<Any?>(T1),
            )
        }
        helper.runMigrationsAndValidate(DB_NAME, 7, true, MIGRATION_6_7).close()
        val db = AppDatabase.build(context)
        try {
            val repo = RoomDiaryRepository(db)
            assertEquals(listOf(Diaper(id = 5, childId = 1, start = T1, kind = DiaperKind.DIRTY)), repo.allEvents())
            assertEquals(emptyList<EventPhoto>(), repo.observeEventPhotos(5).first())
            repo.setEventPhotos(5, PhotoEdit(keep = emptyList(), add = listOf(byteArrayOf(1))))
            assertEquals(mapOf(5L to 1), repo.observePhotoCounts(1).first())
        } finally {
            db.close()
        }
    }

    @Test
    fun `migration 2 to 3 produces schema 3`() {
        createVersion2(birthDate = BIRTH)
        helper.runMigrationsAndValidate(DB_NAME, 3, true, MIGRATION_2_3).close()
    }

    @Test
    fun `migration 3 to 4 produces schema 4`() {
        createVersion3()
        helper.runMigrationsAndValidate(DB_NAME, 4, true, MIGRATION_3_4).close()
    }

    @Test
    fun `update over version 3 closes old feedings and keeps the active sleep`() = runBlocking {
        createVersion3()
        val db = AppDatabase.build(context)
        try {
            val expected = listOf(
                Feeding(id = 1, childId = 1, start = T1, end = T1, breast = Breast.RIGHT, note = "ночью"),
                Sleep(id = 2, childId = 1, start = T2),
                Diaper(id = 3, childId = 1, start = T3, kind = DiaperKind.WET),
            )
            assertEquals(expected, RoomDiaryRepository(db).allEvents())
        } finally {
            db.close()
        }
    }

    @Test
    fun `update over version 2 keeps every record, active sleep and birth date`() = runBlocking {
        createVersion2(birthDate = BIRTH)
        val db = AppDatabase.build(context)
        try {
            val repo = RoomDiaryRepository(db)
            assertEquals(listOf(Child(1, Child.DEFAULT_NAME, BIRTH)), repo.allChildren())
            val expected = listOf(
                Feeding(id = 7, childId = 1, start = T1, end = T1, breast = Breast.LEFT, note = "ночью"),
                Sleep(id = 8, childId = 1, start = T2, end = T3),
                Sleep(id = 9, childId = 1, start = T4, note = "спит сейчас"),
            )
            assertEquals(expected, repo.allEvents())
        } finally {
            db.close()
        }
    }

    @Test
    fun `profile without birth date becomes a child without it`() = runBlocking {
        createVersion2(birthDate = null)
        val db = AppDatabase.build(context)
        try {
            assertEquals(listOf(Child(1, Child.DEFAULT_NAME, null)), RoomDiaryRepository(db).allChildren())
        } finally {
            db.close()
        }
    }

    private companion object {
        /** Имя файла из [AppDatabase.build]: тест открывает ту же базу, что приложение. */
        const val DB_NAME = "babylog.db"
        const val BIRTH = 1_779_235_200_000L // 2026-05-20
        const val T1 = 1_786_000_000_000L
        const val T2 = 1_786_003_600_000L
        const val T3 = 1_786_008_000_000L
        const val T4 = 1_786_020_000_000L
    }
}
