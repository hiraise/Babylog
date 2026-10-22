package com.kid.babylog.feature.help

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kid.babylog.GuideSandbox
import com.kid.babylog.GuideSessions
import com.kid.babylog.HelpHost
import com.kid.babylog.HelpRoute
import com.kid.babylog.core.data.HelpProgress
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.feature.diary.DiaryScreen
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import com.kid.babylog.testing.FakeTipsSource
import com.kid.babylog.testing.testDevelopment
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Справка (ADR-0043): ⋮ → «Справка» → тема → пример и обратно; в примере нет переноса данных. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class HelpNavigationTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `help opens from the menu, a topic opens its example and closes back`() {
        val example = FakeDiaryRepository(children = emptyList())
        val sessions = GuideSessions { GuideSandbox(it, example, FakeTipsSource(), FakeDocumentStore(), clock, io = Dispatchers.Unconfined) }
        val opened = mutableListOf<String>()
        var route by mutableStateOf<HelpRoute?>(null)
        val diary = DiaryViewModel(FakeDiaryRepository(), FakeSelectedChildStore(), FakeDocumentStore(), clock)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                when (val current = route) {
                    null -> DiaryScreen(
                        diary,
                        ThemeChoice(),
                        onThemeChange = {},
                        development = testDevelopment(),
                        onOpenHelp = { route = HelpRoute.Topics },
                    )
                    else -> HelpHost(
                        route = current,
                        onRoute = { route = it },
                        topics = HELP_TOPICS,
                        progress = HelpProgress(),
                        onTopicOpened = { opened += it.id },
                        onTopicCompleted = {},
                        sessions = sessions,
                        theme = ThemeChoice(),
                        onThemeChange = {},
                        shadeEnabled = false,
                        onShadeChange = {},
                    )
                }
            }
        }

        compose.onNodeWithContentDescription("Меню").performClick()
        tap("Справка")
        assertEquals(HelpRoute.Topics, route)

        compose.onNode(hasText("Сон и кормление") and hasClickAction()).performClick()
        compose.onNodeWithText("Попробовать на примере").assertIsDisplayed()
        assertEquals(listOf("sleep-feeding"), opened)

        tap("Попробовать на примере")
        compose.onNodeWithText("Пример · ничего не сохранится").assertIsDisplayed()
        compose.onNodeWithText("Дневник Сони").assertExists()

        compose.onNodeWithContentDescription("Закрыть пример").performClick()
        assertEquals(HelpRoute.Topic("sleep-feeding"), route)
        compose.onNodeWithContentDescription("Назад").performClick()
        compose.onNodeWithContentDescription("Назад").performClick()
        assertNull(route)
        assertTrue(example.events.value.isNotEmpty())
    }

    @Test
    fun `example diary has no data transfer and no help in the menu`() {
        val diary = DiaryViewModel(FakeDiaryRepository(), FakeSelectedChildStore(), FakeDocumentStore(), clock)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                DiaryScreen(diary, ThemeChoice(), onThemeChange = {}, development = testDevelopment(), onOpenHelp = {}, example = true)
            }
        }
        compose.onNodeWithContentDescription("Меню").performClick()
        compose.onNodeWithText("Тема").assertIsDisplayed()
        compose.onNodeWithText("Экспорт в файл").assertDoesNotExist()
        compose.onNodeWithText("Импорт из файла").assertDoesNotExist()
        compose.onNodeWithText("Справка").assertDoesNotExist()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }
}
