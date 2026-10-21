package com.kid.babylog.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Ввод температуры (ADR-0041): «38,4», «38.4» и «384» — одно и то же, вне 34–43 °C — нет. */
class TemperatureInputTest {

    @Test
    fun `comma, dot and digits mean the same temperature`() {
        assertEquals(384, parseTemperature("38,4"))
        assertEquals(384, parseTemperature("38.4"))
        assertEquals(384, parseTemperature("384"))
        assertEquals(380, parseTemperature("38"))
        assertEquals(366, parseTemperature(" 36,6 "))
    }

    @Test
    fun `incomplete or out of range input is not a temperature`() {
        assertNull(parseTemperature(""))
        assertNull(parseTemperature("3"))
        assertNull(parseTemperature("38,"))
        assertNull(parseTemperature("38,45"))
        assertNull(parseTemperature("33,9"))
        assertNull(parseTemperature("43,1"))
        assertNull(parseTemperature("500"))
    }

    @Test
    fun `typing keeps digits and one decimal comma`() {
        assertEquals("38,4", temperatureInput("38.4"))
        assertEquals("38,4", temperatureInput("38,45"))
        assertEquals("38,", temperatureInput("38,"))
        assertEquals("384", temperatureInput("3845"))
        assertEquals("38,4", temperatureInput("38,4°C"))
        assertEquals("36,6", temperatureInput("366,6"))
    }
}
