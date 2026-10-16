package com.kid.babylog.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.kid.babylog.R
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatTimer
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Карточка таймера, который ещё не идёт (сон, кормление): строка «иконка `primary` — [title] —
 * кнопка [startLabel]». Идущий таймер показывай [RunningTimerCard].
 */
@Composable
fun TimerStartCard(@DrawableRes icon: Int, title: String, startLabel: String, onStart: () -> Unit) {
    val spacing = BabyLogTheme.spacing
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = spacing.l, vertical = spacing.m),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            AppIcon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Button(onClick = onStart) { Text(startLabel) }
        }
    }
}

/**
 * Идущий таймер: по центру [title], «с HH:mm», время с [start] крупными цифрами и кнопки [actions]
 * столбиком во всю ширину (`Modifier.fillMaxWidth()` у каждой). Первой ставь кнопку, которая
 * останавливает таймер (`Button`), — она на одном месте у всех таймеров; остальные — `OutlinedButton`.
 * Столбиком, а не в ряд: на экране 360 dp две подписи вроде «Сменить грудь» в ряд не помещаются.
 */
@Composable
fun RunningTimerCard(title: String, start: Long, now: Long, actions: @Composable ColumnScope.() -> Unit) {
    val spacing = BabyLogTheme.spacing
    AppCard(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.xs),
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                "с ${formatClock(start)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Цифры Nunito одной ширины: строка таймера не дрожит, когда меняются секунды
            Text(
                formatTimer((now - start).coerceAtLeast(0)),
                style = MaterialTheme.typography.displayMedium,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = spacing.s),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
                content = actions,
            )
        }
    }
}

@Preview(name = "TimerCard", widthDp = 720)
@Composable
private fun TimerCardPreview() = LightDarkPreview {
    val start = 1_786_000_000_000L
    TimerStartCard(R.drawable.ic_sleep, "Сон", "Уснул…", onStart = {})
    RunningTimerCard("Маша ест · левая грудь", start, now = start + 754_000) {
        Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Закончить") }
        OutlinedButton(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Сменить грудь") }
    }
}
