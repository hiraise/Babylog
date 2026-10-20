package com.kid.babylog.feature.development

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DevelopmentAgeTest {

    private val birth = LocalDate.of(2026, 5, 28)

    @Test
    fun `birthday is day zero of the first week`() {
        assertEquals(0, ageInDays(birth, birth))
        assertEquals(1, weekOfLife(0))
    }

    @Test
    fun `weeks of life start every seventh day`() {
        assertEquals(1, weekOfLife(6))
        assertEquals(2, weekOfLife(7))
        assertEquals(52, weekOfLife(363))
        assertEquals(53, weekOfLife(364))
    }

    @Test
    fun `age counts full days across months and leap years`() {
        assertEquals(146, ageInDays(birth, LocalDate.of(2026, 10, 21)))
        assertEquals(366, ageInDays(LocalDate.of(2027, 5, 28), LocalDate.of(2028, 5, 28)))
    }

    @Test
    fun `birth date in the future gives no age`() {
        assertNull(ageInDays(birth.plusDays(1), birth))
    }

    @Test
    fun `tips for age are newest first and doctor tips last`() {
        val early = tip("early", 0..100)
        val later = tip("later", 50..200)
        val doctor = tip("doctor", 0..364, TipKind.DOCTOR)
        val future = tip("future", 201..300)
        assertEquals(
            listOf("later", "early", "doctor"),
            tipsFor(listOf(doctor, early, future, later), ageDays = 60).map { it.id },
        )
    }

    @Test
    fun `range bounds are inclusive`() {
        val tips = listOf(tip("a", 10..20))
        assertEquals(1, tipsFor(tips, 10).size)
        assertEquals(1, tipsFor(tips, 20).size)
        assertEquals(0, tipsFor(tips, 21).size)
    }

    @Test
    fun `milestone state is relative to its window`() {
        val sitting = MilestoneWindow("sitting", "Сидит без поддержки", 115..279, 3.8..9.2)
        assertEquals(MilestoneState.AHEAD, sitting.stateAt(114))
        assertEquals(MilestoneState.IN_WINDOW, sitting.stateAt(115))
        assertEquals(MilestoneState.IN_WINDOW, sitting.stateAt(279))
        assertEquals(MilestoneState.PAST, sitting.stateAt(280))
    }

    private fun tip(id: String, ages: IntRange, kind: TipKind = TipKind.TIP) = Tip(
        id = id,
        ages = ages,
        category = TipCategory.SLEEP,
        kind = kind,
        title = id,
        summary = id,
        body = id,
        points = emptyList(),
        sources = listOf(TipSource("NHS", "https://www.nhs.uk/", "Раздел")),
        disagreement = emptyList(),
        reviewedAt = LocalDate.of(2026, 10, 20),
    )
}
