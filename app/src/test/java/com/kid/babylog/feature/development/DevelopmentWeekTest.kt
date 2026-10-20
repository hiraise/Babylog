package com.kid.babylog.feature.development

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DevelopmentWeekTest {

    private val birth = LocalDate.of(2026, 5, 20)
    private val sitting = MilestoneWindow("sitting", "Сидит без поддержки", 115..279, 3.8..9.2)
    private val walking = MilestoneWindow("walking", "Ходит самостоятельно", 250..534, 8.2..17.6)
    private val data = DevelopmentData(
        tips = listOf(
            tip("sleep", 0..180, TipCategory.SLEEP),
            tip("solids", 126..181, TipCategory.FEEDING),
            tip("doctor", 112..181, TipCategory.DEVELOPMENT, TipKind.DOCTOR),
            tip("later", 200..300, TipCategory.SAFETY),
        ),
        milestones = MilestoneTable(TipSource("ВОЗ", "https://cdn.who.int/t.pdf", "1st, 99th"), birth, listOf(sitting, walking)),
    )

    @Test
    fun `week splits tips from doctor tips and places the child in each window`() {
        val week = developmentWeek(data, birth, birth.plusDays(150))!!
        assertEquals(150, week.ageDays)
        assertEquals(22, week.week)
        assertEquals(listOf("solids", "sleep"), week.tips.map { it.id })
        assertEquals(listOf("doctor"), week.doctorTips.map { it.id })
        assertEquals(listOf("solids", "sleep", "doctor"), week.allTips.map { it.id })
        assertEquals(listOf(MilestoneState.IN_WINDOW, MilestoneState.AHEAD), week.milestones.map { it.second })
    }

    @Test
    fun `no week before birth`() {
        assertNull(developmentWeek(data, birth, birth.minusDays(1)))
    }

    @Test
    fun `tips are new until this week was opened for this child`() {
        val week = developmentWeek(data, birth, birth.plusDays(150))!!
        assertTrue(hasNewTips(week, childId = 1, seenWeeks = emptyMap()))
        assertTrue(hasNewTips(week, childId = 1, seenWeeks = mapOf(1L to 21, 2L to 22)))
        assertFalse(hasNewTips(week, childId = 1, seenWeeks = mapOf(1L to 22)))
        assertFalse(hasNewTips(null, childId = 1, seenWeeks = emptyMap()))
        val noTips = developmentWeek(data.copy(tips = emptyList()), birth, birth.plusDays(150))
        assertFalse(hasNewTips(noTips, childId = 1, seenWeeks = emptyMap()))
    }

    @Test
    fun `texts name the week, topics and windows`() {
        val week = developmentWeek(data, birth, birth.plusDays(150))!!
        assertEquals("22-я неделя", weekTitle(22))
        assertEquals("на 22-ю неделю", "на ${weekAccusative(22)}")
        assertEquals("Сон, кормление, развитие · 3 совета", bannerSummary(week))
        assertEquals("Сейчас в окне: сидит без поддержки.", windowsSummary(week))
        val newborn = developmentWeek(data, birth, birth.plusDays(10))!!
        assertEquals("Ближайшее окно — «сидит без поддержки», с 3,8 мес.", windowsSummary(newborn))
        assertEquals("для 4–6 мес", tipAgeText(tip("x", 126..181, TipCategory.FEEDING)))
        assertEquals("3,8", formatMonths(115))
    }

    private fun tip(id: String, ages: IntRange, category: TipCategory, kind: TipKind = TipKind.TIP) = Tip(
        id = id,
        ages = ages,
        category = category,
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
