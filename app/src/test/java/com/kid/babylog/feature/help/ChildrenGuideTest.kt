package com.kid.babylog.feature.help

import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.createComposeRule
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Гайд «children» (ADR-0043): второй ребёнок добавляется в пример и выбирается, потом — обратно Соня. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class ChildrenGuideTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    private val guide = GuideHarness(compose, "children")

    @Test
    fun `guide passes from the first step to done`() {
        guide.show()
        guide.tapAction("выбрать ребёнка")
        guide.tap("Добавить ребёнка")
        guide.type("Миша")
        guide.tap("Сохранить")
        assertEquals(listOf("Соня", "Миша"), guide.example.children.value.map { it.name })

        guide.tapAction("выбрать ребёнка")
        guide.tap("Соня")
        guide.next()
        guide.assertDone()
    }
}
