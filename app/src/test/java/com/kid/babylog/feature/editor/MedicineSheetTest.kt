package com.kid.babylog.feature.editor

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
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

/** Подсказки названия в листе лекарства: ввод, касание подсказки и сохранение (ADR-0022). */
@RunWith(RobolectricTestRunner::class)
class MedicineSheetTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private var savedName: String? = null

    private fun showSheet(pastNames: List<String>) = compose.setContent {
        BabyLogTheme(clock = clock, darkTheme = false) {
            MedicineSheet(initial = null, pastNames = pastNames, onDismiss = {}, onSave = { name, _, _ -> savedName = name })
        }
    }

    private val nameField get() = compose.onNode(hasSetTextAction() and hasText("Название"))

    @Test
    fun `tapping a suggestion puts the whole name with the cursor at the end`() {
        showSheet(listOf("Витамин D", "Нурофен"))
        nameField.performTextInput("ви")
        compose.onNodeWithText("Нурофен").assertDoesNotExist()

        compose.onNodeWithText("Витамин D").performClick()
        nameField.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Витамин D")))
        nameField.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(9)))
        // Название набрано целиком — подсказка пропала, «Витамин D» остался только в поле
        compose.onAllNodesWithText("Витамин D").assertCountEquals(1)

        compose.onNodeWithText("Сохранить").performClick()
        compose.waitForIdle()
        assertEquals("Витамин D", savedName)
    }

    @Test
    fun `suggestions follow the typed letters`() {
        showSheet(listOf("Нурофен форте", "Нурофен", "Витамин D"))
        nameField.performTextInput("нурофен")
        compose.onNodeWithText("Нурофен форте").assertIsDisplayed()
        compose.onNodeWithText("Нурофен").assertDoesNotExist()
        compose.onNodeWithText("Витамин D").assertDoesNotExist()
    }
}
