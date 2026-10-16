package com.kid.babylog.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarInitialTest {

    @Test
    fun `takes the first letter in upper case`() {
        assertEquals("М", avatarInitial("маша"))
        assertEquals("Ё", avatarInitial("ёлка"))
        assertEquals("П", avatarInitial("Петя Иванов"))
        assertEquals("A", avatarInitial("alice"))
    }

    @Test
    fun `skips spaces punctuation and emoji before the letter`() {
        assertEquals("П", avatarInitial("  «Петя»"))
        assertEquals("В", avatarInitial("🐣 Вася"))
        assertEquals("Д", avatarInitial("(2) Даша"))
    }

    @Test
    fun `keeps combining marks with the letter`() {
        // «й», набранная как «и» + комбинируемая кратка U+0306
        assertEquals("Й", avatarInitial("йван"))
    }

    @Test
    fun `returns null when the name has no letters`() {
        assertNull(avatarInitial(""))
        assertNull(avatarInitial("   "))
        assertNull(avatarInitial("🐣"))
        assertNull(avatarInitial("123"))
    }
}
