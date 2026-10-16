package com.kid.babylog.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.kid.babylog.R

/** Inter — основной текст: 400, 500 и 600 (`FontWeight.SemiBold` для выделения в тексте). */
private val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
)

/** Nunito ExtraBold — округлый шрифт заголовков, таймера и кнопок вместо Baloo из макета (у Baloo нет кириллицы). */
private val Nunito = FontFamily(Font(R.font.nunito_extrabold, FontWeight.ExtraBold))

// Базовые стили Material 3: от них берутся выравнивание строки и платформенные настройки текста
private val Base = Typography()

private fun TextStyle.nunito(size: TextUnit, lineHeight: TextUnit) = copy(
    fontFamily = Nunito,
    fontWeight = FontWeight.ExtraBold,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = 0.sp,
)

private fun TextStyle.inter(weight: FontWeight, size: TextUnit, lineHeight: TextUnit) = copy(
    fontFamily = Inter,
    fontWeight = weight,
    fontSize = size,
    lineHeight = lineHeight,
    letterSpacing = 0.sp,
)

/**
 * Шкала шрифтов BabyLog (ADR-0010): `display*`, `headline*` и `labelLarge` — Nunito ExtraBold,
 * остальное — Inter.
 *
 * - `displayMedium` — таймер сна. У Nunito нет OpenType-функции `tnum`, но она и не нужна: цифры
 *   в нём одной ширины (600/1000 em) и без кернинга между собой, так что таймер не прыгает.
 *   У Inter цифры пропорциональные: для ровных колонок чисел добавляй `fontFeatureSettings = "tnum"`.
 * - `headlineSmall` — заголовки диалогов и название приложения в шапке.
 * - `labelLarge` — кнопки, `TextButton` и чипы. Material 3 сам берёт его ещё для пунктов
 *   `DropdownMenuItem` и подзаголовка «Выберите дату» в `DatePicker`. Подзаголовок пусть остаётся
 *   таким, а пункт меню — обычный текст: задавай ему `Text(…, style = MaterialTheme.typography.bodyLarge)`,
 *   как у строки списка.
 */
internal val BabyLogTypography = Typography(
    displayLarge = Base.displayLarge.nunito(57.sp, 64.sp),
    displayMedium = Base.displayMedium.nunito(45.sp, 52.sp),
    displaySmall = Base.displaySmall.nunito(36.sp, 44.sp),
    headlineLarge = Base.headlineLarge.nunito(32.sp, 40.sp),
    headlineMedium = Base.headlineMedium.nunito(28.sp, 36.sp),
    headlineSmall = Base.headlineSmall.nunito(24.sp, 32.sp),
    titleLarge = Base.titleLarge.inter(FontWeight.Medium, 20.sp, 26.sp),
    titleMedium = Base.titleMedium.inter(FontWeight.Medium, 16.sp, 22.sp),
    titleSmall = Base.titleSmall.inter(FontWeight.Medium, 14.sp, 20.sp),
    bodyLarge = Base.bodyLarge.inter(FontWeight.Normal, 16.sp, 22.sp),
    bodyMedium = Base.bodyMedium.inter(FontWeight.Normal, 14.sp, 20.sp),
    bodySmall = Base.bodySmall.inter(FontWeight.Normal, 12.sp, 16.sp),
    labelLarge = Base.labelLarge.nunito(16.sp, 20.sp),
    labelMedium = Base.labelMedium.inter(FontWeight.Medium, 12.sp, 16.sp),
    labelSmall = Base.labelSmall.inter(FontWeight.Medium, 11.sp, 16.sp),
)
