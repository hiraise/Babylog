package com.kid.babylog.feature.editor

import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.Note
import org.junit.Assert.assertEquals
import org.junit.Test

class CryingChoicesTest {

    @Test
    fun `used tags come first by frequency, then the defaults without repeats`() {
        val events = listOf(
            Crying(1, 1, 0, end = 1, reasons = listOf("зубки", "срыгивание"), helped = listOf("соска")),
            Crying(2, 1, 10, end = 11, reasons = listOf("срыгивание")),
            Crying(3, 1, 20, end = 21, reasons = listOf("колики"), helped = listOf("прогулка")),
        )
        val choices = cryingChoices(events)
        // «срыгивание» дважды, «зубки» и «колики» по разу — по алфавиту; дальше стандартные
        assertEquals(listOf("срыгивание", "зубки", "колики", "усталость", "голод", "живот", "перевозбуждение"), choices.reasons.shown)
        assertEquals(listOf("прогулка", "соска", "укачивание", "на руках", "грудь", "тёплое на живот", "белый шум"), choices.helped)
    }

    @Test
    fun `chips of a sheet are tags of its own kind, the other kind's tags are hints`() {
        val events = listOf(
            Note(1, 1, 0, tags = listOf("питание мамы")),
            Note(2, 1, 10, tags = listOf("питание мамы", "зубки")),
            Crying(3, 1, 20, end = 21, reasons = listOf("колики", "перевозбуждение")),
        )
        // У плача — его причины и стандартные; метка заметки — подсказкой, «зубки» уже среди стандартных
        val crying = cryingChoices(events).reasons
        assertEquals(listOf("колики", "перевозбуждение", "усталость", "голод", "живот", "зубки"), crying.shown)
        assertEquals(listOf("питание мамы"), crying.other)
        // У заметки — свои метки и свои стандартные: «перевозбуждение» не теснит «новое» и «сыпь»
        val note = noteTagChoices(events)
        assertEquals(listOf("питание мамы", "зубки", "новое", "сыпь", "прививка", "колики"), note.shown)
        assertEquals(listOf("перевозбуждение"), note.other)
    }

    @Test
    fun `no more than ten reasons and eight ways to calm`() {
        val events = (1..12).map { Crying(it.toLong(), 1, it * 10L, end = it * 10L + 1, reasons = listOf("метка $it"), helped = listOf("способ $it")) }
        val choices = cryingChoices(events)
        assertEquals(10, choices.reasons.shown.size)
        assertEquals(8, choices.helped.size)
    }
}
