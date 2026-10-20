package com.kid.babylog.feature.development

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.feature.diary.DiaryScreen
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.feature.diary.MainSection
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import com.kid.babylog.testing.SCREENSHOT_OPTIONS
import com.kid.babylog.testing.testDevelopment
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * «Развитие» на настоящих советах (ADR-0037): обзор недели в светлой и тёмной теме, экран навыков,
 * совет с расхождением источников и полоса советов на «Сегодня». Маше 151 день — 22-я неделя.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = TALL_PIXEL_5)
class DevelopmentScreenshotTest {

    private val clock = Clock.fixed(Instant.parse("2026-10-18T08:20:00Z"), ZoneOffset.UTC)
    private val today = LocalDate.of(2026, 10, 18)
    private val masha = Child(1, "Маша", Instant.parse("2026-05-20T00:00:00Z").toEpochMilli(), Sex.GIRL)
    private val source get() = AssetTipsSource(RuntimeEnvironment.getApplication())
    private lateinit var systemZone: TimeZone

    @Before
    fun utc() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun overview() = screen("development_overview", MainSection.DEVELOPMENT, dark = false)

    @Test
    fun `overview dark`() = screen("development_overview_dark", MainSection.DEVELOPMENT, dark = true)

    @Test
    fun `today with tips banner`() = screen("diary_today_tips", MainSection.TODAY, dark = false)

    @Test
    fun skills() {
        val data = DevelopmentData(source.tips(), source.milestones())
        captureRoboImage("src/test/screenshots/development_skills.png", roborazziOptions = SCREENSHOT_OPTIONS) {
            BabyLogTheme(clock = clock, darkTheme = false) {
                Box(Modifier.background(MaterialTheme.colorScheme.background)) {
                    DevelopmentContent(masha, data, today, onSetBirthDate = {}, initialSkills = true)
                }
            }
        }
    }

    @Test
    fun `tip with disagreeing sources`() {
        val tip = source.tips().first { it.id == "m3-solids-timing" }
        captureRoboImage("src/test/screenshots/development_tip.png", roborazziOptions = SCREENSHOT_OPTIONS) {
            BabyLogTheme(clock = clock, darkTheme = false) {
                Box(Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(BabyLogTheme.spacing.l)) {
                    TipDetails(tip)
                }
            }
        }
    }

    private fun screen(name: String, section: MainSection, dark: Boolean) {
        val viewModel = DiaryViewModel(FakeDiaryRepository(listOf(masha)), FakeSelectedChildStore(), FakeDocumentStore(), clock)
        val development = testDevelopment(source)
        captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = SCREENSHOT_OPTIONS) {
            BabyLogTheme(clock = clock, darkTheme = dark) {
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, development = development, initialSection = section)
            }
        }
    }
}
