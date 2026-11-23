package com.hiraise.babylog

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.captureRoboImage
import com.hiraise.babylog.core.data.HelpProgress
import com.hiraise.babylog.core.data.ThemeChoice
import com.hiraise.babylog.core.model.Child
import com.hiraise.babylog.core.model.Sex
import com.hiraise.babylog.core.ui.theme.BabyLogTheme
import com.hiraise.babylog.feature.development.AssetTipsSource
import com.hiraise.babylog.feature.diary.DiaryScreen
import com.hiraise.babylog.feature.diary.DiaryView
import com.hiraise.babylog.feature.diary.DiaryViewModel
import com.hiraise.babylog.feature.diary.MainSection
import com.hiraise.babylog.feature.help.HELP_TOPICS
import com.hiraise.babylog.feature.help.SAMPLE_AGE_DAYS
import com.hiraise.babylog.feature.help.SAMPLE_CHILD_NAME
import com.hiraise.babylog.feature.help.UtcRule
import com.hiraise.babylog.feature.help.sampleMonths
import com.hiraise.babylog.testing.ComponentActivityRule
import com.hiraise.babylog.testing.FakeDiaryRepository
import com.hiraise.babylog.testing.FakeDocumentStore
import com.hiraise.babylog.testing.FakeSelectedChildStore
import com.hiraise.babylog.testing.SCREENSHOT_OPTIONS
import com.hiraise.babylog.testing.testDevelopment
import kotlinx.coroutines.Dispatchers
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/**
 * Скриншоты README (`docs/screenshots/`): дневник девочки примера, как в гайдах справки (ADR-0043).
 * Сверяются с приложением, как все эталоны: поменял вид — `scripts/dev.sh goldens`, и README не отстанет.
 * Экран 393 × 851 dp в xhdpi — картинки в README узкие, большее разрешение только раздуло бы файлы.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w393dp-h851dp-normal-long-notround-any-xhdpi-keyshidden-nonav")
class ReadmeScreenshotTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    /** Утро, идёт дневной сон. */
    private val clock = Clock.fixed(Instant.parse("2026-10-15T09:15:00Z"), ZoneOffset.UTC)

    @Test
    fun today() = captureDiary("today")

    @Test
    fun `today, dark`() = captureDiary("today_dark", dark = true)

    @Test
    fun diary() = captureDiary("diary", section = MainSection.DIARY)

    @Test
    fun notes() = captureDiary("notes", section = MainSection.DIARY, view = DiaryView.NOTES)

    @Test
    fun analytics() = captureDiary("analytics", section = MainSection.ANALYTICS)

    @Test
    fun development() = captureDiary("development", section = MainSection.DEVELOPMENT)

    /** Первый шаг гайда «Сон и кормление»: панель шага и обведённая плитка. */
    @Test
    fun help() {
        val sessions = GuideSessions {
            GuideSandbox(it, FakeDiaryRepository(children = emptyList()), tips(), FakeDocumentStore(), clock, io = Dispatchers.Unconfined)
        }
        capture("help") {
            HelpHost(
                route = HelpRoute.Guide(HELP_TOPICS.first().id),
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

    private fun captureDiary(name: String, dark: Boolean = false, section: MainSection = MainSection.TODAY, view: DiaryView = DiaryView.DAYS) {
        val today = clock.instant().atZone(ZoneOffset.UTC).toLocalDate()
        val birth = today.minusDays(SAMPLE_AGE_DAYS).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        // Идущие таймеры оставлены: на «Сегодня» идёт сон
        val repository = FakeDiaryRepository(listOf(Child(1, SAMPLE_CHILD_NAME, birth, Sex.GIRL))).apply {
            events.value = sampleMonths(childId = 1, today = today, now = clock.millis(), zone = ZoneOffset.UTC)
        }
        val diary = DiaryViewModel(repository, FakeSelectedChildStore(), FakeDocumentStore(), clock)
        capture(name, dark) {
            DiaryScreen(
                diary,
                ThemeChoice(),
                onThemeChange = {},
                development = testDevelopment(tips()),
                initialSection = section,
                initialDiaryView = view,
            )
        }
    }

    private fun tips() = AssetTipsSource(RuntimeEnvironment.getApplication())

    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        compose.setContent { BabyLogTheme(clock = clock, darkTheme = dark) { content() } }
        compose.onRoot().captureRoboImage("../docs/screenshots/$name.png", roborazziOptions = SCREENSHOT_OPTIONS)
    }
}
