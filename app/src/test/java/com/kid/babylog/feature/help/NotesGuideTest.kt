package com.kid.babylog.feature.help

import com.kid.babylog.core.model.Note
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.junit4.createComposeRule
import com.kid.babylog.feature.analytics.TALL_PIXEL_5
import com.kid.babylog.testing.ComponentActivityRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Гайд «notes» (ADR-0043): заметка с меткой, лента «Заметки» в «Дневнике» и сводка метки. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = TALL_PIXEL_5)
class NotesGuideTest {

    @get:Rule(order = 0)
    val utc = UtcRule()

    @get:Rule(order = 1)
    val activity = ComponentActivityRule()

    @get:Rule(order = 2)
    val compose = createComposeRule()

    private val guide = GuideHarness(compose, "notes")

    @Test
    fun `guide passes from the first step to done`() {
        guide.show()
        guide.tap("Заметка")
        guide.type("Тёрла дёсны")
        guide.tap("зубки")
        guide.tap("Сохранить")
        assertEquals(listOf("зубки"), guide.records<Note>().maxBy { it.start }.tags)

        guide.tap("Дневник")
        guide.tap("Заметки")
        guide.tapFirst("зубки", scroll = true)
        guide.next()
        guide.assertDone()
    }
}
