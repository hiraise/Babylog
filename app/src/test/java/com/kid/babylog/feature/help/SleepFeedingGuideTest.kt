package com.kid.babylog.feature.help

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
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
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeTipsSource
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
 * Гайд темы «sleep-feeding» проходит на примере дневника от первого шага до «Готово» (ADR-0043): каждое
 * нажатие — по настоящему экрану дневника, записи появляются в примере, часы примера перематываются.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class SleepFeedingGuideTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val example = FakeDiaryRepository(children = emptyList())
    private val completed = mutableListOf<String>()
    private lateinit var systemZone: TimeZone

    @Before
    fun showGuide() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val sessions = GuideSessions { topic ->
            GuideSandbox(topic, example, FakeTipsSource(), FakeDocumentStore(), clock, io = Dispatchers.Unconfined)
        }
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                HelpHost(
                    route = HelpRoute.Guide("sleep-feeding"),
                    onRoute = {},
                    topics = HELP_TOPICS,
                    progress = HelpProgress(),
                    onTopicOpened = {},
                    onTopicCompleted = { completed += it.id },
                    sessions = sessions,
                    theme = ThemeChoice(),
                    onThemeChange = {},
                    shadeEnabled = false,
                    onShadeChange = {},
                )
            }
        }
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `guide passes from the first step to done`() {
        tile("начать сон").performClick()
        tap("Запустить таймер")
        tap("Прошло 40 минут")
        tile("проснулась").performClick()
        tap("Понятно")
        val sleep = newest<Sleep>()
        assertEquals(40 * MINUTE, sleep.end!! - sleep.start)

        tile("начать кормление").performClick()
        tap("Левая грудь")
        tap("Запустить таймер")
        compose.onNodeWithContentDescription("Сменить грудь").performClick()
        tap("Прошло 15 минут")
        tile("закончить кормление").performClick()
        assertTrue(example.events.value.none { it is Feeding && it.isActive })

        tile("начать кормление").performClick()
        tap("Бутылочка")
        tap("Сохранить")

        compose.onNodeWithText("Готово: тема «Сон и кормление» пройдена").assertIsDisplayed()
        assertEquals(listOf("sleep-feeding"), completed)
        assertEquals(clock.millis() + 55 * MINUTE, newest<Bottle>().start)
    }

    /** Плитка таймера по тому, что сделает касание: «начать сон», «проснулась». */
    private fun tile(clickLabel: String) = compose.onNode(
        SemanticsMatcher("касание — «$clickLabel»") { it.config.getOrNull(SemanticsActions.OnClick)?.label == clickLabel },
    )

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        compose.waitForIdle()
    }

    private inline fun <reified T : BabyEvent> newest(): T = example.events.value.filterIsInstance<T>().maxBy { it.start }

    private companion object {
        const val MINUTE = 60_000L
    }
}
