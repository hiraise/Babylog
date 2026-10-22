package com.kid.babylog.feature.help

import com.kid.babylog.core.model.Bottle
import com.kid.babylog.core.model.Feeding
import com.kid.babylog.core.model.Sleep
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

/** Гайд «sleep-feeding» (ADR-0043): сон 40 минут перемоткой часов, кормление со сменой груди и бутылочка — по настоящему экрану. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class SleepFeedingGuideTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    private val guide = GuideHarness(compose, "sleep-feeding")

    @Test
    fun `guide passes from the first step to done`() {
        guide.show()
        guide.tapAction("начать сон")
        guide.tap("Запустить таймер")
        guide.next("Прошло 40 минут")
        guide.tapAction("проснулась")
        guide.next("Понятно")
        val sleep = guide.records<Sleep>().maxBy { it.start }
        assertEquals(40 * MINUTE, sleep.end!! - sleep.start)

        guide.tapAction("начать кормление")
        guide.tap("Левая грудь")
        guide.tap("Запустить таймер")
        guide.tapIcon("Сменить грудь")
        guide.next("Прошло 15 минут")
        guide.tapAction("закончить кормление")
        assertTrue(guide.records<Feeding>().none { it.isActive })

        guide.tapAction("начать кормление")
        guide.tap("Бутылочка")
        guide.tap("Сохранить")
        assertEquals(guide.clock.millis() + 55 * MINUTE, guide.records<Bottle>().maxOf { it.start })
        guide.assertDone()
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
