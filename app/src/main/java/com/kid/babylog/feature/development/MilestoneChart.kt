package com.kid.babylog.feature.development

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kid.babylog.core.ui.theme.BabyLogTheme
import java.util.Locale
import kotlin.math.roundToInt

/** Длина месяца в днях, как в таблице ВОЗ: месяцы там — дни, делённые на 30,4375. */
const val DAYS_PER_MONTH = 30.4375

/** Правый край шкалы — 18 месяцев: позже всех кончается окно «ходит самостоятельно» (534 дня). */
private const val SCALE_MONTHS = 18
private val SCALE_DAYS = (SCALE_MONTHS * DAYS_PER_MONTH).roundToInt()
private val AXIS_MONTHS = listOf(0, 6, 12, SCALE_MONTHS)

/** Место над полосами под подпись «сейчас» и под ними — под подписи месяцев. */
private val LABEL_ROOM = 16.dp
private val BAR_HEIGHT = 8.dp
private val NOW_LINE = 2.dp

/** Возраст в месяцах с одним знаком после запятой, как у ВОЗ: 115 дней — «3,8». */
fun formatMonths(days: Int): String = formatMonthValue(days / DAYS_PER_MONTH)

/** Месяцы из таблицы ВОЗ по-русски: 13.5 — «13,5». */
fun formatMonthValue(months: Double): String = String.format(Locale.forLanguageTag("ru"), "%.1f", months)

/**
 * Окна навыков ВОЗ (ADR-0037): строка на навык — подпись и полоса окна на шкале 0–18 месяцев,
 * вертикальная линия — возраст ребёнка. Окно, в котором ребёнок сейчас, — `chartColors.milestone`
 * и подпись полужирным, остальные — `milestoneOther`. Подложка полосы — `surfaceContainerHighest`.
 * Для TalkBack график — одна фраза: возраст и окно каждого навыка в месяцах.
 */
@Composable
fun MilestoneChart(
    milestones: List<Pair<MilestoneWindow, MilestoneState>>,
    ageDays: Int,
    modifier: Modifier = Modifier,
    rowHeight: Dp = 36.dp,
    labelWidth: Dp = 136.dp,
) {
    val chart = BabyLogTheme.chartColors
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val measurer = rememberTextMeasurer()
    val axisStyle = typography.labelSmall.copy(color = colors.onSurfaceVariant)
    val nowStyle = typography.labelSmall.copy(color = colors.onSurface, fontWeight = FontWeight.SemiBold)
    val description = "Окна навыков ВОЗ, возраст сейчас — ${formatMonths(ageDays)} мес. " +
        milestones.joinToString("; ") { (m, state) ->
            "${m.title} — с ${formatMonthValue(m.months.start)} до ${formatMonthValue(m.months.endInclusive)} мес" +
                if (state == MilestoneState.IN_WINDOW) ", сейчас в окне" else ""
        }
    Row(
        modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
    ) {
        Column(Modifier.width(labelWidth).padding(top = LABEL_ROOM)) {
            milestones.forEach { (m, state) ->
                Box(Modifier.height(rowHeight), contentAlignment = Alignment.CenterStart) {
                    Text(
                        m.title,
                        style = typography.bodySmall,
                        fontWeight = if (state == MilestoneState.IN_WINDOW) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Canvas(Modifier.weight(1f).height(LABEL_ROOM * 2 + rowHeight * milestones.size)) {
            val top = LABEL_ROOM.toPx()
            val row = rowHeight.toPx()
            val bar = BAR_HEIGHT.toPx()
            val bottom = top + row * milestones.size
            fun x(days: Int) = size.width * days.coerceIn(0, SCALE_DAYS) / SCALE_DAYS
            milestones.forEachIndexed { index, (m, state) ->
                val barTop = top + row * index + (row - bar) / 2
                val radius = CornerRadius(bar / 2)
                drawRoundRect(colors.surfaceContainerHighest, Offset(0f, barTop), Size(size.width, bar), radius)
                drawRoundRect(
                    color = if (state == MilestoneState.IN_WINDOW) chart.milestone else chart.milestoneOther,
                    topLeft = Offset(x(m.window.first), barTop),
                    size = Size(x(m.window.last) - x(m.window.first), bar),
                    cornerRadius = radius,
                )
            }
            val nowX = x(ageDays)
            drawLine(colors.onSurface, Offset(nowX, top), Offset(nowX, bottom), strokeWidth = NOW_LINE.toPx())
            val now = measurer.measure("сейчас", nowStyle)
            drawText(now, topLeft = Offset((nowX - now.size.width / 2f).coerceIn(0f, size.width - now.size.width), 0f))
            AXIS_MONTHS.forEach { months ->
                val label = measurer.measure(if (months == SCALE_MONTHS) "$months мес" else "$months", axisStyle)
                val tick = x((months * DAYS_PER_MONTH).roundToInt())
                val left = (tick - label.size.width / 2f).coerceIn(0f, size.width - label.size.width)
                drawText(label, topLeft = Offset(left, bottom + (LABEL_ROOM.toPx() - label.size.height) / 2))
            }
        }
    }
}
