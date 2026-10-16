package com.kid.babylog.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Карточка макета (ADR-0010): фон `surfaceContainerLow`, форма `MaterialTheme.shapes.medium`,
 * в светлой теме — мягкая тень [BabyLogTheme.shadows] (в тёмной теней нет, карточку отделяет цвет).
 * Текст внутри по умолчанию `onSurface`.
 *
 * Используй для любого блока экрана на карточке: ребёнок, сон, сводка дня, запись истории.
 * Не бери `Card` и `ElevatedCard` из Material 3: у `Card` фон `surfaceContainerHighest`,
 * у `ElevatedCard` своя тень вместо тени макета.
 *
 * @param onClick нажатие на всю карточку; `null` — карточка не нажимается. Нажимаемая карточка
 *   для TalkBack — кнопка, её текст читается одной фразой; волна нажатия не выходит за скругления.
 * @param onClickLabel что сделает нажатие, для TalkBack: «изменить запись»; без него TalkBack
 *   скажет общее «активировать».
 * @param contentPadding отступ содержимого от края карточки; по умолчанию `spacing.l`.
 * @param content содержимое — `Column` сверху вниз без промежутков: шаг между строками задавай
 *   вложенной колонкой с `Arrangement.spacedBy(spacing.…)`.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    contentPadding: PaddingValues = PaddingValues(BabyLogTheme.spacing.l),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.medium
    val colors = MaterialTheme.colorScheme
    // Цвет содержимого задан снаружи Column: от него берёт цвет и волна нажатия
    CompositionLocalProvider(LocalContentColor provides colors.onSurface) {
        Column(
            modifier = modifier
                .cardShadow(shape, BabyLogTheme.shadows.card)
                .clip(shape)
                .background(colors.surfaceContainerLow)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = null,
                            indication = ripple(),
                            onClickLabel = onClickLabel,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                .padding(contentPadding),
            content = content,
        )
    }
}

/**
 * Мягкая тень темы по форме элемента; `shadow == null` (тёмная тема) — без тени.
 * Ставь в цепочке раньше `clip` и `background`: иначе клип обрежет тень.
 */
internal fun Modifier.cardShadow(shape: Shape, shadow: Shadow?): Modifier =
    if (shadow != null) dropShadow(shape, shadow) else this

@Preview(name = "AppCard", widthDp = 720)
@Composable
private fun AppCardPreview() = LightDarkPreview {
    val spacing = BabyLogTheme.spacing
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text("Сегодня", style = MaterialTheme.typography.titleMedium)
            Text("Кормлений: 6 · последнее: левая, 25 мин назад", style = MaterialTheme.typography.bodyMedium)
            Text("Сон: 9 ч 40 мин", style = MaterialTheme.typography.bodyMedium)
        }
    }
    AppCard(Modifier.fillMaxWidth(), onClick = {}, onClickLabel = "изменить данные ребёнка") {
        Text("Маша", style = MaterialTheme.typography.titleMedium)
        Text(
            "Нажимаемая карточка: волна в границах скругления",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
