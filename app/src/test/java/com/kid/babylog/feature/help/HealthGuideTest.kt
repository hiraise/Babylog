package com.kid.babylog.feature.help

import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.Temperature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import androidx.compose.ui.test.junit4.createComposeRule
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Гайд «health» (ADR-0043): температура, лекарство по подсказке, болезнь от начала до «Выздоровела». */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class HealthGuideTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    private val guide = GuideHarness(compose, "health")

    @Test
    fun `guide passes from the first step to done`() {
        guide.show()
        guide.tap("Здоровье")
        guide.tap("Температура")
        guide.typeInto("Температура", "37,8")
        guide.tap("Сохранить")
        assertEquals(378, guide.records<Temperature>().maxBy { it.start }.tenths)

        guide.tap("Здоровье")
        guide.tap("Лекарство")
        guide.type("Ви")
        guide.tap("Витамин D")
        guide.tap("Сохранить")

        guide.tap("Здоровье")
        guide.tap("Заболела")
        guide.tap("Начать")
        assertTrue(guide.records<Illness>().any { it.isActive })

        guide.tapAction("открыть болезнь")
        guide.tap("Выздоровела")
        guide.tapLast("Выздоровела")
        assertTrue(guide.records<Illness>().none { it.isActive })
        guide.assertDone()
    }
}
