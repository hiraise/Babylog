package com.kid.babylog.feature.help

import androidx.compose.ui.test.junit4.createComposeRule
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Гайд «diary» (ADR-0043): «Дневник» по дням — вчера, выбор дня из списка и правка записи. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class DiaryGuideTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    private val guide = GuideHarness(compose, "diary")

    @Test
    fun `guide passes from the first step to done`() {
        guide.show()
        guide.tap("Дневник")
        guide.tapIcon("Предыдущий день")
        guide.tap("Вчера")
        guide.tapFirst("13 октября, вторник")
        guide.tapAction("изменить запись")
        guide.tap("Отмена")
        guide.assertDone()
    }
}
