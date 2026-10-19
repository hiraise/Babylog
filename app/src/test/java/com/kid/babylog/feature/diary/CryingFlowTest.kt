package com.kid.babylog.feature.diary

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.model.Breast
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import com.kid.babylog.testing.FakeDiaryRepository
import com.kid.babylog.testing.FakeDocumentStore
import com.kid.babylog.testing.FakeSelectedChildStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * Плач с главного экрана (ADR-0030): касание плитки запускает таймер, второе — останавливает его и
 * открывает лист; выбранные причина и «что помогло» сохраняются в ту же запись, а «Ещё плачет»
 * продолжает таймер.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class CryingFlowTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private lateinit var systemZone: TimeZone

    // id записей — с 100: новые записи фейк нумерует с 1
    private val repository = FakeDiaryRepository(listOf(Child(1, "Маша", null, Sex.GIRL))).apply {
        events.value = listOf(
            Sleep(id = 100, childId = 1, start = at("2026-10-15T06:30:00Z"), end = at("2026-10-15T08:00:00Z")),
            Feeding(id = 101, childId = 1, start = at("2026-10-15T08:05:00Z"), end = at("2026-10-15T08:25:00Z"), breast = Breast.LEFT),
        )
    }

    private val crying get() = repository.events.value.filterIsInstance<Crying>().single()

    @Before
    fun showDiary() {
        systemZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val viewModel = DiaryViewModel(repository, FakeSelectedChildStore(), FakeDocumentStore(), clock)
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                DiaryScreen(viewModel, ThemeChoice(), onThemeChange = {})
            }
        }
    }

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    private fun tapCryingTile() {
        compose.onNode(hasText("Плач") and hasClickAction()).performClick()
        compose.waitForIdle()
    }

    @Test
    fun `stopped crying asks why and keeps the answer in the same record`() {
        tapCryingTile()
        assertTrue(crying.isActive)

        tapCryingTile()
        compose.onNodeWithText("Что было до").assertIsDisplayed()
        compose.onNodeWithText("Не спала 2 ч").assertIsDisplayed()
        compose.onNodeWithText("колики").performClick()
        compose.onNodeWithText("укачивание").performClick()
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitForIdle()

        assertFalse(crying.isActive)
        assertEquals(listOf("колики"), crying.reasons)
        assertEquals(listOf("укачивание"), crying.helped)
        compose.onNodeWithText("Что было до").assertDoesNotExist()
    }

    @Test
    fun `an accidental start is undone from the message`() {
        tapCryingTile()
        compose.onNodeWithText("Отменить").performClick()
        compose.waitForIdle()
        assertTrue(repository.events.value.none { it is Crying })
    }

    @Test
    fun `crying stopped by mistake goes on`() {
        tapCryingTile()
        tapCryingTile()
        compose.onNodeWithText("Ещё плачет").performClick()
        compose.waitForIdle()

        assertTrue(crying.isActive)
        compose.onNodeWithText("Что было до").assertDoesNotExist()
    }

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()
}
