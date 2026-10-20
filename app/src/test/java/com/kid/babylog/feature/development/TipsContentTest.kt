package com.kid.babylog.feature.development

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.time.LocalDate

/**
 * Проверка контента раздела «Развитие» по правилам ADR-0037 — на тех файлах, что попадут в сборку:
 * схема и список источников проверяются при разборе, здесь — остальное. Смысл и контекст каждого
 * совета сверяет субагент `tips-verifier` до PR.
 */
@RunWith(RobolectricTestRunner::class)
class TipsContentTest {

    private val source = AssetTipsSource(RuntimeEnvironment.getApplication())
    private val tips = source.tips()
    private val today = LocalDate.now()

    @Test
    fun `ids are unique`() {
        val duplicates = tips.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue("повторяются id: $duplicates", duplicates.isEmpty())
    }

    @Test
    fun `every week of the first year has a tip`() {
        val empty = (1..LAST_WEEK).filter { week ->
            val days = (week - 1) * 7..week * 7 - 1
            tips.none { it.ages.first <= days.last && it.ages.last >= days.first }
        }
        assertTrue("недели без советов: $empty", empty.isEmpty())
    }

    @Test
    fun `texts fit the screen`() {
        val tooLong = tips.flatMap { tip ->
            listOfNotNull(
                "${tip.id}: title".takeIf { tip.title.length > MAX_TITLE },
                "${tip.id}: summary".takeIf { tip.summary.length > MAX_SUMMARY },
                "${tip.id}: body".takeIf { tip.body.length > MAX_BODY },
                "${tip.id}: points".takeIf { tip.points.size > MAX_POINTS || tip.points.any { it.length > MAX_POINT } },
            )
        }
        assertTrue("длиннее лимита: $tooLong", tooLong.isEmpty())
    }

    @Test
    fun `tips name no doses`() {
        val withDoses = tips.filter { tip -> tip.texts().any { DOSE.containsMatchIn(it) } }.map { it.id }
        assertTrue("дозы в советах: $withDoses", withDoses.isEmpty())
    }

    @Test
    fun `every tip and the milestone table were reviewed within two years`() {
        val stale = tips.filter { !it.reviewedAt.isFresh() }.map { it.id }
        assertTrue("перепроверить: $stale", stale.isEmpty())
        assertTrue("перепроверить окна навыков", source.milestones().reviewedAt.isFresh())
    }

    @Test
    fun `milestone table has six windows from who`() {
        val table = source.milestones()
        assertEquals(6, table.milestones.size)
        assertTrue(table.source.url.startsWith("https://cdn.who.int/"))
        assertEquals(table.milestones.size, table.milestones.map { it.id }.toSet().size)
    }

    private fun LocalDate.isFresh() = !isAfter(today) && !isBefore(today.minusYears(2))

    private fun Tip.texts() = listOf(title, summary, body) + points + disagreement.map { it.position }

    private companion object {
        const val LAST_WEEK = 52
        const val MAX_TITLE = 60
        const val MAX_SUMMARY = 100
        const val MAX_BODY = 600
        const val MAX_POINTS = 5
        const val MAX_POINT = 90

        /** Число с единицей дозы: «400 МЕ», «2,5 мг», «мл/кг». `\b` не годится: он не видит кириллицу. */
        val DOSE = Regex("""\d+(?:[.,]\d+)?\s*(?:мкг|мг|МЕ|ME|IU|mg)(?!\p{L})|мл\s*/\s*кг""")
    }
}
