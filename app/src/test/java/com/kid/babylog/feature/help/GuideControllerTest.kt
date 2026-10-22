package com.kid.babylog.feature.help

import com.kid.babylog.R
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Sleep
import com.kid.babylog.core.ui.guide.GuideTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** Ход гайда (ADR-0043): шаги засчитываются по записям, цели и кнопке и не откатываются. */
class GuideControllerTest {

    private val base = Clock.fixed(Instant.parse("2026-10-15T10:00:00Z"), ZoneOffset.UTC)
    private val clock = GuideClock(base)
    private val sleeping = StepDone.Records { now, _ -> now.any { it is Sleep && it.isActive } }
    private val topic = HelpTopic(
        id = "test",
        title = "Тест",
        icon = R.drawable.ic_help,
        summary = "",
        steps = listOf(
            GuideStep("Запустите сон", sleeping, target = GuideTarget.SLEEP_TILE, sheetHint = "В листе"),
            GuideStep("Подождите", StepDone.Next, button = "Прошло 40 минут", skipMinutes = 40),
            GuideStep("Откройте кормление", StepDone.Shown(GuideTarget.FEEDING_TILE)),
        ),
    )
    private val guide = GuideController(topic, clock)
    private val sleep = Sleep(id = 1, childId = 1, start = base.millis())

    @Test
    fun `record step waits for the record`() {
        guide.onEvents(emptyList())
        assertEquals(0, guide.index)
        assertEquals(GuideTarget.SLEEP_TILE, guide.target)
        assertEquals("В листе", guide.sheetHint)
        guide.onEvents(listOf(sleep))
        assertEquals(1, guide.index)
        assertEquals("Подождите", guide.sheetHint)
    }

    @Test
    fun `button skips the example clock and passes the step`() {
        guide.onEvents(listOf(sleep))
        guide.press()
        assertEquals(base.millis() + 40 * 60_000L, clock.millis())
        assertEquals(2, guide.index)
    }

    @Test
    fun `button does nothing on a step that waits for an action`() {
        guide.onEvents(emptyList())
        guide.press()
        assertEquals(0, guide.index)
        assertEquals(base.millis(), clock.millis())
    }

    @Test
    fun `passed step stays passed when the record goes away`() {
        guide.onEvents(listOf(sleep))
        guide.onEvents(listOf(sleep.copy(end = base.millis())))
        assertEquals(1, guide.index)
    }

    @Test
    fun `skip passes a step that waits for an action`() {
        guide.onEvents(emptyList())
        guide.skip()
        assertEquals(1, guide.index)
        assertEquals(base.millis(), clock.millis())
    }

    @Test
    fun `visited step passes when the sheet opened earlier closes`() {
        val sheet = GuideController(
            HelpTopic("sheet", "Лист", R.drawable.ic_help, "", listOf(
                GuideStep("Откройте лист", StepDone.Shown(GuideTarget.DAY_SELECTOR)),
                GuideStep("Выберите день", StepDone.Visited(GuideTarget.DAY_SELECTOR)),
            )),
            clock,
        )
        sheet.shown(GuideTarget.DAY_SELECTOR)
        assertEquals(1, sheet.index)
        sheet.hidden(GuideTarget.DAY_SELECTOR)
        assertTrue(sheet.finished)
    }

    @Test
    fun `children step passes when a child is added`() {
        val masha = Child(1, "Маша", null)
        val children = GuideController(
            HelpTopic("children", "Дети", R.drawable.ic_help, "", listOf(
                GuideStep("Добавьте ребёнка", StepDone.Children { now, atStart -> now.size > atStart.size }),
            )),
            clock,
        )
        children.onChildren(listOf(masha))
        assertEquals(0, children.index)
        children.onChildren(listOf(masha, Child(2, "Петя", null)))
        assertTrue(children.finished)
    }

    @Test
    fun `shown step passes when the target appears, and the guide finishes`() {
        guide.shown(GuideTarget.FEEDING_TILE)
        guide.onEvents(listOf(sleep))
        guide.press()
        assertTrue(guide.finished)
        assertNull(guide.target)
        assertNull(guide.sheetHint)
    }
}
