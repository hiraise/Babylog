package com.kid.babylog.feature.help

import com.kid.babylog.core.model.Height
import com.kid.babylog.core.model.Weight
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.createComposeRule
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Гайд «records» (ADR-0043): подгузник, вес и рост записываются в пример дневника. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class RecordsGuideTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    private val guide = GuideHarness(compose, "records")

    @Test
    fun `guide passes from the first step to done`() {
        guide.show()
        guide.tap("Подгузник")
        guide.tap("Пописала")
        guide.tap("Сохранить")
        guide.next()
        guide.next()

        guide.tap("Вес")
        guide.type("6450")
        guide.tap("Сохранить")
        guide.tap("Рост")
        guide.type("64,5")
        guide.tap("Сохранить")

        assertEquals(6450, guide.records<Weight>().maxBy { it.start }.grams)
        assertEquals(645, guide.records<Height>().maxBy { it.start }.millimeters)
        guide.assertDone()
    }
}
