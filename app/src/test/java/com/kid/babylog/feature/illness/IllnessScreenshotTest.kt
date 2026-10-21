package com.kid.babylog.feature.illness

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.TemperatureMethod
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.analytics.OVERVIEW_PIXEL_5
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.feature.diary.DiaryScreen
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.feature.diary.MainSection
import com.kid.babylog.feature.notes.NotesContent
import com.kid.babylog.feature.notes.NotesFilter
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import com.kid.babylog.testing.SCREENSHOT_OPTIONS
import com.kid.babylog.testing.testDevelopment
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Болезнь (ADR-0040) на эталонных скриншотах: карточка на «Сегодня» ночью 4-го дня, экран болезни
 * целиком в светлой и тёмной теме и «Болезни» в «Заметках». Обновить — `scripts/dev.sh goldens`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IllnessScreenshotTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private lateinit var systemZone: TimeZone

    // Время и сутки — в поясе устройства: фиксируем его, иначе Mac и CI нарисуют разное
    @Before
    fun fixTimeZone() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private val hour = 3_600_000L
    private val masha = Child(1, "Маша", at("2026-01-01T00:00:00Z"), Sex.GIRL)
    private val illness = Illness(
        id = 1, childId = 1, start = at("2026-10-07T19:30:00Z"),
        names = listOf(IllnessName("ОРВИ"), IllnessName("Отит", LocalDate.of(2026, 10, 10))),
    )

    /**
     * История Маши: две недели режима до болезни, ОРВИ с 7 октября, ночью на 4-й день 39,2 и ибупрофен,
     * днём врач нашёл отит — антибиотик. [until] — что уже случилось к этому времени.
     */
    private fun diary(until: Long): List<BabyEvent> = buildList {
        add(illness)
        add(Illness(id = 2, childId = 1, start = at("2026-09-02T09:00:00Z"), end = at("2026-09-06T20:00:00Z"), names = listOf(IllnessName("простуда"))))
        add(Weight(id = 3, childId = 1, start = at("2026-10-02T09:00:00Z"), grams = 8900))
        var id = 100L
        (0..17).forEach { back ->
            val day = at("2026-10-10T00:00:00Z") - back * 24 * hour
            val sick = back in 1..3
            repeat(if (sick) 11 else 8) {
                val start = day + it * (if (sick) 2 else 3) * hour
                add(Feeding(id = id++, childId = 1, start = start, end = start + 15 * 60_000, breast = if (it % 2 == 0) Breast.LEFT else Breast.RIGHT))
            }
            repeat(if (sick) 5 else 7) { add(Diaper(id = id++, childId = 1, start = day + it * 3 * hour + hour, kind = DiaperKind.WET)) }
            add(Sleep(id = id++, childId = 1, start = day + 12 * hour, end = day + (if (sick) 22 else 24) * hour))
        }
        add(Sleep(id = id++, childId = 1, start = at("2026-10-10T03:30:00Z"), end = at("2026-10-10T06:00:00Z")))
        listOf(
            "2026-10-07T19:30:00Z" to 384, "2026-10-07T23:00:00Z" to 381, "2026-10-08T07:00:00Z" to 386,
            "2026-10-08T20:00:00Z" to 382, "2026-10-09T08:00:00Z" to 376, "2026-10-09T15:20:00Z" to 380,
            "2026-10-10T03:10:00Z" to 392, "2026-10-10T07:30:00Z" to 381, "2026-10-10T10:40:00Z" to 388,
        ).forEach { (time, tenths) -> add(Temperature(id = id++, childId = 1, start = at(time), tenths = tenths, method = TemperatureMethod.ARMPIT)) }
        listOf("2026-10-07T19:40:00Z", "2026-10-08T07:10:00Z", "2026-10-08T20:10:00Z", "2026-10-09T21:10:00Z", "2026-10-10T03:15:00Z", "2026-10-10T10:45:00Z")
            .forEach { add(Medicine(id = id++, childId = 1, start = at(it), name = "Ибупрофен", note = "4 мл")) }
        listOf("2026-10-08T14:00:00Z", "2026-10-09T15:30:00Z").forEach { add(Medicine(id = id++, childId = 1, start = at(it), name = "Парацетамол", note = "5 мл")) }
        add(Medicine(id = id++, childId = 1, start = at("2026-10-10T12:00:00Z"), name = "Амоксициллин", note = "5 мл"))
        add(Note(id = id++, childId = 1, start = at("2026-10-08T12:10:00Z"), tags = listOf("врач"), note = "Врач: ОРВИ, горло красное"))
        add(Note(id = id++, childId = 1, start = at("2026-10-10T03:30:00Z"), note = "Трёт левое ухо, плачет лёжа"))
        add(Note(id = id++, childId = 1, start = at("2026-10-10T11:30:00Z"), tags = listOf("врач"), note = "Врач: отит слева, амоксициллин 7 дней"))
    }.filter { it.start <= until }

    @Test
    @Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
    fun `today with illness card`() {
        val now = at("2026-10-10T06:55:00Z")
        val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
        val repository = FakeDiaryRepository(listOf(masha)).apply { events.value = diary(now) }
        val viewModel = DiaryViewModel(repository, FakeSelectedChildStore(), FakeDocumentStore(), clock)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, development = testDevelopment(), initialSection = MainSection.TODAY)
            }
        }
        capture("diary_illness")
    }

    @Test
    @Config(qualifiers = TALL_PIXEL_5)
    fun `illness screen, light`() = captureScreen("illness_screen", dark = false)

    @Test
    @Config(qualifiers = TALL_PIXEL_5)
    fun `illness screen, dark`() = captureScreen("illness_screen_dark", dark = true)

    @Test
    @Config(qualifiers = OVERVIEW_PIXEL_5)
    fun `illnesses in notes`() {
        val now = at("2026-10-20T10:00:00Z")
        val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
        val events = diary(now).map { if (it is Illness && it.id == 1L) it.copy(end = at("2026-10-14T20:00:00Z")) else it } +
            Illness(id = 50, childId = 1, start = at("2026-10-18T08:00:00Z"))
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    NotesContent(
                        events = events, sex = Sex.GIRL, now = now, photoCounts = emptyMap(),
                        filter = NotesFilter.Illnesses, onFilter = {}, cryingWeek = null, onOpenAnalytics = {},
                        onAddNote = {}, onEdit = {}, onDelete = {},
                    )
                }
            }
        }
        capture("notes_illnesses")
    }

    private fun captureScreen(name: String, dark: Boolean) {
        val now = at("2026-10-10T12:30:00Z")
        val clock = Clock.fixed(Instant.ofEpochMilli(now), ZoneOffset.UTC)
        val model = illnessScreenModel(diary(now), illness, now, ZoneOffset.UTC)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = dark) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    IllnessContent(model, Sex.GIRL, now, IllnessActions({}, {}, {}, {}, {}, null, {}, {}, {}, {}))
                }
            }
        }
        capture(name)
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = SCREENSHOT_OPTIONS)
}
