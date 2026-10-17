package com.kid.babylog.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.kid.babylog.core.ui.LocalClock
import java.time.Clock

private val DefaultSpacing = Spacing()

/**
 * Тема BabyLog (ADR-0010): цвета, шрифты и формы Material 3, отступы [Spacing] и тени [Shadows].
 * Экраны берут цвета из `MaterialTheme.colorScheme`, стили текста — из `MaterialTheme.typography`,
 * отступы и тени — из [BabyLogTheme.spacing] и [BabyLogTheme.shadows].
 *
 * Динамический цвет (Material You) не используется: цвета одинаковы на всех устройствах.
 *
 * @param clock часы для [LocalClock]: в приложении — `AppContainer.clock`, в превью и тестах — свои.
 * @param darkTheme тёмная тема; по умолчанию — как в системе. В приложении её передаёт `MainActivity`
 *   с учётом темы, выбранной в настройке (ADR-0017).
 */
@Composable
fun BabyLogTheme(
    clock: Clock,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalClock provides clock,
        LocalSpacing provides DefaultSpacing,
        LocalShadows provides if (darkTheme) DarkShadows else LightShadows,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = BabyLogTypography,
            shapes = BabyLogShapes,
            content = content,
        )
    }
}

/** Значения темы, которых нет в `MaterialTheme`; пара к функции [BabyLogTheme], как у `MaterialTheme`. */
object BabyLogTheme {
    /** Шкала отступов: `padding` и `spacedBy` в экранах — только отсюда. */
    val spacing: Spacing
        @Composable @ReadOnlyComposable
        get() = LocalSpacing.current

    /** Тени; в тёмной теме [Shadows.card] равен `null`. */
    val shadows: Shadows
        @Composable @ReadOnlyComposable
        get() = LocalShadows.current
}
