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
import androidx.compose.ui.test.performTextReplacement
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

/** Подсказки в листе лекарства: ввод, касание подсказки, подстановка комментария и сохранение (ADR-0022). */
@RunWith(RobolectricTestRunner::class)
class MedicineSheetTest {

    @get:Rule(order = 0)
    val activity = ComponentActivityRule()

    @get:Rule(order = 1)
    val compose = createComposeRule()

    private val clock = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private var savedName: String? = null
    private var savedNote: String? = null

    private fun showSheet(pastMedicines: List<PastMedicine>) = compose.setContent {
        BabyLogTheme(clock = clock, darkTheme = false) {
            MedicineSheet(initial = null, pastMedicines = pastMedicines, onDismiss = {}, onSave = { name, _, note ->
                savedName = name
                savedNote = note
            })
        }
    }

    private fun showSheet(vararg names: String) = showSheet(names.map { PastMedicine(it) })

    private val nameField get() = compose.onNode(hasSetTextAction() and hasText("Название"))
    private val noteField get() = compose.onNode(hasSetTextAction() and hasText("Комментарий (доза, причина…)"))

    private fun save() {
        compose.onNodeWithText("Сохранить").performClick()
        compose.waitForIdle()
    }

    @Test
    fun `tapping a suggestion puts the whole name with the cursor at the end`() {
        showSheet("Витамин D", "Нурофен")
        nameField.performTextInput("ви")
        compose.onNodeWithText("Нурофен").assertDoesNotExist()

        compose.onNodeWithText("Витамин D").performClick()
        nameField.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("Витамин D")))
        nameField.assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(9)))
        // Название набрано целиком — подсказка пропала, «Витамин D» остался только в поле
        compose.onAllNodesWithText("Витамин D").assertCountEquals(1)

        save()
        assertEquals("Витамин D", savedName)
    }

    @Test
    fun `suggestions follow the typed letters`() {
        showSheet("Нурофен форте", "Нурофен", "Витамин D")
        nameField.performTextInput("нурофен")
        compose.onNodeWithText("Нурофен форте").assertIsDisplayed()
        compose.onNodeWithText("Нурофен").assertDoesNotExist()
        compose.onNodeWithText("Витамин D").assertDoesNotExist()
    }

    @Test
    fun `suggestion fills an empty note and another suggestion replaces it`() {
        showSheet(listOf(PastMedicine("Витамин D", "1 капля"), PastMedicine("Виферон", "1 свеча")))
        nameField.performTextInput("ви")
        compose.onNodeWithText("Витамин D").performClick()
        noteField.assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("1 капля")))

        // Передумал: другое лекарство — и доза другая
        nameField.performTextReplacement("ви")
        compose.onNodeWithText("Виферон").performClick()

        save()
        assertEquals("Виферон", savedName)
        assertEquals("1 свеча", savedNote)
    }

    @Test
    fun `suggestion keeps a note typed by hand`() {
        showSheet(listOf(PastMedicine("Витамин D", "1 капля")))
        noteField.performTextInput("от колик")
        nameField.performTextInput("ви")
        compose.onNodeWithText("Витамин D").performClick()

        save()
        assertEquals("Витамин D", savedName)
        assertEquals("от колик", savedNote)
    }
}
