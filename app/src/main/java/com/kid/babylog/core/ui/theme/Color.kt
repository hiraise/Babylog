package com.kid.babylog.core.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Fixed-роли по смыслу Material 3 не зависят от темы: одни и те же цвета в светлой и тёмной схеме
private val PrimaryFixed = Color(0xFFC9EDC5)
private val PrimaryFixedDim = Color(0xFF9BD896)
private val OnPrimaryFixed = Color(0xFF002204)
private val OnPrimaryFixedVariant = Color(0xFF1D5A1C)
private val SecondaryFixed = Color(0xFFD8E8D3)
private val SecondaryFixedDim = Color(0xFFBCCCB7)
private val OnSecondaryFixed = Color(0xFF121F10)
private val OnSecondaryFixedVariant = Color(0xFF3B4B38)
private val TertiaryFixed = Color(0xFFFFDEA6)
private val TertiaryFixedDim = Color(0xFFF0C06A)
private val OnTertiaryFixed = Color(0xFF261900)
private val OnTertiaryFixedVariant = Color(0xFF5D4200)

/**
 * Светлая палитра по макету HiMom: светлый фон, белые карточки, зелёный акцент.
 *
 * Заданы все роли `ColorScheme`: роль, оставленная по умолчанию, взяла бы фиолетовый цвет
 * базовой схемы Material 3. Где какая роль (в тёмной палитре — так же):
 * - `background`, `surface` — фон экрана и верхняя панель;
 * - `surfaceContainerLow` — карточки; глубину им даёт тень [Shadows.card], а не тон, поэтому
 *   `surfaceTint` равен `surface`;
 * - `surfaceContainer` — меню; `surfaceContainerHigh` — диалоги и пикеры;
 * - `surfaceContainerHighest` — заливка без акцентного текста: циферблат `TimePicker`, дорожка `Switch`.
 *   Текст `primary` и `error` на нём даёт 4,1–4,5:1, ниже нормы. Поэтому поля ввода — только
 *   `OutlinedTextField`: у `TextField` с заливкой на этом фоне лежат подпись в фокусе и текст ошибки.
 *   Карточки — `AppCard`, а не `Card` из Material 3: у неё тот же фон;
 * - `primary` — акцентный текст, иконки и кнопки; `onSurfaceVariant` — второстепенный текст.
 *
 * Акцент `#348333` темнее макетного `#49B848`, второстепенный текст — `#6E6E6E` вместо `#B0B0B0`:
 * иначе контраст ниже 4,5:1 (ADR-0010). Контраст пар проверяет `ThemeContrastTest`.
 */
internal val LightColors = lightColorScheme(
    primary = Color(0xFF348333),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5EFD1),
    onPrimaryContainer = Color(0xFF0B3A0B),
    inversePrimary = Color(0xFF6CC96B),
    secondary = Color(0xFF4F6B4C),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCEBD8),
    onSecondaryContainer = Color(0xFF142513),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFEC874),
    onTertiaryContainer = Color(0xFF2A1C00),
    background = Color(0xFFF9FAFB),
    onBackground = Color(0xFF040415),
    surface = Color(0xFFF9FAFB),
    onSurface = Color(0xFF040415),
    surfaceVariant = Color(0xFFF2F2F2),
    onSurfaceVariant = Color(0xFF6E6E6E),
    surfaceTint = Color(0xFFF9FAFB),
    inverseSurface = Color(0xFF29323C),
    inverseOnSurface = Color(0xFFF2F4F5),
    error = Color(0xFFC93C3C),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFE0DE),
    onErrorContainer = Color(0xFF5C1210),
    outline = Color(0xFF8C8C8C),
    outlineVariant = Color(0xFFE2E2E2),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFFFFFFF),
    surfaceContainerHighest = Color(0xFFF2F2F2),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceDim = Color(0xFFF2F2F2),
    primaryFixed = PrimaryFixed,
    primaryFixedDim = PrimaryFixedDim,
    onPrimaryFixed = OnPrimaryFixed,
    onPrimaryFixedVariant = OnPrimaryFixedVariant,
    secondaryFixed = SecondaryFixed,
    secondaryFixedDim = SecondaryFixedDim,
    onSecondaryFixed = OnSecondaryFixed,
    onSecondaryFixedVariant = OnSecondaryFixedVariant,
    tertiaryFixed = TertiaryFixed,
    tertiaryFixedDim = TertiaryFixedDim,
    onTertiaryFixed = OnTertiaryFixed,
    onTertiaryFixedVariant = OnTertiaryFixedVariant,
)

/**
 * Тёмная палитра по вариантам «Black var» макета: тёмно-серый фон, карточки `#29323C`,
 * акцент — макетный `#49B848`. Роли используются так же, как в [LightColors]; теней нет
 * (`Shadows.card == null`), карточку от фона отделяет цвет.
 */
internal val DarkColors = darkColorScheme(
    primary = Color(0xFF49B848),
    onPrimary = Color(0xFF0C1F0C),
    primaryContainer = Color(0xFF1F4A1E),
    onPrimaryContainer = Color(0xFFC6EFC2),
    inversePrimary = Color(0xFF2C6F2B),
    secondary = Color(0xFFB5CDB0),
    onSecondary = Color(0xFF1F331C),
    secondaryContainer = Color(0xFF34472F),
    onSecondaryContainer = Color(0xFFD2E9CC),
    tertiary = Color(0xFFFEC874),
    onTertiary = Color(0xFF3A2800),
    tertiaryContainer = Color(0xFF5A4100),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = Color(0xFF22272C),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF22272C),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF36414D),
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceTint = Color(0xFF22272C),
    inverseSurface = Color(0xFFEEF1F3),
    inverseOnSurface = Color(0xFF22272C),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0E0B),
    errorContainer = Color(0xFF6B1E1B),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8B949D),
    outlineVariant = Color(0xFF3B4652),
    scrim = Color(0xFF000000),
    surfaceBright = Color(0xFF2F3944),
    surfaceContainer = Color(0xFF29323C),
    surfaceContainerHigh = Color(0xFF2F3944),
    surfaceContainerHighest = Color(0xFF36414D),
    surfaceContainerLow = Color(0xFF29323C),
    surfaceContainerLowest = Color(0xFF1C2025),
    surfaceDim = Color(0xFF1C2025),
    primaryFixed = PrimaryFixed,
    primaryFixedDim = PrimaryFixedDim,
    onPrimaryFixed = OnPrimaryFixed,
    onPrimaryFixedVariant = OnPrimaryFixedVariant,
    secondaryFixed = SecondaryFixed,
    secondaryFixedDim = SecondaryFixedDim,
    onSecondaryFixed = OnSecondaryFixed,
    onSecondaryFixedVariant = OnSecondaryFixedVariant,
    tertiaryFixed = TertiaryFixed,
    tertiaryFixedDim = TertiaryFixedDim,
    onTertiaryFixed = OnTertiaryFixed,
    onTertiaryFixedVariant = OnTertiaryFixedVariant,
)
