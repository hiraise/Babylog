package com.kid.babylog.feature.analytics

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppSheet

/** Справка «?» на карточке «Сон» (ADR-0036): как сон делится на ночной и дневной. */
@Composable
internal fun SleepHelpSheet(onDismiss: () -> Unit) {
    AppSheet(onDismiss = onDismiss, title = "Сон ночью и днём", icon = R.drawable.ic_help) {
        SleepHelp()
    }
}

/** Текст справки без листа — его снимает скриншот-тест. */
@Composable
internal fun SleepHelp() {
    val style = MaterialTheme.typography.bodyMedium
    Text(
        "Ночью — сон с $NIGHT_START_HOUR:00 до $NIGHT_END_HOUR:00, днём — с $NIGHT_END_HOUR:00 до $NIGHT_START_HOUR:00. " +
            "Так принято в исследованиях детского сна и в большинстве дневников малыша.",
        style = style,
    )
    Text(
        "Сон, который переходит через $NIGHT_END_HOUR:00 или $NIGHT_START_HOUR:00, делится: " +
            "сон с 18:30 до 20:00 — это 30 мин днём и 1 ч ночью.",
        style = style,
    )
    Text(
        "Столбик — сон за сутки с полуночи до полуночи, как в сводке дня: ночная часть в нём — утро " +
            "до $NIGHT_END_HOUR:00 и вечер после $NIGHT_START_HOUR:00. Средние — по прошедшим суткам, в которые вели дневник.",
        style = style,
    )
    Text("В «Ритме дня» дневной сон светлее ночного.", style = style)
}
