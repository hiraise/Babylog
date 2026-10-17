package com.kid.babylog.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Плитка быстрого действия: иконка `primary` над короткой подписью `labelMedium`, на нажимаемой
 * карточке [AppCard]. Высота не меньше 72 dp, содержимое по центру по горизонтали и сверху: в ряду
 * плиток разной высоты иконки и подписи стоят на одной линии.
 *
 * Используй для ряда однотипных действий «добавить запись» на главном экране: плитки клади
 * в `Row` с `Modifier.weight(…)` и шагом `spacing.s`. Подпись — одно слово в одну строку. Если оно
 * не влезает (крупный шрифт в настройках, узкий экран), подпись уменьшается до 3/4 размера;
 * многоточие — только когда не влезает и так. TalkBack читает подпись как кнопку; иконка декоративна.
 *
 * [caption] — пояснение под подписью (`bodySmall`, `onSurfaceVariant`), например «2 ч 10 мин назад»,
 * до двух строк. Строку про запас не держи: на обычном шрифте пояснение влезает в одну, и пустая
 * вторая только раздувает ряд. [highlight] — пояснение важно сейчас: `labelMedium` цвета `primary`,
 * как идущий таймер; смысл несёт сам текст, цвет только привлекает внимание. Плитки с пояснением
 * и без в одном ряду выравнивай по высоте: `Row(Modifier.height(IntrinsicSize.Min))` и
 * `fillMaxHeight()` у плиток.
 */
@Composable
fun ActionTile(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    caption: String? = null,
    highlight: Boolean = false,
) {
    val spacing = BabyLogTheme.spacing
    val labelStyle = MaterialTheme.typography.labelMedium
    // Боковой отступ минимальный: «Кормление» (68 dp в labelMedium) едва влезает в плитку шириной
    // 76 dp — четверть ряда на экране 360 dp. При шрифте крупнее обычного подпись уменьшается сама
    AppCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = spacing.xs)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .heightIn(min = 72.dp)
                .padding(vertical = spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.Top),
        ) {
            AppIcon(
                id = icon,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = label,
                style = labelStyle,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // Слово целиком важнее размера: на 360 dp влезает до масштаба шрифта ×1,3
                autoSize = TextAutoSize.StepBased(
                    minFontSize = labelStyle.fontSize * 0.75f,
                    maxFontSize = labelStyle.fontSize,
                ),
            )
            if (caption != null) {
                Text(
                    text = caption,
                    style = if (highlight) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodySmall,
                    color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Preview(name = "ActionTile", widthDp = 720)
@Composable
private fun ActionTilePreview() = LightDarkPreview {
    // Ряд главного экрана на 360 dp: у подгузника пояснение, обычное и выделенное
    for (highlight in listOf(false, true)) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
            val tile = Modifier.weight(1f).fillMaxHeight()
            ActionTile(
                R.drawable.ic_diaper,
                "Подгузник",
                onClick = {},
                modifier = tile,
                caption = if (highlight) "3 ч 20 мин назад" else "25 мин назад",
                highlight = highlight,
            )
            ActionTile(R.drawable.ic_medicine, "Лекарство", onClick = {}, modifier = tile)
            ActionTile(R.drawable.ic_weight, "Вес", onClick = {}, modifier = tile)
        }
    }
}
