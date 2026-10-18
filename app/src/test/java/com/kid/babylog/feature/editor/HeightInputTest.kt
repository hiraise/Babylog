package com.kid.babylog.feature.editor

import com.kid.babylog.core.ui.formatHeight
import com.kid.babylog.core.ui.heightCentimeters
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Рост вводят в сантиметрах с десятыми, а хранят в миллиметрах (ADR-0027). */
class HeightInputTest {

    @Test
    fun `input keeps centimeters and one tenth`() {
        assertEquals("62,5", heightInput("62.5"))
        assertEquals("62,5", heightInput("62,57"))
        assertEquals("62,", heightInput("62,"))
        assertEquals("100", heightInput("1000"))
        assertEquals("62,5", heightInput("6 2,5 см"))
        assertEquals("62,5", heightInput("62,5,3"))
    }

    @Test
    fun `parsed height is millimeters within range`() {
        assertEquals(625, parseHeight("62,5"))
        assertEquals(620, parseHeight("62"))
        assertEquals(620, parseHeight("62,"))
        assertEquals(200, parseHeight("20"))
        assertEquals(1500, parseHeight("150"))
        listOf("", ",5", "19,9", "150,1").forEach { assertNull(it, parseHeight(it)) }
    }

    @Test
    fun `height is shown in centimeters without a zero tenth`() {
        assertEquals("62,5 см", formatHeight(625))
        assertEquals("62 см", formatHeight(620))
        // Так же рост подставляется в поле при правке записи — и читается обратно тем же числом
        listOf(625, 620, 1005).forEach { assertEquals(it, parseHeight(heightCentimeters(it))) }
    }
}
