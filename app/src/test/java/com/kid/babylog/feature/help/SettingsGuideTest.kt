package com.kid.babylog.feature.help

import androidx.compose.ui.test.junit4.createComposeRule
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Гайд «settings» (ADR-0043): меню ⋮ → «Тема», лист темы закрывается «Назад»; остальное — пояснения. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class SettingsGuideTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    private val guide = GuideHarness(compose, "settings")

    @Test
    fun `guide passes from the first step to done`() {
        guide.show()
        guide.tapIcon("Меню")
        guide.tap("Тема")
        guide.back()
        guide.next()
        guide.next()
        guide.next()
        guide.assertDone()
    }
}
