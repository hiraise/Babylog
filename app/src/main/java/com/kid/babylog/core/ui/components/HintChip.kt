package com.kid.babylog.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Чип-подсказка — обёртка `SuggestionChip`: касание подставляет готовое значение, например название
 * лекарства из прежних записей.
 *
 * Выглядит как невыбранный [ChoiceChip]: фон `surfaceContainerLow`, текст `onSurfaceVariant`, рамки
 * нет, в светлой теме — мягкая тень темы по форме `MaterialTheme.shapes.small`, подпись `labelLarge`
 * в одну строку с многоточием. Но для TalkBack это кнопка, а не радиокнопка: подсказка ничего
 * не выбирает. Место под палец — 48 dp, как у [ChoiceChip]. Чипы клади в `FlowRow` с шагом `spacing.s`.
 */
@Composable
fun HintChip(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    // Место под палец резервирует Box, а не сам чип: иначе тень легла бы по рамке 48 dp (см. ChoiceChip)
    Box(modifier.minimumInteractiveComponentSize(), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            SuggestionChip(
                onClick = onClick,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                modifier = Modifier.cardShadow(shape, BabyLogTheme.shadows.card),
                shape = shape,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = colors.surfaceContainerLow,
                    labelColor = colors.onSurfaceVariant,
                ),
                // Глубину даёт только тень темы, своя тень Material 3 не нужна
                elevation = null,
                border = null,
            )
        }
    }
}

@Preview(name = "HintChip", widthDp = 720)
@Composable
private fun HintChipPreview() = LightDarkPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        HintChip(label = "Витамин D", onClick = {})
        HintChip(label = "Нурофен", onClick = {})
    }
}
