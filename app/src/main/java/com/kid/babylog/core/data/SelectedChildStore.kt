package com.kid.babylog.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Какой ребёнок выбран на экране. Выбор переживает перезапуск приложения. */
interface SelectedChildStore {
    /** id выбранного ребёнка; null — ещё не выбирали (показывается первый). */
    val selectedChildId: StateFlow<Long?>

    fun select(childId: Long)
}

class PrefsSelectedChildStore(context: Context) : SelectedChildStore {

    private val prefs = context.getSharedPreferences("babylog", Context.MODE_PRIVATE)

    override val selectedChildId = MutableStateFlow(
        prefs.getLong(KEY, NONE).takeIf { it != NONE }
    )

    override fun select(childId: Long) {
        prefs.edit().putLong(KEY, childId).apply()
        selectedChildId.value = childId
    }

    private companion object {
        const val KEY = "selected_child_id"
        const val NONE = -1L
    }
}
