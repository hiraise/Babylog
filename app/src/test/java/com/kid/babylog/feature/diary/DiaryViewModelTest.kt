package com.kid.babylog.feature.diary

import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Milk
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelTest {

    private val now = Instant.parse("2026-10-15T10:00:00.750Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val repo = FakeDiaryRepository(listOf(Child(1, "Маша", null), Child(2, "Петя", null)))
    private val selection = FakeSelectedChildStore()
    private val documents = FakeDocumentStore()

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** ViewModel с подписчиком на state: без него `stateIn(WhileSubscribed)` не обновляется. */
    private fun TestScope.viewModel(): DiaryViewModel {
        val vm = DiaryViewModel(repo, selection, documents, clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    @Test
    fun `first child is shown until another is selected`() = runTest {
        val vm = viewModel()
        assertEquals(1L, vm.state.value.child?.id)
        vm.selectChild(2)
        assertEquals(2L, vm.state.value.child?.id)
    }

    @Test
    fun `records belong to the selected child only`() = runTest {
        val vm = viewModel()
        vm.saveEvent(Feeding(childId = 1, start = 1_000, breast = Breast.LEFT))
        vm.saveEvent(Diaper(childId = 2, start = 2_000, kind = DiaperKind.WET))
        assertEquals(listOf(1L), vm.state.value.events.map { it.childId })
        vm.selectChild(2)
        assertTrue(vm.state.value.events.single() is Diaper)
    }

    @Test
    fun `new child is selected and duplicate names are ignored`() = runTest {
        val vm = viewModel()
        vm.addChild(" Вася ", birthDate = null, sex = Sex.BOY)
        assertEquals(Child(3, "Вася", null, Sex.BOY), vm.state.value.child)
        vm.addChild("вася", birthDate = null, sex = null)
        assertEquals(3, vm.state.value.children.size)
    }

    @Test
    fun `deleting the selected child falls back to the first one`() = runTest {
        val vm = viewModel()
        vm.selectChild(2)
        vm.deleteChild(vm.state.value.child!!)
        assertEquals(1L, vm.state.value.child?.id)
        assertEquals("Удалено: Петя", vm.state.value.message)
        vm.deleteChild(vm.state.value.child!!) // последнего удалить нельзя
        assertEquals(1, vm.state.value.children.size)
    }

    @Test
    fun `sleep timer ends at clock time truncated to seconds`() = runTest {
        val vm = viewModel()
        vm.startSleep(now.toEpochMilli() - 3_600_000)
        vm.startSleep(now.toEpochMilli()) // второй активный сон не начинается
        assertEquals(1, vm.state.value.events.size)
        vm.endSleep()
        assertNull(vm.state.value.activeSleep)
        val sleep = vm.state.value.events.single() as com.kid.babylog.core.model.Sleep
        assertEquals(Instant.parse("2026-10-15T10:00:00Z").toEpochMilli(), sleep.end)
    }

    @Test
    fun `switching breast splits the feeding at one second and end stops it`() = runTest {
        val vm = viewModel()
        val start = now.toEpochMilli() - 600_000
        vm.startFeeding(start, Breast.LEFT)
        vm.startFeeding(now.toEpochMilli(), Breast.RIGHT) // второе кормление не начинается
        assertEquals(Breast.LEFT, vm.state.value.activeFeeding?.breast)
        vm.switchBreast()
        val second = Instant.parse("2026-10-15T10:00:00Z").toEpochMilli()
        assertEquals(Breast.RIGHT, vm.state.value.activeFeeding?.breast)
        assertEquals(second, vm.state.value.activeFeeding?.start)
        vm.endFeeding()
        assertNull(vm.state.value.activeFeeding)
        val feedings = vm.state.value.events.filterIsInstance<Feeding>().sortedBy { it.start }
        assertEquals(listOf(Breast.LEFT, Breast.RIGHT), feedings.map { it.breast })
        assertEquals(listOf(second, second), feedings.map { it.end })
        assertEquals(second, feedings[1].start) // правая начата ровно в конце левой: одно кормление в сводке
    }

    @Test
    fun `sleep and feeding never run together`() = runTest {
        val vm = viewModel()
        val start = now.toEpochMilli() - 1_800_000
        vm.startFeeding(start, Breast.LEFT)
        vm.startSleep(start + 600_000)
        assertNull(vm.state.value.activeFeeding)
        // Кормление закончилось в момент начала сна — с точностью до секунды, как хранит репозиторий
        assertEquals(Instant.parse("2026-10-15T09:40:00Z").toEpochMilli(), vm.state.value.events.filterIsInstance<Feeding>().single().end)
        vm.startFeeding(start + 1_200_000, Breast.RIGHT)
        assertNull(vm.state.value.activeSleep)
        assertEquals(Breast.RIGHT, vm.state.value.activeFeeding?.breast)
    }

    @Test
    fun `stopping a timer by tap can be undone`() = runTest {
        val vm = viewModel()
        vm.startSleep(now.toEpochMilli() - 3_600_000)
        vm.endSleep()
        assertEquals("Сон: 1 ч", vm.state.value.message)
        assertTrue(vm.state.value.canUndo)
        vm.consumeMessage(undo = true)
        assertNull(vm.state.value.events.filterIsInstance<Sleep>().single().end)
        assertFalse(vm.state.value.canUndo)
    }

    @Test
    fun `undo does not resume a timer once another one started`() = runTest {
        val vm = viewModel()
        vm.startSleep(now.toEpochMilli() - 3_600_000)
        vm.endSleep()
        vm.startFeeding(now.toEpochMilli(), Breast.LEFT)
        vm.consumeMessage(undo = true)
        assertNull(vm.state.value.activeSleep)
        assertEquals(Breast.LEFT, vm.state.value.activeFeeding?.breast)
    }

    @Test
    fun `export and import report results`() = runTest {
        val vm = viewModel()
        vm.saveEvent(Feeding(childId = 1, start = 1_000, breast = Breast.LEFT))
        vm.exportTo("file")
        assertEquals("Экспортировано записей: 1", vm.state.value.message)
        vm.consumeMessage()
        vm.importFrom("file")
        assertEquals("Импорт: добавлено 0, дубликатов 1", vm.state.value.message)
    }

    @Test
    fun `history days include today even without records`() {
        val today = LocalDate.of(2026, 10, 15)
        val events = listOf(Feeding(childId = 1, start = Instant.parse("2026-10-13T12:00:00Z").toEpochMilli(), breast = Breast.LEFT))
        assertEquals(
            listOf(LocalDate.of(2026, 10, 13), today),
            historyDays(events, today, ZoneId.of("UTC")),
        )
    }

    @Test
    fun `bottle is saved for the selected child and becomes the last feeding and bottle`() = runTest {
        val vm = viewModel()
        vm.addBottle(volumeMl = 120, milk = Milk.FORMULA, time = 5_000)
        val bottle = vm.state.value.events.single() as Bottle
        assertEquals(Triple(1L, 120, Milk.FORMULA), Triple(bottle.childId, bottle.volumeMl, bottle.milk))
        assertEquals(bottle, vm.state.value.lastBottle)
        assertEquals(LastFeeding(5_000, 5_000, breast = null), vm.state.value.lastFeeding)
    }
}
