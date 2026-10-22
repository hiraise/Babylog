package com.kid.babylog.feature.help

import androidx.compose.ui.test.junit4.createComposeRule
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Гайд «analytics» (ADR-0043): обзор плиток, раздел «Сон» и назад к обзору. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class AnalyticsGuideTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    private val guide = GuideHarness(compose, "analytics")

    @Test
    fun `guide passes from the first step to done`() {
        guide.show()
        guide.tap("Аналитика")
        guide.next()
        guide.tapTile("Сон")
        guide.next()
        guide.tapIcon("Назад к обзору")
        guide.assertDone()
    }
}
