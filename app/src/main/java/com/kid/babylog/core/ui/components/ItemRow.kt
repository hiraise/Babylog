package com.kid.babylog.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Строка записи: иконка `primary` слева, справа заголовок, под ним подзаголовок и заметка,
 * в конце — необязательный слот [trailing] (например, `IconButton` «Удалить»).
 *
 * Используй для записи истории и любой похожей строки «иконка — текст — действие». Сама строка
 * не нажимается и фона не имеет: клади её в [AppCard] (нажатие — через `onClick` карточки) или
 * в другой контейнер. Иконка декоративна: смысл несёт заголовок.
 *
 * @param title главная строка, `bodyLarge` цвета содержимого (`onSurface` на карточке).
 * @param subtitle вторая строка, `bodySmall` `onSurfaceVariant`: время, длительность; пустая не показывается.
 * @param note заметка пользователя, `bodySmall` курсивом `onSurfaceVariant`; пустая не показывается.
 */
@Composable
fun ItemRow(
    @DrawableRes icon: Int,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    note: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = colors.primary)
        // Строки идут вплотную: воздух между ними дают высоты строк стилей
        Column(Modifier.weight(1f)) {
            Text(title, style = typography.bodyLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(subtitle, style = typography.bodySmall, color = colors.onSurfaceVariant)
            }
            if (!note.isNullOrBlank()) {
                Text(note, style = typography.bodySmall, fontStyle = FontStyle.Italic, color = colors.onSurfaceVariant)
            }
        }
        trailing?.invoke()
    }
}

@Preview(name = "ItemRow", widthDp = 720)
@Composable
private fun ItemRowPreview() = LightDarkPreview {
    val spacing = BabyLogTheme.spacing
    // Запись истории: нажатие на карточку — правка, корзина — удаление. Справа отступ меньше:
    // у IconButton своё поле вокруг иконки
    val rowPadding = PaddingValues(start = spacing.l, top = spacing.s, end = spacing.xs, bottom = spacing.s)
    AppCard(Modifier.fillMaxWidth(), onClick = {}, onClickLabel = "изменить запись", contentPadding = rowPadding) {
        ItemRow(
            icon = R.drawable.ic_medicine,
            title = "Лекарство · Витамин D",
            subtitle = "09:15",
            note = "3 капли после еды",
            trailing = {
                IconButton(onClick = {}) {
                    AppIcon(R.drawable.ic_delete, "Удалить", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
    AppCard(Modifier.fillMaxWidth()) {
        ItemRow(icon = R.drawable.ic_sleep, title = "Сон · идёт", subtitle = "с 13:40")
    }
}
