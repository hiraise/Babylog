package com.kid.babylog.core.ui

import androidx.compose.material3.ExperimentalMaterial3Api
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/** Календарь не даёт выбрать день позже сегодняшнего: запись «из будущего» сдвинула бы сводки и «Аналитику». */
@OptIn(ExperimentalMaterial3Api::class)
class NotAfterTest {

    private val today = LocalDate.of(2026, 10, 19)
    private val dates = notAfter(today)

    // DatePicker передаёт день как UTC-миллисекунды его полуночи
    private fun utc(date: LocalDate) = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `today and the past are selectable, tomorrow is not`() {
        assertTrue(dates.isSelectableDate(utc(today)))
        assertTrue(dates.isSelectableDate(utc(LocalDate.of(2024, 2, 29))))
        assertFalse(dates.isSelectableDate(utc(today.plusDays(1))))
    }

    @Test
    fun `next year is not offered`() {
        assertTrue(dates.isSelectableYear(2026))
        assertFalse(dates.isSelectableYear(2027))
    }
}
