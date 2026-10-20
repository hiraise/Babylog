package com.kid.babylog.feature.development

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.feature.diary.DiaryScreen
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.feature.diary.MainSection
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDevelopmentSeenStore
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import com.kid.babylog.testing.testDevelopment
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Касания нижней панели и «Развития» (ADR-0035, ADR-0037): панель переключает разделы, «Назад» с любого
 * раздела ведёт на «Сегодня», а с экрана навыков — к обзору раздела; полоса советов открывает «Развитие»,
 * и точка «новые» гаснет; без даты рождения раздел предлагает её указать.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class DevelopmentFlowTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-18T08:20:00Z"), ZoneOffset.UTC)
    private val birth = LocalDate.of(2026, 5, 20)
    private val seen = FakeDevelopmentSeenStore()
    private lateinit var systemZone: TimeZone

    @Before
    fun utc() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    private fun show(section: MainSection, birthDate: LocalDate? = birth) {
        val millis = birthDate?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val repository = FakeDiaryRepository(listOf(Child(1, "Маша", millis, Sex.GIRL)))
        val viewModel = DiaryViewModel(repository, FakeSelectedChildStore(), FakeDocumentStore(), clock)
        val development = testDevelopment(AssetTipsSource(RuntimeEnvironment.getApplication()), seen)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {}, development = development, initialSection = section)
            }
        }
    }

    @Test
    fun `tips banner opens development and the new mark goes out`() {
        show(MainSection.TODAY)
        compose.onNodeWithText("Советы на 22-ю неделю").assertIsDisplayed()
        compose.onNodeWithText("новые").assertIsDisplayed()

        compose.onNodeWithText("Советы на 22-ю неделю").performClick()
        compose.onNodeWithText("22-я неделя").assertIsDisplayed()
        compose.onNodeWithText("Советы недели").assertIsDisplayed()
        assertEquals(mapOf(1L to 22), seen.seenWeeks.value)

        Espresso.pressBack()
        compose.onNodeWithText("Советы на 22-ю неделю").assertIsDisplayed()
        compose.onNodeWithText("новые").assertDoesNotExist()
    }

    @Test
    fun `bottom bar switches sections and back returns to today`() {
        show(MainSection.TODAY)
        compose.onNode(hasText("Дневник") and hasClickAction()).performClick()
        compose.onNodeWithText("По дням").assertIsDisplayed()
        compose.onNodeWithText("Заметки").performClick()
        compose.onNodeWithText("Заметка").assertIsDisplayed()

        compose.onNode(hasText("Аналитика") and hasClickAction()).performClick()
        compose.onNodeWithText("7 дней").assertIsDisplayed()

        Espresso.pressBack()
        compose.onNodeWithText("Подгузник").assertIsDisplayed()
        compose.onNodeWithText("По дням").assertDoesNotExist()
    }

    @Test
    fun `skills open inside the section and back returns to its overview`() {
        show(MainSection.DEVELOPMENT)
        compose.onNodeWithText("подробнее").performClick()
        compose.onNodeWithContentDescription("Назад к разделу").assertIsDisplayed()
        compose.onNodeWithText("Сидит без поддержки").assertIsDisplayed()

        Espresso.pressBack()
        compose.onNodeWithText("Советы недели").assertIsDisplayed()
        compose.onNodeWithContentDescription("Назад к разделу").assertDoesNotExist()
    }

    @Test
    fun `tip opens with its sources`() {
        show(MainSection.DEVELOPMENT)
        val data = DevelopmentData(AssetTipsSource(RuntimeEnvironment.getApplication()).tips(), AssetTipsSource(RuntimeEnvironment.getApplication()).milestones())
        val first = developmentWeek(data, birth, LocalDate.of(2026, 10, 18))!!.tips.first()
        compose.onNode(hasText(first.title) and hasClickAction()).performClick()
        compose.onNodeWithText("Источники").assertIsDisplayed()
        compose.onNodeWithText(first.sources.first().name).assertIsDisplayed()
    }

    @Test
    fun `without a birth date development asks for it`() {
        show(MainSection.DEVELOPMENT, birthDate = null)
        compose.onNodeWithText("Указать дату рождения").assertIsDisplayed()
        compose.onNodeWithText("Советы недели").assertDoesNotExist()
    }
}
