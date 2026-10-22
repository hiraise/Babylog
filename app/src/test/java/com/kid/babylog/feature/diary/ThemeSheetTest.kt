package com.kid.babylog.feature.diary

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.kid.babylog.core.data.DarkPalette
import com.kid.babylog.core.data.ThemeChoice
import com.kid.babylog.core.data.ThemeMode
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Лист «Тема»: режим и палитра тёмной темы выбираются независимо (ADR-0017, ADR-0032). */
@RunWith(RobolectricTestRunner::class)
class ThemeSheetTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private var theme by mutableStateOf(ThemeChoice())

    private fun showSheet(initial: ThemeChoice) {
        theme = initial
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                ThemeOptions(theme, onSelect = { theme = it })
            }
        }
    }

    @Test
    fun `palette is chosen without touching the mode`() {
        showSheet(ThemeChoice(mode = ThemeMode.DARK))
        // По умолчанию — «Классика» (ADR-0044)
        compose.onNodeWithText("Классика").assertIsSelected()
        compose.onNodeWithText("Ночник").performClick()
        assertEquals(ThemeChoice(mode = ThemeMode.DARK, darkPalette = DarkPalette.NIGHTLIGHT), theme)
        compose.onNodeWithText("Ночник").assertIsSelected()
        compose.onNodeWithText("Классика").assertIsNotSelected()
    }

    @Test
    fun `mode is chosen without touching the palette`() {
        showSheet(ThemeChoice(mode = ThemeMode.SYSTEM, darkPalette = DarkPalette.CLASSIC))
        compose.onNodeWithText("Тёмная").performClick()
        assertEquals(ThemeChoice(mode = ThemeMode.DARK, darkPalette = DarkPalette.CLASSIC), theme)
        compose.onNodeWithText("Классика").assertIsSelected()
    }

    @Test
    fun `light theme explains when palettes show`() {
        showSheet(ThemeChoice(mode = ThemeMode.LIGHT))
        compose.onNodeWithText("Видны, когда тема тёмная").assertExists()
        compose.onNodeWithText("Тёмная").performClick()
        compose.onNodeWithText("Видны, когда тема тёмная").assertDoesNotExist()
    }
}
