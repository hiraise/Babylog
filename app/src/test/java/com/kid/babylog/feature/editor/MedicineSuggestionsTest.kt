package com.kid.babylog.feature.editor

import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Medicine
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineSuggestionsTest {

    @Test
    fun `past names are distinct, recent first, in the latest spelling`() {
        val events = listOf(
            Medicine(childId = 1, start = 1_000, name = "нурофен"),
            Medicine(childId = 1, start = 4_000, name = " Нурофен "),
            Diaper(childId = 1, start = 5_000, kind = DiaperKind.WET),
            Medicine(childId = 1, start = 3_000, name = "Витамин D"),
            Medicine(childId = 1, start = 2_000, name = "Эспумизан"),
        )
        assertEquals(listOf("Нурофен", "Витамин D", "Эспумизан"), pastMedicineNames(events))
    }

    @Test
    fun `names starting with the typed letters come before word matches`() {
        val names = listOf("Витамин D", "Дюфалак", "Нурофен", "Детский Панадол")
        assertEquals(listOf("Дюфалак", "Детский Панадол"), medicineSuggestions(names, "д"))
        assertEquals(listOf("Витамин D"), medicineSuggestions(names, "D"))
        assertEquals(listOf("Детский Панадол"), medicineSuggestions(names, "пан"))
    }

    @Test
    fun `case and yo are ignored`() {
        val names = listOf("Ёлочка", "НУРОФЕН")
        assertEquals(listOf("Ёлочка"), medicineSuggestions(names, "ел"))
        assertEquals(listOf("НУРОФЕН"), medicineSuggestions(names, "нур"))
    }

    @Test
    fun `nothing is suggested for empty input or a fully typed name`() {
        val names = listOf("Нурофен", "Нурофен форте")
        assertEquals(emptyList<String>(), medicineSuggestions(names, " "))
        assertEquals(listOf("Нурофен форте"), medicineSuggestions(names, "нурофен "))
        assertEquals(emptyList<String>(), medicineSuggestions(names, "Нурофен форте"))
    }

    @Test
    fun `at most three suggestions, recent first`() {
        val names = listOf("Аква Марис", "Аквадетрим", "Амоксиклав", "Арбидол")
        assertEquals(listOf("Аква Марис", "Аквадетрим", "Амоксиклав"), medicineSuggestions(names, "а"))
    }
}
