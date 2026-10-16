package com.kid.babylog.core.ui

import androidx.compose.runtime.staticCompositionLocalOf
import java.time.Clock

/** Часы для UI («сейчас», таймеры). Подменяются в превью и тестах; в приложении — из `AppContainer`. */
val LocalClock = staticCompositionLocalOf<Clock> { Clock.systemDefaultZone() }
