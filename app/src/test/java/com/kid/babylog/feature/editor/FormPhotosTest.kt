package com.kid.babylog.feature.editor

import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Sex
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.util.TimeZone

/** Фото формы подгузника и подпись к ним для «Поделиться» (ADR-0024). */
class FormPhotosTest {

    private val systemZone = TimeZone.getDefault()

    @Before
    fun fixTimeZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun restoreTimeZone() = TimeZone.setDefault(systemZone)

    private val time = Instant.parse("2026-10-17T14:30:00Z").toEpochMilli()
    private val today = LocalDate.parse("2026-10-17")

    @Test
    fun `share text says who, what and when`() {
        val masha = Child(1, " Маша ", Instant.parse("2026-06-05T00:00:00Z").toEpochMilli(), Sex.GIRL)
        assertEquals(
            "Маша, 4 мес 12 дн.\nПодгузник: покакала\n17 октября 2026, 14:30\nКомментарий: зелёный, жидкий",
            diaperShareText(masha, DiaperKind.DIRTY, time, " зелёный, жидкий ", today),
        )
    }

    @Test
    fun `share text without birth date and comment`() {
        assertEquals(
            "Петя\nПодгузник: и то, и то\n17 октября 2026, 14:30",
            diaperShareText(Child(2, "Петя", null, Sex.BOY), DiaperKind.MIXED, time, "  ", today),
        )
    }

    @Test
    fun `saved photos keep their order and new ones go after them`() {
        val photos = listOf(FormPhoto.Saved(7), FormPhoto.Pending("/a"), FormPhoto.Saved(3), FormPhoto.Pending("/gone"))
        val edit = photos.toEdit { path -> if (path == "/a") byteArrayOf(1) else null }
        assertEquals(listOf(7L, 3L), edit.keep)
        assertEquals(listOf(listOf<Byte>(1)), edit.add.map { it.toList() })
    }
}
