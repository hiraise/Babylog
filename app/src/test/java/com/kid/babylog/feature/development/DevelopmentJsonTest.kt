package com.kid.babylog.feature.development

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.LocalDate

/** Разбор `org.json` — класс платформы, поэтому под Robolectric. */
@RunWith(RobolectricTestRunner::class)
class DevelopmentJsonTest {

    @Test
    fun `parses a full tip`() {
        val tip = parseTips("[${tipJson()}]").single()
        assertEquals("m3-rolling", tip.id)
        assertEquals(91..210, tip.ages)
        assertEquals(TipCategory.SAFETY, tip.category)
        assertEquals(TipKind.TIP, tip.kind)
        assertEquals(listOf("пункт"), tip.points)
        assertEquals("https://www.nhs.uk/baby/", tip.sources.single().url)
        assertEquals(listOf(SourcePosition("ВОЗ", "с 6 месяцев")), tip.disagreement)
        assertEquals(LocalDate.of(2026, 10, 20), tip.reviewedAt)
    }

    @Test
    fun `points and disagreement are optional`() {
        val tip = parseTips("[${tipJson(points = null, disagreement = null)}]").single()
        assertTrue(tip.points.isEmpty())
        assertTrue(tip.disagreement.isEmpty())
    }

    @Test
    fun `rejects a tip without sources`() {
        assertRejected("m3-rolling", tipJson(sources = "[]"))
    }

    @Test
    fun `rejects a source outside the allowed sites`() {
        assertRejected("не из списка", tipJson(sources = """[{"name": "Блог", "url": "https://example.com/a", "section": "Раздел"}]"""))
    }

    @Test
    fun `rejects unknown category, empty text and ages beyond the first year`() {
        assertRejected("category", tipJson(category = "GROWTH"))
        assertRejected("title", tipJson(title = "  "))
        assertRejected("вне 0–364", tipJson(to = 400))
        assertRejected("ageFromDays больше ageToDays", tipJson(from = 100, to = 50))
    }

    @Test
    fun `allowed sources include subdomains and doi but only over https`() {
        assertTrue(isAllowedSourceUrl("https://cdn.who.int/media/a.pdf"))
        assertTrue(isAllowedSourceUrl("https://www.healthychildren.org/English/a.aspx"))
        assertTrue(isAllowedSourceUrl("https://doi.org/10.1542/peds.113.6.e570"))
        assertFalse(isAllowedSourceUrl("http://www.nhs.uk/"))
        assertFalse(isAllowedSourceUrl("https://notwho.int/"))
        assertFalse(isAllowedSourceUrl("не ссылка"))
    }

    @Test
    fun `parses milestone windows`() {
        val table = parseMilestones(
            """
            {"source": {"name": "ВОЗ", "url": "https://cdn.who.int/t.pdf", "section": "1st и 99th"},
             "reviewedAt": "2026-10-20",
             "milestones": [{"id": "sitting", "title": "Сидит без поддержки", "fromDays": 115, "toDays": 279}]}
            """.trimIndent()
        )
        assertEquals(listOf(MilestoneWindow("sitting", "Сидит без поддержки", 115..279)), table.milestones)
        assertEquals("ВОЗ", table.source.name)
    }

    private fun assertRejected(messagePart: String, tip: String) {
        val error = assertThrows(IllegalArgumentException::class.java) { parseTips("[$tip]") }
        assertTrue("«${error.message}» без «$messagePart»", error.message.orEmpty().contains(messagePart))
    }

    private fun tipJson(
        from: Int = 91,
        to: Int = 210,
        category: String = "SAFETY",
        title: String = "Скоро начнёт переворачиваться",
        points: String? = """["пункт"]""",
        sources: String = """[{"name": "NHS — Baby", "url": "https://www.nhs.uk/baby/", "section": "Раздел"}]""",
        disagreement: String? = """[{"source": "ВОЗ", "position": "с 6 месяцев"}]""",
    ) = buildString {
        append("""{"id": "m3-rolling", "ageFromDays": $from, "ageToDays": $to, "category": "$category", "kind": "TIP",""")
        append(""" "title": "$title", "summary": "Кратко", "body": "Текст", "sources": $sources, "reviewedAt": "2026-10-20"""")
        if (points != null) append(""", "points": $points""")
        if (disagreement != null) append(""", "disagreement": $disagreement""")
        append("}")
    }
}
