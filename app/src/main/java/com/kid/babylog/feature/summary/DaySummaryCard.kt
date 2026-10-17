package com.kid.babylog.feature.summary

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.DiaperKind
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.bottleLabel
import com.kid.babylog.core.ui.breastLabel
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.diaperIcon
import com.kid.babylog.core.ui.diaperLabel
import com.kid.babylog.core.ui.formatAgo
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.theme.BabyLogTheme
import java.time.LocalDate
import java.time.ZoneId

/**
 * Сводка дня; [sex] — пол ребёнка, для «пописал / пописала».
 *
 * Заголовка с днём нет: какой это день, и так видно — по вкладке «Сегодня» или по выбору дня
 * в «Истории».
 */
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
                            if (line.parts.isNotEmpty()) {
                                append(" (")
                                line.parts.forEachIndexed { i, part ->
                                    if (i > 0) append(" · ")
                                    // TalkBack читает вместо иконки её описание: «пописал»
                                    appendInlineContent(part.inlineId, part.description)
                                    append("×${part.count}")
                                }
                                append(")")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        inlineContent = line.parts.associate { part ->
                            // Иконка в строке — размером с иконку строки (20 dp при обычном шрифте),
                            // не выше строки bodyMedium
                            part.inlineId to InlineTextContent(Placeholder(1.4.em, 1.4.em, PlaceholderVerticalAlign.TextCenter)) {
                                AppIcon(part.icon, contentDescription = null, modifier = Modifier.fillMaxSize(), tint = MaterialTheme.colorScheme.primary)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * Строка сводки: иконка, метка («Кормлений:») и значение («5 · последнее: левая, 25 мин назад»).
 * [parts] — разбивка значения иконками: у подгузников «3 (💧×2 · 💨×1)» с иконками вида.
 */
internal data class SummaryLine(
    @DrawableRes val icon: Int,
    val label: String,
    val value: String,
    val parts: List<SummaryPart> = emptyList(),
)

/** Часть разбивки: иконка, сколько раз и описание для TalkBack («пописал»). */
internal data class SummaryPart(@DrawableRes val icon: Int, val count: Int, val description: String) {
    val inlineId: String get() = "part_$icon"
}

/** Строки карточки: кормления, подгузники, сон, лекарства. */
internal fun DaySummary.lines(now: Long, sex: Sex?): List<SummaryLine> {
    val feedingValue = buildString {
        append(feedings)
        // Неразрывный пробел: число и «мл» не расходятся по строкам
        if (bottles > 0) append(" (бутылочкой $bottles, $bottleMl\u00A0мл)")
        val breast = lastFeeding
        val bottle = lastBottle
        // Перерыв между кормлениями считают от начала: «кормить каждые 3 часа»; у бутылочки начало и есть время
        when {
            breast?.isActive == true -> append(" · сейчас ест: ${breastLabel(breast.lastBreast)}")
            bottle != null && (breast == null || bottle.start >= breast.start) -> {
                append(" · последнее: ${bottleLabel(bottle.volumeMl, bottle.milk)}, ")
                append(formatAgo((now - bottle.start).coerceAtLeast(0)))
            }
            breast != null -> {
                append(" · последнее: ${breastLabel(breast.lastBreast)}, ")
                append(formatAgo((now - breast.start).coerceAtLeast(0)))
            }
        }
    }
    // Разбивка по видам — иконками вида подгузника, как в истории: капля, волны, оба знака
    val diaperParts = DiaperKind.entries
        .filter { (diapers[it] ?: 0) > 0 }
        .map { SummaryPart(diaperIcon(it), diapers.getValue(it), diaperLabel(it, sex)) }
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
        SummaryLine(R.drawable.ic_diaper, "Подгузников:", diaperCount.toString(), diaperParts),
        SummaryLine(R.drawable.ic_sleep, "Сон:", sleepValue),
        SummaryLine(R.drawable.ic_medicine, "Лекарств:", medsValue),
    )
}
