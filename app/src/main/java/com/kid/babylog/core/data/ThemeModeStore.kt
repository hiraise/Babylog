package com.kid.babylog.core.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Тема приложения (ADR-0017): как в системе, всегда светлая или всегда тёмная. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Тёмная ли тема на экране: выбранная в приложении или, при [ThemeMode.SYSTEM], системная [systemDark]. */
fun ThemeMode.isDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

/**
 * Выбранная тема; переживает перезапуск. Это настройка телефона, а не дневника: в базу и CSV
 * она не попадает. Применяет её `MainActivity` (ADR-0017).
 */
interface ThemeModeStore {
    val mode: StateFlow<ThemeMode>

    fun select(mode: ThemeMode)
}

class PrefsThemeModeStore(context: Context) : ThemeModeStore {

    private val prefs = context.getSharedPreferences("babylog", Context.MODE_PRIVATE)

    override val mode = MutableStateFlow(
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY, null) } ?: ThemeMode.SYSTEM
    )

    override fun select(mode: ThemeMode) {
        prefs.edit().putString(KEY, mode.name).apply()
        this.mode.value = mode
    }

    private companion object {
        const val KEY = "theme_mode"
    }
}
