package com.kid.babylog.feature.notes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.RU
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.theme.BabyLogTheme
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val CellHeight = 34.dp
private val CellShape = RoundedCornerShape(9.dp)
private val DotSize = 4.dp
private val SwatchSize = 12.dp
private val SwatchShape = RoundedCornerShape(4.dp)
private val StripHeight = 24.dp
private val TrackHeight = 16.dp
private val StartDotRadius = 5.dp
private val LineIconSize = 20.dp
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMM", RU)

/** Минут в сутках — для доли на шкале «Время суток». */
private const val DAY_MINUTES = 24 * 60f

/** Подписи шкалы «Время суток». */
private val HOURS = listOf(0, 6, 12, 18, 24)

/**
 * Сводка метки (ADR-0031) над записями с ней: календарь, время суток, что было до, что помогало
 * и какие метки рядом. Карточки без данных не показываются: у метки только из заметок нет плача.
 * [records] — сколько записей с меткой в ленте под сводкой; [onTag] — открыть соседнюю метку из «Часто вместе».
 */
internal fun LazyListScope.tagSummaryItems(summary: TagSummary, sex: Sex?, records: Int, onTag: (String) -> Unit) {
    item(key = "tag:days") { TagDaysCard(summary) }
    if (summary.cryings > 0) {
        item(key = "tag:time") { TagTimeCard(summary) }
    }
    if (summary.awakeKnown > 0 || summary.sinceFeeding != null) {
        item(key = "tag:before") { TagBeforeCard(summary, sex) }
    }
    if (summary.helped.isNotEmpty() || summary.together.isNotEmpty()) {
        item(key = "tag:helped") { TagHelpedCard(summary, onTag) }
    }
    item(key = "tag:records") {
        Text(
            "Записи · $records",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = BabyLogTheme.spacing.xs).semantics { heading() },
        )
    }
}

