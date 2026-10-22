package com.kid.babylog.feature.help

import com.kid.babylog.feature.development.AssetTipsSource
import org.robolectric.RuntimeEnvironment
import androidx.compose.ui.test.junit4.createComposeRule
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Гайд «development» (ADR-0043): советы недели и экран навыков — на настоящих файлах советов. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class DevelopmentGuideTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    private val guide = GuideHarness(compose, "development", tips = AssetTipsSource(RuntimeEnvironment.getApplication()))

    @Test
    fun `guide passes from the first step to done`() {
        guide.show()
        guide.tap("Развитие")
        guide.next()
        guide.tap("подробнее")
        guide.assertDone()
    }
}
