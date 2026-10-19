package com.kid.babylog.feature.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.espresso.Espresso
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Note
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.feature.diary.DiaryScreen
import com.kid.babylog.feature.diary.DiaryTab
import com.kid.babylog.feature.diary.DiaryViewModel
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import org.junit.After
import org.junit.Assert.assertEquals
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
 * Вкладка «Заметки» (ADR-0031): «Заметка» открывает лист, заметка с меткой появляется в ленте;
 * метка в ряду фильтров показывает сводку, системная «Назад» снимает фильтр; карточка плача
 * за неделю открывает раздел «Плач» в «Аналитике».
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class NotesFlowTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var systemZone: TimeZone

    // id записей — с 100: новые записи фейк нумерует с 1
    private val repository = FakeDiaryRepository(listOf(Child(1, "Маша", null, Sex.GIRL))).apply {
        events.value = listOf(
            Feeding(id = 100, childId = 1, start = at("2026-10-14T08:00:00Z"), end = at("2026-10-14T08:20:00Z"), breast = Breast.LEFT),
            Crying(id = 101, childId = 1, start = at("2026-10-14T18:30:00Z"), end = at("2026-10-14T18:50:00Z"), reasons = listOf("колики")),
            Note(id = 102, childId = 1, start = at("2026-10-14T11:20:00Z"), tags = listOf("новое"), note = "Перевернулась!"),
        )
    }

    @Before
    fun showNotes() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val viewModel = DiaryViewModel(repository, FakeSelectedChildStore(), FakeDocumentStore(), clock)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                DiaryScreen(viewModel, ThemeMode.SYSTEM, onThemeModeChange = {}, initialTab = DiaryTab.NOTES)
            }
        }
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    @Test
    fun `new note with a tag appears in the feed and the tag shows its summary`() {
        compose.onNode(hasText("Заметка") and hasClickAction()).performClick()
        compose.onNode(hasSetTextAction() and hasText("Что заметили")).performTextInput("Тёрла ушко")
        compose.onNodeWithText("зубки").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitForIdle()

        val note = repository.events.value.filterIsInstance<Note>().single { it.note == "Тёрла ушко" }
        assertEquals(listOf("зубки"), note.tags)
        compose.onNodeWithText("Тёрла ушко").assertIsDisplayed()

        // Метка на карточке — не правка записи, а её сводка
        compose.onNode(hasText("зубки") and hasClickAction() and !isSelectable()).performClick()
        compose.onNodeWithText("По дням · последние 4 недели").assertIsDisplayed()
        compose.onNode(hasText("зубки") and isSelectable()).assertIsSelected()
        compose.onNodeWithText("Перевернулась!").assertDoesNotExist()

        Espresso.pressBack()
        compose.onNodeWithText("По дням · последние 4 недели").assertDoesNotExist()
        compose.onNodeWithText("Перевернулась!").assertIsDisplayed()
    }

    @Test
    fun `crying of the week leads to the crying section of analytics`() {
        compose.onNode(hasText("в сутки за неделю", substring = true)).performClick()
        compose.onNodeWithText("Перед плачем").assertIsDisplayed()
    }

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
