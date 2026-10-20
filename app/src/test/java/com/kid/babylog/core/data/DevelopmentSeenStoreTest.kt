package com.kid.babylog.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DevelopmentSeenStoreTest {

    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun `seen week is kept per child and survives restart`() {
        val store = PrefsDevelopmentSeenStore(context)
        assertEquals(emptyMap<Long, Int>(), store.seenWeeks.value)

        store.markSeen(childId = 1, week = 21)
        store.markSeen(childId = 2, week = 3)
        store.markSeen(childId = 1, week = 22)

        assertEquals(mapOf(1L to 22, 2L to 3), store.seenWeeks.value)
        assertEquals(mapOf(1L to 22, 2L to 3), PrefsDevelopmentSeenStore(context).seenWeeks.value)
    }

    @Test
    fun `other settings in the same file are ignored`() {
        context.getSharedPreferences("babylog", 0).edit().putLong("selected_child_id", 5).apply()
        assertEquals(emptyMap<Long, Int>(), PrefsDevelopmentSeenStore(context).seenWeeks.value)
    }
}
