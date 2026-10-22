package com.kid.babylog.feature.help

import com.kid.babylog.core.model.Crying
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.createComposeRule
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Гайд «crying» (ADR-0043): плач 10 минут перемоткой, причина сохраняется в ту же запись. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class CryingGuideTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    private val guide = GuideHarness(compose, "crying")

    @Test
    fun `guide passes from the first step to done`() {
        guide.show()
        guide.tapAction("начать плач")
        guide.next("Прошло 10 минут")
        guide.tapAction("закончить плач")
        guide.tap("колики")
        guide.tap("Сохранить")
        guide.next()

        val crying = guide.records<Crying>().maxBy { it.start }
        assertEquals(listOf("колики"), crying.reasons)
        assertEquals(10 * 60_000L, crying.end!! - crying.start)
        guide.assertDone()
    }
}
