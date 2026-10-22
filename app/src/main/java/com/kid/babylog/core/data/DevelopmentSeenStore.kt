package com.kid.babylog.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Какую неделю жизни раздел «Развитие» уже открывали у каждого ребёнка (ADR-0037): пока текущую не
 * открывали — на пункте панели точка, на полосе советов — «новые». Это настройка телефона, а не дневника:
 * в базу и CSV она не попадает.
 */
interface DevelopmentSeenStore {
    /** Последняя открытая неделя жизни по id ребёнка. */
    val seenWeeks: StateFlow<Map<Long, Int>>

    fun markSeen(childId: Long, week: Int)
}

class PrefsDevelopmentSeenStore(context: Context) : DevelopmentSeenStore {

    private val prefs = context.getSharedPreferences("babylog", Context.MODE_PRIVATE)

    override val seenWeeks = MutableStateFlow(
        prefs.all.mapNotNull { (key, value) ->
            val childId = key.removePrefix(KEY_PREFIX).takeIf { key.startsWith(KEY_PREFIX) }?.toLongOrNull()
            if (childId != null && value is Int) childId to value else null
        }.toMap()
    )

    override fun markSeen(childId: Long, week: Int) {
        prefs.edit().putInt(KEY_PREFIX + childId, week).apply()
        seenWeeks.value += childId to week
    }

    private companion object {
        const val KEY_PREFIX = "development_seen_week_"
    }
}

/** «Развитие» в примере дневника (ADR-0043): открытые там недели не гасят точку в настоящем дневнике. */
class MemoryDevelopmentSeenStore : DevelopmentSeenStore {
    override val seenWeeks = MutableStateFlow<Map<Long, Int>>(emptyMap())

    override fun markSeen(childId: Long, week: Int) {
        seenWeeks.value += childId to week
    }
}
