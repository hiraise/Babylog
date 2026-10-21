package com.kid.babylog.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.lastDate
import com.kid.babylog.core.model.startDate
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.formatDayRange
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.theme.illnessBand
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToLong

/** «В дни болезни и без» — только если тех и других полных суток с записями не меньше стольких, как у меток (ADR-0031). */
private const val MIN_DAYS_TO_COMPARE = 3

private val SwatchSize = 12.dp
private val SwatchShape = RoundedCornerShape(3.dp)
private val ComparisonBarHeight = 12.dp
private val ComparisonBarShape = RoundedCornerShape(6.dp)

/**
 * «Болела: 7–14 окт. и с 18 окт.» — болезни (ADR-0040), захватившие сутки периода с [first] по [last];
 * null — не болел(а). Идущая — «с 18 окт.».
 */
internal fun illnessNote(events: List<BabyEvent>, first: LocalDate, last: LocalDate, today: LocalDate, sex: Sex?, zone: ZoneId): String? {
    val ranges = events.filterIsInstance<Illness>()
        .filter { it.startDate(zone) <= last && it.lastDate(today, zone) >= first }
        .sortedBy { it.start }
        .map { illness ->
            val start = illness.startDate(zone)
            if (illness.isActive) "с ${formatDayRange(start, start)}" else formatDayRange(start, illness.lastDate(today, zone))
        }
    if (ranges.isEmpty()) return null
    val joined = if (ranges.size == 1) ranges.single() else ranges.dropLast(1).joinToString(", ") + " и " + ranges.last()
    return "${bySex(sex, "Болел", "Болела")}: $joined"
}

/** Сон в сутки в дни болезни и без: средние и по скольким суткам; [illLabel] — «болел» или «болела». */
data class IllnessSleep(val illMillis: Long, val illDays: Int, val healthyMillis: Long, val healthyDays: Int, val illLabel: String)

/**
 * Сон «в дни болезни и без» (ADR-0040) по полным суткам с записями [days]; null — тех или других меньше
 * [MIN_DAYS_TO_COMPARE]. Дни болезни из средних не убираются — это сравнение рядом, а не поправка.
 */
internal fun illnessSleep(days: List<DayStats>, sex: Sex?): IllnessSleep? {
    val (ill, healthy) = days.filter { it.countable }.partition { it.ill }
    if (ill.size < MIN_DAYS_TO_COMPARE || healthy.size < MIN_DAYS_TO_COMPARE) return null
    return IllnessSleep(
        illMillis = ill.map { it.sleepMillis }.average().roundToLong(),
        illDays = ill.size,
        healthyMillis = healthy.map { it.sleepMillis }.average().roundToLong(),
        healthyDays = healthy.size,
        illLabel = bySex(sex, "болел", "болела"),
    )
}

/** Подпись под выбором периода: образец полосы болезни и когда болел(а) ([illnessNote]). */
@Composable
internal fun IllnessLegend(note: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
    ) {
        Box(Modifier.size(SwatchSize).background(colors.illnessBand, SwatchShape))
        Text(note, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

/**
 * «В дни болезни и без» в разделе «Сон»: две полосы — сон в сутки без болезни и в дни болезни, длина —
 * доля от большего. Полоса болезни — на её фоне, как столбики графика.
 */
@Composable
internal fun IllnessSleepCard(model: IllnessSleep) {
    val spacing = BabyLogTheme.spacing
    val chart = BabyLogTheme.chartColors
    val colors = MaterialTheme.colorScheme
    val longest = maxOf(model.illMillis, model.healthyMillis).coerceAtLeast(1)
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            CardHeader(R.drawable.ic_illness, "В дни болезни и без", null)
            listOf(
                Triple(model.healthyMillis, "без болезни", false),
                Triple(model.illMillis, model.illLabel, true),
            ).forEach { (millis, label, ill) ->
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    Text("${formatDuration(millis)} в сутки — $label", style = MaterialTheme.typography.bodyMedium)
                    Box(Modifier.fillMaxWidth().height(ComparisonBarHeight).then(if (ill) Modifier.background(colors.illnessBand, ComparisonBarShape) else Modifier)) {
                        Box(Modifier.fillMaxWidth(millis.toFloat() / longest).height(ComparisonBarHeight).background(chart.sleep, ComparisonBarShape))
                    }
                }
            }
            Text(
                "по ${model.healthyDays} и ${model.illDays} суткам; сегодня в средние не входит",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}
