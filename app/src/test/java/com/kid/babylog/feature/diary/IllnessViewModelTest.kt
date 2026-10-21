package com.kid.babylog.feature.diary

import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.TemperatureMethod
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Болезнь и температура на главном экране (ADR-0040, ADR-0041): начать, закончить, вернуть, названия. */
@OptIn(ExperimentalCoroutinesApi::class)
class IllnessViewModelTest {

    private val now = Instant.parse("2026-10-10T03:10:00Z").toEpochMilli()
    private val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
    private val repo = FakeDiaryRepository(listOf(Child(1, "Маша", null)))

    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(): DiaryViewModel {
        val vm = DiaryViewModel(repo, FakeSelectedChildStore(), FakeDocumentStore(), clock)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.state.collect {} }
        return vm
    }

    private fun illnesses() = repo.events.value.filterIsInstance<Illness>()

    @Test
    fun `illness starts with names from its start and a temperature now`() = runTest {
        repo.events.value = listOf(Temperature(id = 50, childId = 1, start = now - 86_400_000, tenths = 366, method = TemperatureMethod.EAR))
        val vm = viewModel()
        vm.startIllness(start = now - 3_600_000, names = listOf("ОРВИ"), note = "из сада", temperatureTenths = 384)
        val illness = vm.state.value.activeIllness!!
        assertEquals(now - 3_600_000, illness.start)
        assertEquals(listOf(IllnessName("ОРВИ")), illness.names)
        assertEquals("из сада", illness.note)
        val temperature = vm.state.value.lastTemperature!!
        assertEquals(384, temperature.tenths)
        assertEquals(now, temperature.start)
        // Место — как в прошлый раз: тем же градусником
        assertEquals(TemperatureMethod.EAR, temperature.method)
    }

    @Test
    fun `second illness does not start while one goes on`() = runTest {
        val vm = viewModel()
        vm.startIllness(now - 1_000, emptyList(), "", null)
        vm.startIllness(now, listOf("отит"), "", 390)
        assertEquals(1, illnesses().size)
        // Замер второго листа не сохраняется: его лист не начал болезнь
        assertNull(vm.state.value.lastTemperature)
    }

    @Test
    fun `temperature switch starts illness from the measurement`() = runTest {
        val vm = viewModel()
        vm.saveTemperature(Temperature(childId = 1, start = now - 60_000, tenths = 384), startIllness = true)
        assertEquals(now - 60_000, vm.state.value.activeIllness?.start)
        assertEquals(384, vm.state.value.lastTemperature?.tenths)
    }

    @Test
    fun `recovered illness ends and may come back`() = runTest {
        val vm = viewModel()
        vm.startIllness(now - 5 * 86_400_000L, emptyList(), "", null)
        val illness = vm.state.value.activeIllness!!
        vm.recoverIllness(illness, end = now - 86_400_000)
        assertNull(vm.state.value.activeIllness)
        assertEquals(now - 86_400_000, illnesses().single().end)
        vm.resumeIllness(illness)
        assertEquals(illness.id, vm.state.value.activeIllness?.id)
    }

    @Test
    fun `end before start is moved to the start`() = runTest {
        val vm = viewModel()
        vm.startIllness(now - 1_000, emptyList(), "", null)
        vm.recoverIllness(vm.state.value.activeIllness!!, end = now - 10_000)
        assertEquals(now - 1_000, illnesses().single().end)
    }

    @Test
    fun `names are added, changed and removed on the same illness`() = runTest {
        val vm = viewModel()
        vm.startIllness(now - 3 * 86_400_000L, listOf("ОРВИ"), "", null)
        val illness = vm.state.value.activeIllness!!
        val otitis = IllnessName("Отит", LocalDate.of(2026, 10, 10))
        vm.saveIllnessName(illness, old = null, name = otitis)
        // Повтор без учёта регистра не добавляется
        vm.saveIllnessName(vm.state.value.activeIllness!!, old = null, name = IllnessName("орви"))
        assertEquals(listOf(IllnessName("ОРВИ"), otitis), vm.state.value.activeIllness!!.names)
        vm.saveIllnessName(vm.state.value.activeIllness!!, old = otitis, name = otitis.copy(since = LocalDate.of(2026, 10, 9)))
        vm.removeIllnessName(vm.state.value.activeIllness!!, IllnessName("ОРВИ"))
        assertEquals(listOf(IllnessName("Отит", LocalDate.of(2026, 10, 9))), vm.state.value.activeIllness!!.names)
    }
}
