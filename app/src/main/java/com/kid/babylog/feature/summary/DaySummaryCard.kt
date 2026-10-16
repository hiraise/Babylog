package com.kid.babylog.feature.summary

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.breastLabel
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.diaperLabel
import com.kid.babylog.core.ui.formatAgo
import com.kid.babylog.core.ui.formatDayHeader
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import java.time.LocalDate
import java.time.ZoneId

/** Сводка дня; [sex] — пол ребёнка, для «пописал / пописала». */
@Composable
fun DaySummaryCard(events: List<BabyEvent>, date: LocalDate, now: Long, sex: Sex?) {
    // Пересчитываем раз в минуту, а не на каждый тик секундного таймера
    val minuteNow = now / 60_000
    val lines = remember(events, date, minuteNow, sex) {
        summarizeDay(events, date, now, ZoneId.systemDefault()).lines(now, sex)
    }
    val spacing = BabyLogTheme.spacing
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text(formatDayHeader(date, now.toLocalDate()), style = MaterialTheme.typography.titleMedium)
            lines.forEach { line ->
                // Иконка и текст — одна фраза для TalkBack
                Row(
                    modifier = Modifier.semantics(mergeDescendants = true) {},
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    AppIcon(
                        line.icon,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(line.label) }
                            append(" ")
                            append(line.value)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/** Строка сводки: иконка, метка («Кормлений:») и значение («5 · последнее: левая, 25 мин назад»). */
internal data class SummaryLine(@DrawableRes val icon: Int, val label: String, val value: String)

/** Строки карточки: кормления, подгузники, сон, лекарства. */
internal fun DaySummary.lines(now: Long, sex: Sex?): List<SummaryLine> {
    val feedingValue = buildString {
        append(feedings)
        lastFeeding?.let {
            if (it.isActive) {
                append(" · сейчас ест: ${breastLabel(it.lastBreast)}")
            } else {
                // Перерыв между кормлениями считают от начала: «кормить каждые 3 часа»
                append(" · последнее: ${breastLabel(it.lastBreast)}, ")
                append(formatAgo((now - it.start).coerceAtLeast(0)))
            }
        }
    }
    val diaperValue = buildString {
        append(diaperCount)
        if (diaperCount > 0) {
            // Разбивка по видам — как у лекарств: «пописал ×2 · покакал ×1»
            val parts = DiaperKind.entries
                .filter { (diapers[it] ?: 0) > 0 }
                .map { "${diaperLabel(it, sex)} ×${diapers[it]}" }
            append(" (${parts.joinToString(" · ")})")
        }
    }
    val sleepValue = buildString {
        append(if (sleepMillis > 0) formatDuration(sleepMillis) else "0 мин")
        if (sleepingNow) append(" · сейчас спит")
    }
    val medsValue = buildString {
        append(medicines.sumOf { it.second })
        if (medicines.isNotEmpty()) {
            // Одинаковые названия суммируем: «Витамин D ×3»
            append(" (")
            append(medicines.joinToString(", ") { (name, count) -> if (count > 1) "$name ×$count" else name })
            append(")")
        }
    }
    return listOf(
        SummaryLine(R.drawable.ic_feeding, "Кормлений:", feedingValue),
        SummaryLine(R.drawable.ic_diaper, "Подгузников:", diaperValue),
        SummaryLine(R.drawable.ic_sleep, "Сон:", sleepValue),
        SummaryLine(R.drawable.ic_medicine, "Лекарств:", medsValue),
    )
}
