package com.kid.babylog.feature.help

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.kid.babylog.GuideSandbox
import com.kid.babylog.GuideSessions
import com.kid.babylog.HelpHost
import com.kid.babylog.HelpRoute
import com.kid.babylog.core.data.HelpProgress
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.diary.DiaryScreen
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import com.kid.babylog.testing.FakeTipsSource
import com.kid.babylog.testing.SCREENSHOT_OPTIONS
import com.kid.babylog.testing.testDevelopment
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/** Справка (ADR-0043): список тем, тема, гайд на примере дневника и карточка «Где справка» на «Сегодня». */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel5)
class HelpScreenshotTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val topic = HELP_TOPICS.first()
    private lateinit var systemZone: TimeZone

    @Before
    fun fixTimeZone() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `topics, light`() = captureTopics("help_topics", dark = false)

    /** Экраны справки стоят вне Scaffold дневника: в тёмной теме текст без Surface был бы чёрным. */
    @Test
    fun `topics, dark`() = captureTopics("help_topics_dark", dark = true)

    @Test
    fun `topic, light`() = captureTopic("help_topic", dark = false)

    @Test
    fun `topic, dark`() = captureTopic("help_topic_dark", dark = true)

    @Test
    fun `guide, light`() = captureGuide("help_guide_light", dark = false)

    @Test
    fun `guide, dark`() = captureGuide("help_guide_dark", dark = true)

    @Test
    fun pointer() {
        val diary = DiaryViewModel(FakeDiaryRepository(), FakeSelectedChildStore(), FakeDocumentStore(), clock)
        capture("help_pointer") {
            DiaryScreen(
                diary,
                ThemeChoice(),
                onThemeChange = {},
                development = testDevelopment(),
                onOpenHelp = {},
                helpBadge = true,
                helpPointer = { HelpPointerCard(onOpen = {}, onDismiss = {}) },
            )
        }
    }

    private fun captureTopics(name: String, dark: Boolean) = capture(name, dark) {
        HelpScreen(HELP_TOPICS, HelpProgress(baseline = "1.10"), onOpenTopic = {}, onStartGuide = {}, onBack = {})
    }

    private fun captureTopic(name: String, dark: Boolean) = capture(name, dark) {
        HelpTopicScreen(topic, HelpProgress(baseline = "1.10"), next = null, onTry = {}, onOpenTopic = {}, onBack = {})
    }

    /** Первый шаг гайда: плитка «Сон» обведена, над примером — панель шага. */
    private fun captureGuide(name: String, dark: Boolean) {
        val sessions = GuideSessions {
            GuideSandbox(it, FakeDiaryRepository(children = emptyList()), FakeTipsSource(), FakeDocumentStore(), clock, io = Dispatchers.Unconfined)
        }
        capture(name, dark) {
            HelpHost(
                route = HelpRoute.Guide(topic.id),
                onRoute = {},
                topics = HELP_TOPICS,
                progress = HelpProgress(),
                onTopicOpened = {},
                onTopicCompleted = {},
                sessions = sessions,
                theme = ThemeChoice(),
                onThemeChange = {},
                shadeEnabled = false,
                onShadeChange = {},
            )
        }
    }

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { BabyLogTheme(clock = clock, darkTheme = dark) { content() } }
        compose.onRoot().captureRoboImage("src/test/screenshots/$name.png", roborazziOptions = SCREENSHOT_OPTIONS)
    }
}
