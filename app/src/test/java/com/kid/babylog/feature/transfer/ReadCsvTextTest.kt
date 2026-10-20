package com.kid.babylog.feature.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/** Чтение выбранного файла перед импортом: файл не по размеру и не текст отклоняются до разбора. */
class ReadCsvTextTest {

    @Test
    fun `text file is read as UTF-8`() {
        val bytes = "﻿type,start\r\nNOTE,2026-08-15 21:50:00,,,Маша\r\n".toByteArray()
        assertEquals("﻿type,start\r\nNOTE,2026-08-15 21:50:00,,,Маша\r\n", readCsvText(ByteArrayInputStream(bytes)))
    }

    @Test
    fun `a file bigger than any diary is not read to the end`() {
        var served = 0L
        // Поток без конца, как видео в несколько гигабайт: читаем только до предела
        val endless = object : InputStream() {
            override fun read(): Int = 'a'.code.also { served++ }

            override fun read(b: ByteArray, off: Int, len: Int): Int {
                b.fill('a'.code.toByte(), off, off + len)
                served += len
                return len
            }
        }
        val error = assertThrows(IllegalArgumentException::class.java) { readCsvText(endless) }
        assertEquals("файл больше 20 МБ — это не дневник BabyLog", error.message)
        assertEquals(true, served <= MAX_IMPORT_BYTES + 64 * 1024)
    }

    @Test
    fun `a binary file is rejected`() {
        val jpegStart = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10)
        val error = assertThrows(IllegalArgumentException::class.java) { readCsvText(ByteArrayInputStream(jpegStart)) }
        assertEquals("это не CSV-файл", error.message)
    }
}
