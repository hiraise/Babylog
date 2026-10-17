package com.kid.babylog.core.ui

import com.kid.babylog.core.model.Sex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Родительный падеж имени для заголовка «Дневник Маши» (ADR-0019). */
class NameDeclensionTest {

    @Test
    fun `names are declined by their ending`() {
        val cases = listOf(
            "Маша" to "Маши", "Ольга" to "Ольги", "Алиса" to "Алисы", "Анна" to "Анны", "Никита" to "Никиты",
            "Петя" to "Пети", "Мария" to "Марии", "Илья" to "Ильи",
            "Андрей" to "Андрея", "Игорь" to "Игоря", "Иван" to "Ивана", "Малыш" to "Малыша",
            "Павел" to "Павла", "Лев" to "Льва", "Пётр" to "Петра",
            "Нико" to "Нико", "Анна-Мария" to "Анны-Марии", "МАША" to "МАШИ", "маша" to "маши",
        )
        cases.forEach { (name, genitive) -> assertEquals(name, genitive, genitiveName(name, sex = null)) }
    }

    @Test
    fun `girls with a soft sign or a consonant at the end`() {
        assertEquals("Любови", genitiveName("Любовь", Sex.GIRL))
        assertEquals("Нинели", genitiveName("Нинель", Sex.GIRL))
        assertEquals("Кармен", genitiveName("Кармен", Sex.GIRL))
        assertEquals("Ивана", genitiveName("Иван", Sex.BOY))
    }

    @Test
    fun `names the rules cannot decline are shown as is`() {
        assertNull(genitiveName("Петя, младший", null))
        assertNull(genitiveName("Emma", null))
        assertNull(genitiveName("Маша 2", null))
        assertEquals("Дневник Маши", diaryTitle(" Маша ", Sex.GIRL))
        assertEquals("Дневник: Петя, младший", diaryTitle("Петя, младший", Sex.BOY))
    }
}
