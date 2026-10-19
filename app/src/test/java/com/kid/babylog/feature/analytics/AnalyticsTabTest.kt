package com.kid.babylog.feature.analytics

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.kid.babylog.core.data.ThemeChoice
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

/**
 * Касания на «Аналитике» (ADR-0022, ADR-0029): плитка открывает раздел, «Назад» — стрелка и системная —
 * возвращает к обзору; на экране раздела столбик показывает итоги дня, «В историю» и строка ритма
 * открывают день в «Истории».
 */
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
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, initialTab = DiaryTab.ANALYTICS)
            }
        }
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `tile opens its section and the arrow returns to the overview`() {
        compose.onNodeWithText("Вес").performClick()
        compose.onNodeWithText("Взвешивания").assertIsDisplayed()

        compose.onNodeWithContentDescription("Назад к обзору").performClick()
        compose.onNodeWithText("Подгузники").assertIsDisplayed()
        compose.onNodeWithText("Взвешивания").assertDoesNotExist()
    }

    @Test
    fun `system back closes the section, bottles live inside feedings`() {
        // У бутылочки нет своей плитки: её график — на экране кормлений
        compose.onNodeWithText("Бутылочка").assertDoesNotExist()
        compose.onNodeWithText("Кормления").performClick()
        compose.onNodeWithText("Бутылочка").assertIsDisplayed()

        Espresso.pressBack()
        compose.onNodeWithText("Бутылочка").assertDoesNotExist()
        compose.onNodeWithText("Сон").assertIsDisplayed()
    }

    @Test
    fun `bar shows the day and opens it in history`() {
        compose.onNodeWithText("Кормления").performClick()
        // Первый столбик четверга — на карточке «Кормления»: она первая на экране раздела
        compose.onAllNodes(isSelectable() and hasContentDescription("чт, 15 окт.:", substring = true)).onFirst().performClick()
        compose.onNodeWithText("чт, 15 окт.: ", substring = true).assertIsDisplayed()

        compose.onNodeWithText("В историю").performClick()
        compose.onNodeWithText("15 октября, четверг").assertIsDisplayed()
    }

    @Test
    fun `rhythm row opens the day in history`() {
        compose.onNodeWithText("Ритм дня").performClick()
        compose.onAllNodes(hasClickAction() and hasContentDescription("пт, 16 окт.: сон", substring = true)).onFirst().performClick()
        compose.onNodeWithText("16 октября, пятница").assertIsDisplayed()
    }
}
