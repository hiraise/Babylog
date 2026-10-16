package com.kid.babylog.feature.diary

import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.RoborazziOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Medicine
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Главный экран на эталонных скриншотах вместо ручной сверки на эмуляторе (ADR-0013).
 * Эталоны — `app/src/test/screenshots/`; обновить их — `scripts/dev.sh goldens`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class DiaryScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var systemZone: TimeZone

    // Экран показывает время в поясе устройства: фиксируем его, иначе Mac и CI нарисуют разное
    @Before
    fun fixTimeZone() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `day with records, light`() = capture("diary_day_light", sampleDiary(), dark = false)

    @Test
    fun `day with records, dark`() = capture("diary_day_dark", sampleDiary(), dark = true)

    @Test
    fun `first launch`() = capture("diary_first_launch", FakeDiaryRepository(), dark = false)

    private fun capture(name: String, repository: FakeDiaryRepository, dark: Boolean) {
        val viewModel = DiaryViewModel(repository, FakeSelectedChildStore(), FakeDocumentStore(), clock)
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = OPTIONS) {
            BabyLogTheme(clock = clock, darkTheme = dark) {
                DiaryScreen(viewModel)
            }
        }
    }

    /** Двое детей; у Маши за сегодня записи всех типов и идёт сон. */
    private fun sampleDiary() = FakeDiaryRepository(
        listOf(Child(1, "Маша", at("2026-05-20T00:00:00Z")), Child(2, "Петя", null)),
    ).apply {
        events.value = listOf<BabyEvent>(
            Weight(id = 1, childId = 1, start = at("2026-10-15T06:10:00Z"), grams = 6350),
            Feeding(id = 2, childId = 1, start = at("2026-10-15T06:30:00Z"), breast = Breast.LEFT),
            Diaper(id = 3, childId = 1, start = at("2026-10-15T07:05:00Z"), kind = DiaperKind.MIXED),
            Medicine(id = 4, childId = 1, start = at("2026-10-15T07:20:00Z"), name = "Витамин D", note = "1 капля"),
            Sleep(id = 5, childId = 1, start = at("2026-10-15T07:40:00Z"), end = at("2026-10-15T08:55:00Z")),
            Feeding(id = 6, childId = 1, start = at("2026-10-15T09:00:00Z"), breast = Breast.RIGHT, note = "долго"),
            Sleep(id = 7, childId = 1, start = at("2026-10-15T09:20:00Z")),
        )
    }

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    private companion object {
        // Mac (эталоны) и Linux (CI) сглаживают шрифты чуть по-разному: допускаем 1 % отличающихся пикселей
        val OPTIONS = RoborazziOptions(
            compareOptions = RoborazziOptions.CompareOptions(changeThreshold = 0.01f),
        )
    }
}