/** Заголовок карточки сводки и справа — пояснение мелко. */
@Composable
private fun SummaryHeader(title: String, trailing: String?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * Метка и сколько раз она была, календарь последних [TAG_WEEKS] недель: сутки с плачем — цветом
 * ряда «плач» с числом дня цвета `surface`, заметка — точкой. Сегодня — жирным, будущие сутки — пустые.
 */
@Composable
private fun TagDaysCard(summary: TagSummary) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    val crying = BabyLogTheme.chartColors.crying
    val counts = listOfNotNull(
        summary.cryings.takeIf { it > 0 }?.let { "плач $it ${plural(it, "раз", "раза", "раз")}" },
        summary.notes.takeIf { it > 0 }?.let { "$it ${plural(it, "заметка", "заметки", "заметок")}" },
    ).joinToString(" · ")
    val description = listOfNotNull(
        summary.days.filter { it.cryings > 0 }.takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = "Плач с меткой: ") { it.date.format(DAY_MONTH) },
        summary.days.filter { it.note }.takeIf { it.isNotEmpty() }
            ?.joinToString(", ", prefix = "заметки: ") { it.date.format(DAY_MONTH) },
    ).joinToString("; ").ifEmpty { "За $TAG_WEEKS недели записей с меткой нет" }
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            SummaryHeader(summary.tag, counts)
            Text("По дням · последние $TAG_WEEKS недели", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            Column(
                modifier = Modifier.clearAndSetSemantics { contentDescription = description },
                verticalArrangement = Arrangement.spacedBy(spacing.xs),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    DayOfWeek.entries.forEach { day ->
                        Text(
                            day.getDisplayName(TextStyle.SHORT, RU),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                summary.days.chunked(7).forEach { week ->
                    Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        week.forEach { day ->
                            val cried = day.cryings > 0
                            val ink = if (cried) colors.surface else colors.onSurfaceVariant
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(CellHeight)
                                    .background(if (cried) crying else colors.surfaceContainerHigh, CellShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (!day.future) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            day.date.dayOfMonth.toString(),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = ink,
                                            fontWeight = if (day.today) FontWeight.Bold else null,
                                        )
                                        Box(
                                            Modifier.size(DotSize).background(
                                                when {
                                                    !day.note -> Color.Transparent
                                                    cried -> colors.surface
                                                    else -> colors.onSurface
                                                },
                                                CircleShape,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.l), verticalAlignment = Alignment.CenterVertically) {
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(SwatchSize).background(crying, SwatchShape))
                    Text("плач", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(DotSize).background(colors.onSurface, CircleShape))
                    Text("заметка", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * Когда начинался плач с меткой: точки на шкале суток, «чаще» — подложкой и словами справа. Точки
 * полупрозрачные: при десятках случаев они ложатся друг на друга, и где плакал чаще, цвет гуще.
 */
@Composable
private fun TagTimeCard(summary: TagSummary) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    val crying = BabyLogTheme.chartColors.crying
    val track = colors.surfaceContainerHigh
    val peak = summary.peak
    val trailing = peak?.let { "${it.count} из ${summary.cryings} — с ${it.from} до ${it.to} ч" } ?: "в разное время"
    val description = summary.cryingStarts.sorted().joinToString(", ", prefix = "Начало плача: ") { "%02d:%02d".format(it / 60, it % 60) }
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            SummaryHeader("Время суток", trailing)
            Canvas(Modifier.fillMaxWidth().height(StripHeight).clearAndSetSemantics { contentDescription = description }) {
                val top = (size.height - TrackHeight.toPx()) / 2
                val radius = CornerRadius(SwatchShapeRadius.toPx())
                drawRoundRect(track, Offset(0f, top), Size(size.width, TrackHeight.toPx()), radius)
                peak?.let {
                    // Окно может перейти через полночь: 22–1 — два куска
                    val from = it.from / 24f
                    val to = if (it.to > it.from) it.to / 24f else 1f
                    drawRect(crying.copy(alpha = PEAK_ALPHA), Offset(from * size.width, top), Size((to - from) * size.width, TrackHeight.toPx()))
                    if (it.to <= it.from && it.to > 0) {
                        drawRect(crying.copy(alpha = PEAK_ALPHA), Offset(0f, top), Size(it.to / 24f * size.width, TrackHeight.toPx()))
                    }
                }
                summary.cryingStarts.forEach { minute ->
                    drawCircle(crying.copy(alpha = START_DOT_ALPHA), StartDotRadius.toPx(), Offset(minute / DAY_MINUTES * size.width, size.height / 2))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                HOURS.forEach { Text("$it", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
        }
    }
}

/** Скругление шкалы — как у образца в легенде. */
private val SwatchShapeRadius = 4.dp

/** Подложка «чаще» — цвет ряда «плач», едва заметно: точки на ней должны читаться. */
private const val PEAK_ALPHA = 0.22f

/** Одна точка — бледнее, две-три поверх — уже полный цвет. */
private const val START_DOT_ALPHA = 0.55f

/** Что было перед плачем с меткой: бодрствование против обычного и сколько прошло после кормления. */
@Composable
private fun TagBeforeCard(summary: TagSummary, sex: Sex?) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    val awake = bySex(sex, "не спал", "не спала")
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            SummaryHeader("Что было до", "плач с меткой")
            if (summary.awakeKnown > 0) {
                BeforeLine(
                    R.drawable.ic_sleep,
                    when (summary.longerAwake) {
                        0 -> "Ни разу перед плачем бодрствование не было дольше обычного"
                        summary.awakeKnown -> "Каждый раз перед плачем $awake дольше обычного"
                        else -> "В ${summary.longerAwake} из ${summary.awakeKnown} раз $awake дольше обычного"
                    },
                )
            }
            summary.sinceFeeding?.let { BeforeLine(R.drawable.ic_feeding, "После кормления — обычно через ${formatDuration(it)}") }
            Text("Это совпадения в записях, а не причины", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun BeforeLine(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.size(LineIconSize), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * Что помогало при плаче с меткой и какие метки стояли рядом с ней — подписями с числом раз. Метка
 * из «Часто вместе» нажимается — открывает свою сводку; «что помогло» — не метки, у них сводки нет.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagHelpedCard(summary: TagSummary, onTag: (String) -> Unit) {
    val spacing = BabyLogTheme.spacing
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            if (summary.helped.isNotEmpty()) {
                SummaryHeader("Что помогало", null)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                    summary.helped.forEach { (name, count) -> TagLabel(name, count) }
                }
            }
            if (summary.together.isNotEmpty()) {
                SummaryHeader("Часто вместе", null)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    summary.together.forEach { (name, count) -> TagLabel(name, count, onClick = { onTag(name) }) }
                }
            }
        }
    }
}
