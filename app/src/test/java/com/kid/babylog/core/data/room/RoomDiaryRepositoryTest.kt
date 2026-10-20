package com.kid.babylog.core.data.room

import androidx.test.platform.app.InstrumentationRegistry
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.EventPhoto
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.PhotoEdit
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.testing.FakeRecoveryNotices
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Правила ADR-0008 на настоящей базе Room, а не на фейке. */
@RunWith(RobolectricTestRunner::class)
class RoomDiaryRepositoryTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** Открывает базу приложения, как после запуска, и закрывает её после [block]. */
    private fun <T> withRepository(block: suspend (RoomDiaryRepository) -> T): T = runBlocking {
        val db = AppDatabase.build(context, FakeRecoveryNotices(), Runnable::run, System::currentTimeMillis)
        try {
            block(RoomDiaryRepository(db))
        } finally {
            db.close()
        }
    }

    @Test
    fun `new database starts with one default child`() = withRepository { repo ->
        assertEquals(listOf(Child(1, Child.DEFAULT_NAME, null)), repo.allChildren())
    }

    @Test
    fun `active sleep survives restart and stays the only one`() {
        withRepository { repo ->
            assertTrue(repo.startSleep(childId = 1, start = T1))
            assertFalse(repo.startSleep(childId = 1, start = T2))
        }
        // Новый экземпляр базы — как после перезапуска приложения или телефона
        withRepository { repo ->
            assertEquals(listOf(Sleep(id = 1, childId = 1, start = T1)), repo.allEvents())
        }
    }

    @Test
    fun `active feeding is the only one and sleep ends it`() = withRepository { repo ->
        assertTrue(repo.startFeeding(childId = 1, start = T1, breast = Breast.LEFT))
        assertFalse(repo.startFeeding(childId = 1, start = T2, breast = Breast.RIGHT))
        // Сон и кормление не идут одновременно (ADR-0020): начало сна заканчивает кормление
        assertTrue(repo.startSleep(childId = 1, start = T2))
        assertEquals(
            listOf(Feeding(id = 1, childId = 1, start = T1, end = T2, breast = Breast.LEFT), Sleep(id = 2, childId = 1, start = T2)),
            repo.allEvents(),
        )
        // …и наоборот; конец не раньше начала, даже если новое «было» раньше
        assertTrue(repo.startFeeding(childId = 1, start = T1, breast = Breast.RIGHT))
        assertEquals(Sleep(id = 2, childId = 1, start = T2, end = T2), repo.allEvents().last { it is Sleep })
    }

    @Test
    fun `crying wakes the baby and falling asleep ends crying`() = withRepository { repo ->
        assertTrue(repo.startSleep(childId = 1, start = T1))
        // Плачет — значит, не спит (ADR-0030): начало плача заканчивает сон, второй плач не начинается
        assertNotNull(repo.startCrying(childId = 1, start = T2))
        assertNull(repo.startCrying(childId = 1, start = T2 + 60_000))
        assertEquals(
            listOf(Sleep(id = 1, childId = 1, start = T1, end = T2), Crying(id = 2, childId = 1, start = T2)),
            repo.allEvents(),
        )
        // Уснул — значит, успокоился: начало сна заканчивает плач
        assertTrue(repo.startSleep(childId = 1, start = T2 + 600_000))
        assertEquals(Crying(id = 2, childId = 1, start = T2, end = T2 + 600_000), repo.allEvents().single { it is Crying })
    }

    @Test
    fun `time is stored with second precision`() = withRepository { repo ->
        repo.addEvent(Medicine(childId = 1, start = T1 + 750, name = "Витамин D"))
        assertEquals(T1, repo.allEvents().single().start)
    }

    @Test
    fun `deleting a child removes only its records and never the last child`() = withRepository { repo ->
        val second = repo.addChild("Петя", birthDate = null, sex = Sex.BOY, photo = null)
        assertEquals(Child(second, "Петя", null, Sex.BOY), repo.allChildren().last())
        repo.addEvent(Medicine(childId = 1, start = T1, name = "Витамин D"))
        repo.addEvent(Medicine(childId = second, start = T2, name = "Нурофен"))

        assertTrue(repo.deleteChild(second))
        assertEquals(listOf(1L), repo.allChildren().map { it.id })
        assertEquals(listOf(1L), repo.allEvents().map { it.childId })
        assertFalse(repo.deleteChild(1))
    }

    @Test
    fun `child photo is stored, replaced and removed with the child`() = withRepository { repo ->
        val first = ChildPhoto(byteArrayOf(1, 2, 3))
        val id = repo.addChild("Петя", birthDate = null, sex = null, photo = first)
        assertEquals(first, repo.allChildren().last().photo)

        val second = ChildPhoto(byteArrayOf(4, 5))
        repo.updateChild(repo.allChildren().last().copy(photo = second))
        assertEquals(second, repo.allChildren().last().photo)
        repo.updateChild(repo.allChildren().last().copy(photo = null))
        assertEquals(null, repo.allChildren().last().photo)

        assertTrue(repo.deleteChild(id))
        assertEquals(listOf(null), repo.allChildren().map { it.photo })
    }

    @Test
    fun `event photos keep their order, survive editing the record and go with it`() = withRepository { repo ->
        val diaper = Diaper(childId = 1, start = T1, kind = DiaperKind.DIRTY)
        val id = repo.addEvent(diaper)
        repo.setEventPhotos(id, PhotoEdit(keep = emptyList(), add = listOf(byteArrayOf(1), byteArrayOf(2), byteArrayOf(3))))
        val (a, b, c) = repo.observeEventPhotos(id).first()
        assertEquals(listOf(1, 2, 3), listOf(a, b, c).map { it.jpeg.single().toInt() })

        // Убрали второе, переставили, добавили новое — в конец
        repo.setEventPhotos(id, PhotoEdit(keep = listOf(c.id, a.id), add = listOf(byteArrayOf(4))))
        assertEquals(listOf(3, 1, 4), repo.observeEventPhotos(id).first().map { it.jpeg.single().toInt() })

        // Правка записи — UPDATE, а не REPLACE: фото не уходят каскадом
        repo.updateEvent(diaper.copy(id = id, kind = DiaperKind.MIXED))
        assertEquals(mapOf(id to 3), repo.observePhotoCounts(1).first())

        repo.deleteEvent(diaper.copy(id = id))
        assertEquals(emptyList<EventPhoto>(), repo.observeEventPhotos(id).first())
    }

    @Test
    fun `deleting a child removes the photos of its records`() = withRepository { repo ->
        val second = repo.addChild("Петя", birthDate = null, sex = null, photo = null)
        val id = repo.addEvent(Diaper(childId = second, start = T1, kind = DiaperKind.WET))
        repo.setEventPhotos(id, PhotoEdit(keep = emptyList(), add = listOf(byteArrayOf(1))))
        assertEquals(mapOf(id to 1), repo.observePhotoCounts(second).first())

        assertTrue(repo.deleteChild(second))
        assertEquals(emptyList<EventPhoto>(), repo.observeEventPhotos(id).first())
    }

    private companion object {
        const val T1 = 1_786_000_000_000L
        const val T2 = 1_786_003_600_000L
    }

    @Test
    fun `bottle ends the sleep it falls into but not a later one`() = withRepository { repo ->
        assertTrue(repo.startSleep(childId = 1, start = T1))
        repo.addBottle(Bottle(childId = 1, start = T2, volumeMl = 120))
        assertEquals(T2, repo.allEvents().filterIsInstance<Sleep>().single().end)

        // Бутылочку записали задним числом, раньше начала идущего сна, — сон идёт дальше
        assertTrue(repo.startSleep(childId = 1, start = T2 + 60_000))
        repo.addBottle(Bottle(childId = 1, start = T1 + 60_000, volumeMl = 90))
        assertEquals(null, repo.allEvents().filterIsInstance<Sleep>().last().end)
        assertEquals(listOf(120, 90), repo.allEvents().filterIsInstance<Bottle>().sortedBy { it.id }.map { it.volumeMl })
    }
}
