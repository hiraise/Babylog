package com.kid.babylog.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Чип выбора варианта — обёртка `FilterChip` в стиле макета: вид подгузника, грудь, «5 мин назад»,
 * пол ребёнка.
 *
 * Не выбран — фон `surfaceContainerLow`, текст `onSurfaceVariant`; выбран — фон `primary`, текст
 * `onPrimary`. Рамки нет; в светлой теме — мягкая тень темы по форме `MaterialTheme.shapes.small`.
 * Подпись — `labelLarge` в одну строку, лишнее обрезается многоточием. Необязательная [icon] стоит
 * перед подписью, 18 dp, цвета подписи. Недоступный чип (`enabled = false`) — без тени и с бледной
 * подписью, как в Material 3.
 *
 * Используй вместо `FilterChip` везде, где выбирают одно значение из нескольких: для TalkBack чип —
 * радиокнопка, выбранный чип повторно не нажимается. Чипы клади в `Row` или `FlowRow` с шагом
 * `spacing.s` и помечай ряд `Modifier.selectableGroup()`: TalkBack объявит их одной группой.
 */
@Composable
fun ChoiceChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = null,
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    // FilterChip сам дотягивает свою раскладку до 48 dp под палец, и тень из его modifier легла бы
    // по этой увеличенной рамке, а не по видимому чипу высотой 32 dp. Поэтому место под палец
    // резервирует внешний Box, а внутри минимум отключён: тень совпадает с формой чипа. Касание
    // рядом с чипом до него всё равно доходит — Compose сам расширяет область касания до 48 dp.
    Box(modifier.minimumInteractiveComponentSize(), propagateMinConstraints = true) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            FilterChip(
                selected = selected,
                onClick = onClick,
                label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                // Цвет иконки FilterChip задаёт через LocalContentColor — его и берёт AppIcon
                leadingIcon = icon?.let {
                    { AppIcon(it, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                },
                // FilterChip объявляет себя флажком (Role.Checkbox), будто можно выбрать несколько.
                // Семантика узла собирается от внутренних модификаторов к внешним, поэтому роль,
                // заданная здесь, снаружи, перекрывает внутреннюю
                modifier = Modifier
                    .cardShadow(shape, if (enabled) BabyLogTheme.shadows.card else null)
                    .semantics { role = Role.RadioButton },
                enabled = enabled,
                shape = shape,
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = colors.surfaceContainerLow,
                    labelColor = colors.onSurfaceVariant,
                    iconColor = colors.onSurfaceVariant,
                    disabledContainerColor = colors.surfaceContainerLow,
                    selectedContainerColor = colors.primary,
                    selectedLabelColor = colors.onPrimary,
                    selectedLeadingIconColor = colors.onPrimary,
                    selectedTrailingIconColor = colors.onPrimary,
                ),
                // Глубину даёт только тень темы, своя тень Material 3 не нужна
                elevation = null,
                border = null,
            )
        }
    }
}

@Preview(name = "ChoiceChip", widthDp = 720)
@Composable
private fun ChoiceChipPreview() = LightDarkPreview {
    val gap = Arrangement.spacedBy(BabyLogTheme.spacing.s)
    Row(Modifier.selectableGroup(), horizontalArrangement = gap) {
        ChoiceChip(selected = true, onClick = {}, label = "Пописал", icon = R.drawable.ic_diaper_wet)
        ChoiceChip(selected = false, onClick = {}, label = "Покакал", icon = R.drawable.ic_diaper_dirty)
        ChoiceChip(selected = false, onClick = {}, label = "И то, и то", icon = R.drawable.ic_diaper_mixed)
    }
    Row(Modifier.selectableGroup(), horizontalArrangement = gap) {
        ChoiceChip(selected = false, onClick = {}, label = "Сейчас")
        ChoiceChip(selected = true, onClick = {}, label = "−5 мин")
        ChoiceChip(selected = false, onClick = {}, label = "−15 мин")
        ChoiceChip(selected = false, onClick = {}, label = "−1 ч")
    }
    // Недоступные: без тени, подпись бледная
    Row(Modifier.selectableGroup(), horizontalArrangement = gap) {
        ChoiceChip(selected = false, onClick = {}, label = "Недоступен", enabled = false)
        ChoiceChip(selected = true, onClick = {}, label = "Выбран, недоступен", enabled = false)
    }
}
