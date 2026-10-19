package com.kid.babylog.feature.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Подпись строки с полосой: «перевозбуждение» и «между кормлениями» влезают целиком в одну строку,
 * при крупном шрифте подпись переносится на вторую.
 */
private val RowLabelWidth = 132.dp
private val DurationWidth = 80.dp
private val CountWidth = 28.dp
private val BarHeight = 12.dp

/** Полоса растёт вправо от общей линии: скруглён только конец, как верх у столбиков графиков. */
private val BarShape = RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp)

/** Самая короткая полоса — чтобы и «1 раз» был виден, а не исчезал. */
private const val MIN_BAR = 0.03f

/**
 * «Перед плачем» (ADR-0030): бодрствование и время после кормления — медиана перед плачем рядом
 * с обычной. Цвет ряда «плач» — перед плачем, нейтральный `outline` — обычное; подписи в строке,
 * поэтому смысл не держится на одном цвете. Сравнивать не с чем — подсказка, каких записей не хватает.
 */
@Composable
internal fun CryingBeforeCard(model: CryingModel) {
    val chart = BabyLogTheme.chartColors
    val colors = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
            CardHeader(R.drawable.ic_schedule, "Перед плачем", "по ${model.episodes} ${plural(model.episodes, "разу", "разам", "разам")}")
            val awake = model.awakeBefore
            val feeding = model.sinceFeeding
            if (awake == null && feeding == null) {
                Text(
                    "Сравнение появится, когда рядом с плачем будут записи сна и кормлений",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            awake?.let {
                Comparison("Бодрствование", listOf(Triple("перед плачем", it, chart.crying), Triple("обычно", model.usualAwake, colors.outline)))
            }
            feeding?.let {
                Comparison(
                    "После кормления",
                    listOf(Triple("перед плачем", it, chart.crying), Triple("между кормлениями", model.feedingInterval, colors.outline)),
                )
            }
            if (awake != null && model.usualAwake != null) {
                Text(longerThanUsualText(model.longerThanUsual, model.awakeEpisodes), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** «В 2 из 5 раз…», «Ни разу…», «Каждый раз…» — из плача, перед которым известно бодрствование. */
internal fun longerThanUsualText(longer: Int, of: Int): String = when (longer) {
    0 -> "Ни разу плач не начался после бодрствования дольше обычного"
    of -> "Каждый раз плач начинался после бодрствования дольше обычного"
    else -> "В $longer из $of раз плач начался после бодрствования дольше обычного"
}

/** Заголовок и строки-полосы одной шкалы: самая длинная — во всю ширину. Строка без значения не рисуется. */
@Composable
private fun Comparison(title: String, rows: List<Triple<String, Long?, Color>>) {
    val max = rows.mapNotNull { it.second }.maxOrNull()?.takeIf { it > 0 } ?: return
    Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.xs)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        rows.forEach { (label, value, color) ->
            value?.let { BarRow(label, it.toFloat() / max, formatDuration(it), color, DurationWidth) }
        }
    }
}

/**
 * «Причины и что помогало» (ADR-0030): метки и способы по числу раз; доля полосы — от всех случаев
 * плача, у одного плача меток бывает несколько. Причины — цветом ряда «плач», способы — `secondary`.
 */
@Composable
internal fun CryingCausesCard(model: CryingModel) {
    val chart = BabyLogTheme.chartColors
    val colors = MaterialTheme.colorScheme
    val spacing = BabyLogTheme.spacing
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
            CardHeader(R.drawable.ic_tag, "Причины и что помогало", "по ${model.episodes} ${plural(model.episodes, "разу", "разам", "разам")}")
            if (model.reasons.isEmpty() && model.helped.isEmpty()) {
                Text(
                    "Отмечайте после плача, почему он был и что помогло — здесь станет видно, что повторяется",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            listOf("Почему, по меткам" to (model.reasons to chart.crying), "Что помогло" to (model.helped to colors.secondary))
                .filter { (_, items) -> items.first.isNotEmpty() }
                .forEach { (title, items) ->
                    val (rows, color) = items
                    Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                        Text(title, style = MaterialTheme.typography.titleSmall)
                        rows.forEach { (label, count) -> BarRow(label, count.toFloat() / model.episodes, count.toString(), color, CountWidth) }
                    }
                }
        }
    }
}

/** Строка: подпись, полоса доли [fraction] от ширины, значение справа. TalkBack читает её целиком. */
@Composable
private fun BarRow(label: String, fraction: Float, value: String, color: Color, valueWidth: Dp) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(RowLabelWidth),
        )
        Box(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(MIN_BAR, 1f)).height(BarHeight).background(color, BarShape))
        }
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width(valueWidth))
    }
}
