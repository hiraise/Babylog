package com.kid.babylog.feature.illness

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.feature.diary.DiaryScreen
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.feature.diary.MainSection
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import com.kid.babylog.testing.testDevelopment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
 * Болезнь на главном экране (ADR-0040): «Здоровье» → «Заболела» → «Начать» — карточка на «Сегодня»;
 * касание карточки — экран болезни, «Назад» возвращает; «Выздоровела» там закрывает болезнь, и карточка
 * пропадает. Температура со «Заболела» начинает болезнь с замера (ADR-0041).
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class IllnessFlowTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-10T06:55:00Z"), ZoneOffset.UTC)
    private lateinit var systemZone: TimeZone
    private val repository = FakeDiaryRepository(listOf(Child(1, "Маша", null, Sex.GIRL)))

    @Before
    fun fixTimeZone() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    private fun show() {
        val viewModel = DiaryViewModel(repository, FakeSelectedChildStore(), FakeDocumentStore(), clock)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, development = testDevelopment(), initialSection = MainSection.TODAY)
            }
        }
    }

    private fun illness() = repository.events.value.filterIsInstance<Illness>().singleOrNull()

    @Test
    fun `illness starts from the health tile and ends on its screen`() {
        show()
        compose.onNode(hasText("Здоровье") and hasClickAction()).performClick()
        compose.onNodeWithText("Заболела").performClick()
        compose.onNodeWithText("ОРВИ").performClick()
        compose.onNodeWithText("Начать").performClick()
        compose.waitForIdle()

        assertEquals(listOf(IllnessName("ОРВИ")), illness()?.names)
        compose.onNodeWithText("Болеет · 1-й день").assertIsDisplayed()

        compose.onNodeWithText("Болеет · 1-й день").performClick()
        compose.onNodeWithText("Для врача").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText("Болеет · 1-й день").assertIsDisplayed()

        compose.onNodeWithText("Болеет · 1-й день").performClick()
        compose.onNode(hasText("Выздоровела") and hasClickAction()).performClick()
        // Лист «Выздоровела»: сегодня по умолчанию, основная кнопка — тем же словом
        compose.onNodeWithText("Болела 1 день · 10 окт.").assertIsDisplayed()
        confirmSheet("Выздоровела")
        compose.waitForIdle()

        assertNotNull(illness()?.end)
        Espresso.pressBack()
        compose.onNodeWithText("Болеет · 1-й день").assertDoesNotExist()
    }

    @Test
    fun `temperature with the switch starts the illness from the measurement`() {
        show()
        compose.onNode(hasText("Здоровье") and hasClickAction()).performClick()
        compose.onNodeWithText("Температура").performClick()
        compose.onNode(hasSetTextAction() and hasContentDescription("Температура")).performTextReplacement("38,4")
        compose.onNodeWithText("Заболела").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitForIdle()

        val temperature = repository.events.value.filterIsInstance<Temperature>().single()
        assertEquals(384, temperature.tenths)
        assertEquals(temperature.start, illness()?.start)
        compose.onNodeWithText("Болеет · 1-й день").assertIsDisplayed()
    }

    @Test
    fun `no illness without the switch`() {
        show()
        compose.onNode(hasText("Здоровье") and hasClickAction()).performClick()
        compose.onNodeWithText("Температура").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitForIdle()

        assertEquals(366, repository.events.value.filterIsInstance<Temperature>().single().tenths)
        assertNull(illness())
    }

    /** Основная кнопка открытого листа — последняя из кнопок с такой подписью: над ним — заголовок с тем же словом. */
    private fun confirmSheet(label: String) {
        compose.onAllNodes(hasText(label) and hasClickAction()).let { nodes ->
            val count = nodes.fetchSemanticsNodes().size
            nodes[count - 1].performClick()
        }
    }
}
