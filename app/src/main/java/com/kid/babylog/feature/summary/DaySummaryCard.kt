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
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.diaperIcon
import com.kid.babylog.core.ui.diaperLabel
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.temperatureValue
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
        summarizeDay(events, date, now, ZoneId.systemDefault()).lines(sex)
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
                                    append("×${part.count}${part.suffix}")
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
 * Строка сводки: иконка, метка («Кормлений:») и значение («5»).
 * [parts] — разбивка значения иконками: у подгузников «3 (💧×2 · 💨×1)» с иконками вида.
 */
internal data class SummaryLine(
    @DrawableRes val icon: Int,
    val label: String,
    val value: String,
    val parts: List<SummaryPart> = emptyList(),
)

/**
 * Часть разбивки: иконка, сколько раз, описание для TalkBack («пописал») и [suffix] после числа —
 * «, 210 мл» у бутылочек.
 */
internal data class SummaryPart(@DrawableRes val icon: Int, val count: Int, val description: String, val suffix: String = "") {
    val inlineId: String get() = "part_$icon"
}

/**
 * Строки карточки: кормления, подгузники, сон, лекарства и — в дни, когда они были, — плач (ADR-0030)
 * и температура (ADR-0041): одна — значением, несколько — «до 39,2 (3 раза)».
 */
internal fun DaySummary.lines(sex: Sex?): List<SummaryLine> {
    // Разбивка кормлений — только когда были бутылочки: «5 (грудь×3 · бутылочка×2, 210 мл)»;
    // одной грудью разбивать нечего. Неразрывный пробел: число и «мл» не расходятся по строкам
    val feedingParts = if (bottles == 0) {
        emptyList()
    } else {
        listOfNotNull(
            SummaryPart(R.drawable.ic_feeding, breastFeedings, "грудью").takeIf { breastFeedings > 0 },
            SummaryPart(R.drawable.ic_bottle, bottles, "бутылочкой", suffix = ", $bottleMl\u00A0мл"),
        )
    }
    // Разбивка по видам — иконками вида подгузника, как в истории: капля, волны, оба знака
    val diaperParts = DiaperKind.entries
        .filter { (diapers[it] ?: 0) > 0 }
        .map { SummaryPart(diaperIcon(it), diapers.getValue(it), diaperLabel(it, sex)) }
    val sleepValue = if (sleepMillis > 0) formatDuration(sleepMillis) else "0 мин"
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
        SummaryLine(R.drawable.ic_feeding, "Кормлений:", feedings.toString(), feedingParts),
        SummaryLine(R.drawable.ic_diaper, "Подгузников:", diaperCount.toString(), diaperParts),
        SummaryLine(R.drawable.ic_sleep, "Сон:", sleepValue),
        SummaryLine(R.drawable.ic_medicine, "Лекарств:", medsValue),
    ) + listOfNotNull(
        // Плач — только в дни, когда он был: тем, кто его не отмечает, «Плач: 0» каждый день ни к чему.
        // Начался вчера и перешёл за полночь — сегодня только его минуты, без «0 раз»
        SummaryLine(
            R.drawable.ic_crying,
            "Плач:",
            if (cryings > 0) "$cryings ${timesWord(cryings)}, ${formatDuration(cryingMillis)}" else formatDuration(cryingMillis),
        ).takeIf { cryings > 0 || cryingMillis > 0 },
        maxTemperatureTenths?.let { max ->
            SummaryLine(
                R.drawable.ic_thermometer,
                "Температура:",
                if (temperatures == 1) temperatureValue(max) else "до ${temperatureValue(max)} ($temperatures ${timesWord(temperatures)})",
            )
        },
    )
}

/** «1 раз», «2 раза», «5 раз». */
private fun timesWord(n: Int): String = when {
    n % 100 in 11..14 -> "раз"
    n % 10 in 2..4 -> "раза"
    else -> "раз"
}
