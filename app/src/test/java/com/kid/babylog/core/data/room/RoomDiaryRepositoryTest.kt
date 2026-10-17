package com.kid.babylog.core.data.room

import androidx.test.platform.app.InstrumentationRegistry
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.ChildPhoto
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val db = AppDatabase.build(context)
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

    private companion object {
        const val T1 = 1_786_000_000_000L
        const val T2 = 1_786_003_600_000L
    }
}
