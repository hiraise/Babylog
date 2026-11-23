package com.hiraise.babylog.feature.analytics

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.hiraise.babylog.core.data.ThemeChoice
import com.hiraise.babylog.core.ui.theme.BabyLogTheme
import com.hiraise.babylog.feature.diary.DiaryScreen
import com.hiraise.babylog.feature.diary.DiaryView
import com.hiraise.babylog.feature.diary.DiaryViewModel
import com.hiraise.babylog.feature.diary.MainSection
import com.hiraise.babylog.testing.ComponentActivityRule
import com.hiraise.babylog.testing.FakeDocumentStore
import com.hiraise.babylog.testing.FakeSelectedChildStore
import com.hiraise.babylog.testing.SCREENSHOT_OPTIONS
import com.hiraise.babylog.testing.testDevelopment
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
import java.time.ZoneOffset
import java.util.TimeZone

/** Экраны плача и меток — выше 1000 dp. */
private const val CRYING_PIXEL_5 = "w393dp-h1400dp-normal-long-notround-any-440dpi-keyshidden-nonav"

/**
 * Экраны разделов «Аналитики» (ADR-0029) на эталонных скриншотах: раздел открывается касанием плитки,
 * как в приложении. Кормления — с графиком бутылочки, сон — ночной и дневной (ADR-0036), вес — со списком
 * взвешиваний, плач — с «Перед плачем» и причинами (ADR-0030), метки — с «Метками и режимом» (ADR-0031).
 * Обновить — `scripts/dev.sh goldens`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = OVERVIEW_PIXEL_5)
class AnalyticsSectionScreenshotTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-18T08:20:00Z"), ZoneOffset.UTC)
    private lateinit var systemZone: TimeZone

    // Сутки и время считаются в поясе устройства: фиксируем его, иначе Mac и CI нарисуют разное
    @Before
    fun showAnalytics() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val viewModel = DiaryViewModel(sampleRepository(clock), FakeSelectedChildStore(), FakeDocumentStore(), clock)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, development = testDevelopment(), initialSection = MainSection.ANALYTICS)
            }
        }
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `feedings with bottles`() = capture(tile = "Кормления", name = "analytics_feedings")

    @Test
    fun `sleep at night and in the day`() = capture(tile = "Сон", name = "analytics_sleep")

    @Test
    fun `weight with weighings`() = capture(tile = "Вес", name = "analytics_weight")

    @Test
    @Config(qualifiers = CRYING_PIXEL_5)
    fun `crying with what was before and the causes`() = capture(tile = "Плач", name = "analytics_crying")

    @Test
    @Config(qualifiers = CRYING_PIXEL_5)
    fun `tags with routine`() = capture(tile = "Метки", name = "analytics_tags")

    private fun capture(tile: String, name: String) {
        compose.onNodeWithText(tile).performClick()
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = SCREENSHOT_OPTIONS)
    }
}
