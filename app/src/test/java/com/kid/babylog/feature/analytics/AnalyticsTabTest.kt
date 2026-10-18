package com.kid.babylog.feature.analytics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.diary.DiaryScreen
import com.kid.babylog.feature.diary.DiaryTab
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/** Касания на «Аналитике»: столбик показывает итоги дня, «В историю» и строка ритма открывают день (ADR-0022). */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class AnalyticsTabTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-18T08:20:00Z"), ZoneOffset.UTC)
    private lateinit var systemZone: TimeZone

    @Before
    fun showAnalytics() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val viewModel = DiaryViewModel(sampleRepository(clock), FakeSelectedChildStore(), FakeDocumentStore(), clock)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                DiaryScreen(viewModel, ThemeMode.SYSTEM, onThemeModeChange = {}, initialTab = DiaryTab.ANALYTICS)
            }
        }
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `bar shows the day and opens it in history`() {
        // Первый столбик четверга — на карточке «Кормления»: она первая из графиков
        compose.onAllNodes(isSelectable() and hasContentDescription("чт, 15 окт.:", substring = true)).onFirst().performClick()
        compose.onNodeWithText("чт, 15 окт.: ", substring = true).assertIsDisplayed()

        compose.onNodeWithText("В историю").performClick()
        compose.onNodeWithText("15 октября, четверг").assertIsDisplayed()
    }

    @Test
    fun `rhythm row opens the day in history`() {
        compose.onAllNodes(hasClickAction() and hasContentDescription("пт, 16 окт.: сон", substring = true)).onFirst().performClick()
        compose.onNodeWithText("16 октября, пятница").assertIsDisplayed()
    }
}
