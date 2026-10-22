package com.kid.babylog.core.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Тема приложения (ADR-0017): как в системе, всегда светлая или всегда тёмная. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/**
 * Палитра тёмной темы (ADR-0032); светлая тема одна. Цвета палитр — `darkColors` в `core/ui/theme/`,
 * [CLASSIC] — прежняя зелёная тёмная тема по макету.
 */
enum class DarkPalette {
    DUSK, NIGHTLIGHT, SAGE, POWDER, CLASSIC;

    companion object {
        /**
         * Палитра по умолчанию — «Классика» (ADR-0044): её фон и акцент повторяют `window_background`
         * и `shade_*` в `values-night/colors.xml`.
         */
        val DEFAULT = CLASSIC
    }
}

/** Выбор темы: режим (ADR-0017) и палитра на случай, когда тема на экране тёмная (ADR-0032). */
data class ThemeChoice(
    val mode: ThemeMode = ThemeMode.SYSTEM,
    val darkPalette: DarkPalette = DarkPalette.DEFAULT,
)

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
    val choice: StateFlow<ThemeChoice>

    fun select(choice: ThemeChoice)
}

class PrefsThemeModeStore(context: Context) : ThemeModeStore {

    private val prefs = context.getSharedPreferences("babylog", Context.MODE_PRIVATE)

    override val choice = MutableStateFlow(
        ThemeChoice(
            mode = prefs.enumValue(MODE_KEY, ThemeMode.SYSTEM),
            darkPalette = prefs.enumValue(PALETTE_KEY, DarkPalette.DEFAULT),
        )
    )

    override fun select(choice: ThemeChoice) {
        prefs.edit()
            .putString(MODE_KEY, choice.mode.name)
            .putString(PALETTE_KEY, choice.darkPalette.name)
            .apply()
        this.choice.value = choice
    }

    private companion object {
        const val MODE_KEY = "theme_mode"
        const val PALETTE_KEY = "dark_palette"
    }
}

/** Значение перечисления по имени из настроек; нет или незнакомое имя — [default]. */
private inline fun <reified T : Enum<T>> SharedPreferences.enumValue(key: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == getString(key, null) } ?: default
