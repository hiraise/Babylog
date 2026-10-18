package com.kid.babylog.feature.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.kid.babylog.core.ui.formatClock
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

/** «Когда»: чипы и своё число минут в чипе «Другое» (ADR-0022). */
@RunWith(RobolectricTestRunner::class)
class TimeAgoPickerTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private var minutesAgo by mutableIntStateOf(0)

    private fun showPicker() = compose.setContent {
        BabyLogTheme(clock = clock, darkTheme = false) {
            TimeAgoPicker(minutesAgo, { minutesAgo = it })
        }
    }

    private fun timeLine(minutes: Int, hint: String = "") = "Время: " + formatClock(clock.millis() - minutes * 60_000L) + hint

    private val field get() = compose.onNodeWithContentDescription("Минут назад")

    @Test
    fun `other chip turns into a focused minutes field`() {
        showPicker()
        compose.onNodeWithText("−15 мин").performClick()
        assertEquals(15, minutesAgo)
        compose.onNodeWithText("Другое").performClick()
        // Пустое поле — пока «сейчас»: прежний чип больше не выбран
        assertEquals(0, minutesAgo)
        field.assertIsFocused()
        field.performTextInput("25")
        assertEquals(25, minutesAgo)
        compose.onNodeWithText(timeLine(25)).assertExists()
    }

    @Test
    fun `hours are typed as minutes and capped at a day`() {
        showPicker()
        compose.onNodeWithText("Другое").performClick()
        field.performTextInput("90")
        assertEquals(90, minutesAgo)
        compose.onNodeWithText(timeLine(90, " · 1 ч 30 мин назад")).assertExists()
        field.performTextReplacement("2000")
        assertEquals(MAX_MINUTES_AGO, minutesAgo)
        compose.onNodeWithText(timeLine(MAX_MINUTES_AGO, " · не больше суток")).assertExists()
    }

    @Test
    fun `preset chip closes the field`() {
        showPicker()
        compose.onNodeWithText("Другое").performClick()
        field.performTextInput("7")
        compose.onNodeWithText("−30 мин").performClick()
        assertEquals(30, minutesAgo)
        compose.onNodeWithText("Другое").assertExists()
        field.assertDoesNotExist()
    }

    @Test
    fun `input keeps up to four digits`() {
        assertEquals("125", customMinutesInput("1 2,5"))
        assertEquals("1234", customMinutesInput("12345"))
        assertEquals(0, customMinutes(""))
        assertEquals(MAX_MINUTES_AGO, customMinutes("9999"))
    }
}
