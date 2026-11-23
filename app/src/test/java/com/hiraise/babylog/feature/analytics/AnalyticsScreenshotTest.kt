package com.hiraise.babylog.feature.analytics

import com.github.takahirom.roborazzi.captureRoboImage
import com.hiraise.babylog.core.data.ThemeChoice
import com.hiraise.babylog.core.model.Child
import com.hiraise.babylog.core.model.Sex
import com.hiraise.babylog.core.ui.theme.BabyLogTheme
import com.hiraise.babylog.feature.diary.DiaryScreen
import com.hiraise.babylog.feature.diary.DiaryView
import com.hiraise.babylog.feature.diary.DiaryViewModel
import com.hiraise.babylog.feature.diary.MainSection
import com.hiraise.babylog.testing.FakeDiaryRepository
import com.hiraise.babylog.testing.FakeDocumentStore
import com.hiraise.babylog.testing.FakeSelectedChildStore
import com.hiraise.babylog.testing.SCREENSHOT_OPTIONS
import com.hiraise.babylog.feature.help.sampleMonths
import com.hiraise.babylog.testing.testDevelopment
import org.junit.After
import org.junit.Before
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

/** Высокий экран Pixel 5: экран раздела виден целиком, а в тестах касаний всё на экране. */
internal const val TALL_PIXEL_5 = "w393dp-h2300dp-normal-long-notround-any-440dpi-keyshidden-nonav"

/** Pixel 5 чуть выше обычного: обзор «Аналитики» помещается целиком, вместе с последним рядом плиток. */
internal const val OVERVIEW_PIXEL_5 = "w393dp-h1000dp-normal-long-notround-any-440dpi-keyshidden-nonav"

/** Обзор «Аналитики» на эталонных скриншотах (ADR-0013, ADR-0029); обновить — `scripts/dev.sh goldens`. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalyticsScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-18T08:20:00Z"), ZoneOffset.UTC)
    private lateinit var systemZone: TimeZone

    // Сутки и время считаются в поясе устройства: фиксируем его, иначе Mac и CI нарисуют разное
    @Before
    fun fixTimeZone() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    @Config(qualifiers = OVERVIEW_PIXEL_5)
    fun `two months of records, light`() = capture("analytics_week", dark = false)

    @Test
    @Config(qualifiers = "w393dp-h851dp-normal-long-notround-any-440dpi-keyshidden-nonav")
    fun `two months of records, dark`() = capture("analytics_dark", dark = true)

    private fun capture(name: String, dark: Boolean) {
        val viewModel = DiaryViewModel(sampleRepository(clock), FakeSelectedChildStore(), FakeDocumentStore(), clock)
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = SCREENSHOT_OPTIONS) {
            BabyLogTheme(clock = clock, darkTheme = dark) {
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, development = testDevelopment(), initialSection = MainSection.ANALYTICS)
            }
        }
    }
}

/** Маша, 4 мес 25 дн.: два месяца режима по [clock], бутылочки, витамин D, вес с рождения. */
internal fun sampleRepository(clock: Clock) = FakeDiaryRepository(
    listOf(Child(1, "Маша", LocalDate.of(2026, 5, 23).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), Sex.GIRL)),
).apply {
    events.value = sampleMonths(childId = 1, today = LocalDate.of(2026, 10, 18), now = clock.millis(), zone = ZoneOffset.UTC)
}
