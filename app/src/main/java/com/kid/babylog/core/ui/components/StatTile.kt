package com.kid.babylog.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kid.babylog.core.ui.theme.BabyLogTheme

private val TileIconSize = 18.dp

/** Насколько главное число может уменьшиться, чтобы влезть в плитку: «12 ч 32 мин» на 360 dp. */
private const val MIN_VALUE_SCALE = 0.7f

/**
 * Плитка обзора «Аналитики» (ADR-0029): иконка и название, главное число [value] (`headlineSmall`;
 * в узкой плитке уменьшается само, но не меньше чем до 70 %), под ним мелко — к чему оно ([unit]:
 * «в сутки», дата замера) и сравнение [note], внизу — мини-график [chart]. Нажимается вся плитка:
 * [onClick] открывает раздел, TalkBack читает «подробнее». Плитки ряда — одной высоты:
 * `Row(Modifier.height(IntrinsicSize.Min))` и `fillMaxHeight()` у плиток, график прижат к низу.
 */
@Composable
fun StatTile(
    @DrawableRes icon: Int,
    title: String,
    value: String,
    unit: String?,
    note: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    chart: (@Composable () -> Unit)? = null,
) {
    val spacing = BabyLogTheme.spacing
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val valueStyle = MaterialTheme.typography.headlineSmall
    AppCard(modifier = modifier, onClick = onClick, onClickLabel = "подробнее", contentPadding = PaddingValues(spacing.m)) {
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                AppIcon(icon, contentDescription = null, modifier = Modifier.size(TileIconSize), tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column {
                Text(
                    value,
                    style = valueStyle,
                    maxLines = 1,
                    autoSize = TextAutoSize.StepBased(minFontSize = valueStyle.fontSize * MIN_VALUE_SCALE, maxFontSize = valueStyle.fontSize),
                )
                unit?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                // Сравнение — до двух строк: «−1 ч 5 мин к прошлым 30 дням» в одну не влезает
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
            if (chart != null) {
                Spacer(Modifier.weight(1f))
                chart()
            }
        }
    }
}
