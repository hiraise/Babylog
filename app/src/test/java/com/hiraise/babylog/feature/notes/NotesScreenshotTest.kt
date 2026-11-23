package com.hiraise.babylog.feature.notes

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.hiraise.babylog.core.data.ThemeChoice
import com.hiraise.babylog.core.ui.theme.BabyLogTheme
import com.hiraise.babylog.feature.analytics.OVERVIEW_PIXEL_5
import com.hiraise.babylog.feature.analytics.sampleRepository
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

/**
 * Вкладка «Заметки» (ADR-0031) на эталонных скриншотах: лента плача и заметок с карточкой плача
 * за неделю — в светлой и тёмной теме, и сводка метки «колики». Обновить — `scripts/dev.sh goldens`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = OVERVIEW_PIXEL_5)
class NotesScreenshotTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

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
    fun `feed, light`() {
        show(dark = false)
        capture("notes_feed")
    }

    @Test
    fun `feed, dark`() {
        show(dark = true)
        capture("notes_dark")
    }

    @Test
    fun `tag summary`() {
        show(dark = false)
        compose.onNode(hasText("колики") and isSelectable()).performClick()
        capture("notes_tag")
    }

    private fun show(dark: Boolean) {
        val viewModel = DiaryViewModel(sampleRepository(clock), FakeSelectedChildStore(), FakeDocumentStore(), clock)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = dark) {
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, development = testDevelopment(), initialSection = MainSection.DIARY, initialDiaryView = DiaryView.NOTES)
            }
        }
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = SCREENSHOT_OPTIONS)
}
