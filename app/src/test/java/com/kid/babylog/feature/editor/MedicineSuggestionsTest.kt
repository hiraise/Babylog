package com.kid.babylog.feature.editor

import com.kid.babylog.core.model.Diaper
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Medicine
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicineSuggestionsTest {

    @Test
    fun `past medicines are distinct, recent first, in the latest spelling`() {
        val events = listOf(
            Medicine(childId = 1, start = 1_000, name = "нурофен"),
            Medicine(childId = 1, start = 4_000, name = " Нурофен "),
            Diaper(childId = 1, start = 5_000, kind = DiaperKind.WET),
            Medicine(childId = 1, start = 3_000, name = "Витамин D"),
            Medicine(childId = 1, start = 2_000, name = "Эспумизан"),
        )
        assertEquals(listOf("Нурофен", "Витамин D", "Эспумизан"), pastMedicines(events).map { it.name })
    }

    @Test
    fun `past medicine keeps the latest non-blank note`() {
        val events = listOf(
            Medicine(childId = 1, start = 1_000, name = "Витамин D", note = "2 капли"),
            Medicine(childId = 1, start = 2_000, name = "витамин d", note = " 1 капля "),
            Medicine(childId = 1, start = 3_000, name = "Витамин D", note = " "),
            Medicine(childId = 1, start = 4_000, name = "Нурофен"),
        )
        assertEquals(
            listOf(PastMedicine("Нурофен", ""), PastMedicine("Витамин D", "1 капля")),
            pastMedicines(events),
        )
    }

    @Test
    fun `names starting with the typed letters come before word matches`() {
        val medicines = medicines("Витамин D", "Дюфалак", "Нурофен", "Детский Панадол")
        assertEquals(listOf("Дюфалак", "Детский Панадол"), suggested(medicines, "д"))
        assertEquals(listOf("Витамин D"), suggested(medicines, "D"))
        assertEquals(listOf("Детский Панадол"), suggested(medicines, "пан"))
    }

    @Test
    fun `case and yo are ignored`() {
        val medicines = medicines("Ёлочка", "НУРОФЕН")
        assertEquals(listOf("Ёлочка"), suggested(medicines, "ел"))
        assertEquals(listOf("НУРОФЕН"), suggested(medicines, "нур"))
    }

    @Test
    fun `nothing is suggested for empty input or a fully typed name`() {
        val medicines = medicines("Нурофен", "Нурофен форте")
        assertEquals(emptyList<String>(), suggested(medicines, " "))
        assertEquals(listOf("Нурофен форте"), suggested(medicines, "нурофен "))
        assertEquals(emptyList<String>(), suggested(medicines, "Нурофен форте"))
    }

    @Test
    fun `at most three suggestions, recent first`() {
        val medicines = medicines("Аква Марис", "Аквадетрим", "Амоксиклав", "Арбидол")
        assertEquals(listOf("Аква Марис", "Аквадетрим", "Амоксиклав"), suggested(medicines, "а"))
    }

    @Test
    fun `suggestion fills an empty note or replaces the one it filled itself`() {
        val vitamin = PastMedicine("Витамин D", "1 капля")
        val nurofen = PastMedicine("Нурофен", "2,5 мл")
        assertEquals("1 капля", noteAfterSuggestion("", "", vitamin))
        assertEquals("1 капля", noteAfterSuggestion(" ", "", vitamin))
        assertEquals("2,5 мл", noteAfterSuggestion("1 капля", "1 капля", nurofen))
        assertEquals("", noteAfterSuggestion("1 капля", "1 капля", PastMedicine("Эспумизан")))
    }

    @Test
    fun `suggestion keeps a note typed or edited by hand`() {
        val vitamin = PastMedicine("Витамин D", "1 капля")
        assertEquals("от колик", noteAfterSuggestion("от колик", "", vitamin))
        assertEquals("1 капля утром", noteAfterSuggestion("1 капля утром", "1 капля", PastMedicine("Нурофен", "2,5 мл")))
    }

    private fun medicines(vararg names: String) = names.map { PastMedicine(it) }

    private fun suggested(medicines: List<PastMedicine>, query: String) =
        medicineSuggestions(medicines, query).map { it.name }
}
