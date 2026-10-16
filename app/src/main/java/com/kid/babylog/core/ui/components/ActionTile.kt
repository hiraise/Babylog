package com.kid.babylog.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
 * карточке [AppCard]. Высота не меньше 72 dp, содержимое по центру.
 *
 * Используй для ряда однотипных действий «добавить запись» на главном экране: плитки клади
 * в `Row` с `Modifier.weight(…)` и шагом `spacing.s`. Подпись — одно слово в одну строку. Если оно
 * не влезает (крупный шрифт в настройках, узкий экран), подпись уменьшается до 3/4 размера;
 * многоточие — только когда не влезает и так. TalkBack читает подпись как кнопку; иконка декоративна.
 */
@Composable
fun ActionTile(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = BabyLogTheme.spacing
    val labelStyle = MaterialTheme.typography.labelMedium
    // Боковой отступ минимальный: «Кормление» (68 dp в labelMedium) едва влезает в плитку шириной
    // 76 dp — четверть ряда на экране 360 dp. При шрифте крупнее обычного подпись уменьшается сама
    AppCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = spacing.xs)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(vertical = spacing.m),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs, Alignment.CenterVertically),
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
        }
    }
}

@Preview(name = "ActionTile", widthDp = 720)
@Composable
private fun ActionTilePreview() = LightDarkPreview {
    // Ряд главного экрана: четыре плитки поровну на ширине телефона 360 dp
    Row(horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        ActionTile(R.drawable.ic_feeding, "Кормление", onClick = {}, modifier = Modifier.weight(1f))
        ActionTile(R.drawable.ic_diaper, "Подгузник", onClick = {}, modifier = Modifier.weight(1f))
        ActionTile(R.drawable.ic_medicine, "Лекарство", onClick = {}, modifier = Modifier.weight(1f))
        ActionTile(R.drawable.ic_weight, "Вес", onClick = {}, modifier = Modifier.weight(1f))
    }
}
