package com.kid.babylog.feature.help

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.espresso.Espresso
import com.kid.babylog.GuideSandbox
import com.kid.babylog.GuideSessions
import com.kid.babylog.HelpHost
import com.kid.babylog.HelpRoute
import com.kid.babylog.core.data.HelpProgress
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.development.TipsSource
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeTipsSource
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.rules.ExternalResource
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Гайд темы [topicId] на примере дневника — для UI-теста, который проходит его до конца (ADR-0043).
 * Пример — [FakeDiaryRepository], часы стоят на 15 октября 2026, 10:00 UTC (их двигает только гайд).
 */
internal class GuideHarness(
    private val compose: ComposeContentTestRule,
    private val topicId: String,
    private val tips: TipsSource = FakeTipsSource(),
) {
    val clock: Clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    val example = FakeDiaryRepository(children = emptyList())
    private val completed = mutableListOf<String>()
    private val topic = HELP_TOPICS.single { it.id == topicId }

    fun show() {
        val sessions = GuideSessions { GuideSandbox(it, example, tips, FakeDocumentStore(), clock, io = Dispatchers.Unconfined) }
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                HelpHost(
                    route = HelpRoute.Guide(topicId),
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

    /** Кнопка или пункт с точным текстом. */
    fun tap(text: String) = done { compose.onNodeWithText(text).performClick() }

    /** Кнопка-иконка по описанию для TalkBack. */
    fun tapIcon(description: String) = done { compose.onNodeWithContentDescription(description).performClick() }

    /** Элемент по тому, что сделает касание: «начать сон», «изменить запись». */
    fun tapAction(clickLabel: String) = done { onAction(clickLabel).performClick() }

    fun onAction(clickLabel: String): SemanticsNodeInteraction = compose.onAllNodes(hasClickLabel(clickLabel)).onFirst()

    /**
     * Первый из нескольких элементов с этим текстом: метка на записи и в фильтрах, день в списке.
     * [scroll] — сначала прокрутить к нему: метка в ряду фильтров бывает за краем экрана.
     */
    fun tapFirst(text: String, scroll: Boolean = false) = done {
        val node = compose.onAllNodesWithText(text).onFirst()
        if (scroll) node.performScrollTo()
        node.performClick()
    }

    /** Последняя кнопка с этим текстом: кнопка листа поверх такой же кнопки экрана. */
    fun tapLast(text: String) = done { compose.onAllNodes(hasText(text) and hasClickAction()).onLast().performClick() }

    /** Плитка «Аналитики» с заголовком [title]. */
    fun tapTile(title: String) = done { compose.onNode(hasText(title) and hasClickLabel("подробнее")).performClick() }

    /** Ввод в первое поле открытого листа: вес, имя, текст заметки. */
    fun type(text: String) = done { compose.onAllNodes(hasSetTextAction()).onFirst().performTextInput(text) }

    /** Замена текста в поле с описанием [description]: «Температура». */
    fun typeInto(description: String, text: String) = done {
        compose.onNodeWithContentDescription(description).performTextReplacement(text)
    }

    /** Системная «Назад»: закрывает открытый лист. */
    fun back() = done { Espresso.pressBack() }

    /** Шаг с кнопкой: «Далее», «Понятно», «Прошло 10 минут». */
    fun next(button: String = "Далее") = tap(button)

    fun step(number: Int) {
        compose.onNodeWithText("${topic.title} · шаг $number из ${topic.steps.size}").assertIsDisplayed()
    }

    fun assertDone() {
        compose.onNodeWithText("Готово: тема «${topic.title}» пройдена").assertIsDisplayed()
        assertEquals(listOf(topicId), completed)
    }

    inline fun <reified T : BabyEvent> records(): List<T> = example.events.value.filterIsInstance<T>()

    private fun done(action: () -> Unit) {
        action()
        compose.waitForIdle()
    }
}

private fun hasClickLabel(label: String) =
    SemanticsMatcher("касание — «$label»") { it.config.getOrNull(SemanticsActions.OnClick)?.label == label }

/** Часовой пояс UTC на время теста: часы примера и подписи экрана — в одном поясе. */
class UtcRule : ExternalResource() {
    private lateinit var system: TimeZone

    override fun before() {
        system = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    override fun after() = TimeZone.setDefault(system)
}
