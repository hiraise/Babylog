package com.kid.babylog.feature.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
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

/** «+ своя метка» (ADR-0031): под полем — метки записей другого типа, по набранным буквам; касание выбирает. */
@RunWith(RobolectricTestRunner::class)
class TagPickerTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)

    @Test
    fun `hints of the other kind follow the typed letters and pick a tag`() {
        var selected by mutableStateOf(emptyList<String>())
        compose.setContent {
            BabyLogTheme(clock = clock, darkTheme = false) {
                TagPicker("Метки", listOf("новое"), selected, { selected = it }, addLabel = "своя метка", other = listOf("питание мамы", "перевозбуждение"), otherTitle = "Метки заметок:")
            }
        }
        compose.onNodeWithText("+ своя метка").performClick()
        // Без набранного — все подсказки, по буквам — с них начинается метка или слово в ней
        compose.onNodeWithText("Метки заметок:").assertIsDisplayed()
        compose.onNodeWithText("питание мамы").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performTextInput("мам")
        compose.onNodeWithText("перевозбуждение").assertDoesNotExist()
        compose.onNodeWithText("питание мамы").performClick()
        assertEquals(listOf("питание мамы"), selected)
        // Выбранная метка — чипом в ряду, поле закрылось
        compose.onNodeWithText("+ своя метка").assertIsDisplayed()
    }
}
