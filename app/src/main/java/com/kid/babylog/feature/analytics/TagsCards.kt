package com.kid.babylog.feature.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.theme.BabyLogTheme

/** Подпись метки в строке «Как часто»: «перевозбуждение» влезает целиком. */
private val TagNameWidth = 124.dp
private val CountWidth = 28.dp
private val StripHeight = 12.dp
private val CellGap = 1.5.dp
private val CellRadius = 2.dp
private val LineIconSize = 18.dp

/** Строка метки — под палец: касание открывает метку на «Заметках». */
private val TagRowMinHeight = 48.dp

/** «плач 50 раз · заметок 14» — сколько записей за период. */
internal fun TagsModel.counts(): String = listOf(
    "плач $cryings ${plural(cryings, "раз", "раза", "раз")}",
    "$notes ${plural(notes, "заметка", "заметки", "заметок")}",
).joinToString(" · ")

/**
 * «Как часто» (ADR-0031): по строке на метку — сколько раз за период и в какие сутки (у «всего
 * времени» — недели) она была: закрашенная клетка — была. Касание строки открывает метку на «Заметках».
 */
@Composable
internal fun TagsFrequencyCard(model: TagsModel, byWeeks: Boolean, onOpenTag: (String) -> Unit) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            CardHeader(R.drawable.ic_tag, "Как часто", if (byWeeks) "по неделям" else "по дням")
            Text(model.counts(), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            if (model.frequencies.isEmpty()) {
                Text("За этот период меток нет", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
            model.frequencies.forEach { tag ->
                val marked = tag.marks.count { it }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = TagRowMinHeight)
                        .clickable(onClickLabel = "показать записи с меткой") { onOpenTag(tag.tag) }
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${tag.tag}: ${tag.count} ${plural(tag.count, "раз", "раза", "раз")}, " +
                                "$marked из ${tag.marks.size} ${if (byWeeks) "недель" else "суток"}"
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.s),
                ) {
                    Text(
                        tag.tag,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(TagNameWidth),
                    )
                    Text(tag.count.toString(), style = MaterialTheme.typography.titleSmall, textAlign = TextAlign.End, modifier = Modifier.width(CountWidth))
                    MarksStrip(tag.marks, Modifier.weight(1f))
                }
            }
            if (model.untaggedNotes > 0) {
                val n = model.untaggedNotes
                Text(
                    "Ещё $n ${plural(n, "заметка", "заметки", "заметок")} без меток — в счёт не входят, они есть в ленте «Заметок»",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/** Клетки по столбикам периода: была метка — `secondary`, не было — `surfaceContainerHigh`. */
@Composable
private fun MarksStrip(marks: List<Boolean>, modifier: Modifier) {
    val on = MaterialTheme.colorScheme.secondary
    val off = MaterialTheme.colorScheme.surfaceContainerHigh
    Canvas(modifier.height(StripHeight)) {
        if (marks.isEmpty()) return@Canvas
        val gap = CellGap.toPx()
        val cell = (size.width - gap * (marks.size - 1)) / marks.size
        marks.forEachIndexed { index, marked ->
            drawRoundRect(if (marked) on else off, Offset(index * (cell + gap), 0f), Size(cell, size.height), CornerRadius(CellRadius.toPx()))
        }
    }
}

/**
 * «Метки и режим» (ADR-0031): у меток заметок — сон и плач в сутки с меткой против суток без неё.
 * Меньше [MIN_ROUTINE_DAYS] суток с меткой или без — «мало данных», а не число из одного дня.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TagsRoutineCard(model: TagsModel) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.m)) {
            CardHeader(R.drawable.ic_schedule, "Метки и режим", "сутки с меткой и без неё")
            model.routine.forEach { tag ->
                Column(Modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                    Text(
                        "${tag.tag} · ${tag.days} ${plural(tag.days, "день", "дня", "дней")}",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    if (tag.sleep == null) {
                        Text(
                            "мало данных: нужно хотя бы $MIN_ROUTINE_DAYS дня с меткой и $MIN_ROUTINE_DAYS без",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    } else {
                        // Строки сна и плача переносятся целиком: «плач — без / разницы» не читается
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.l), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                            RoutineLine(R.drawable.ic_sleep, tag.sleep)
                            tag.crying?.let { RoutineLine(R.drawable.ic_crying, it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoutineLine(icon: Int, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.xs)) {
        AppIcon(icon, contentDescription = null, modifier = Modifier.size(LineIconSize), tint = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}

/** Что именно считаем — под картами «Меток»: текст заметок не разбирается, это совпадения, а не причины. */
@Composable
internal fun TagsFootnote() {
    Text(
        "Считаем по меткам и времени записей, текст заметок не разбираем — всё остаётся на телефоне. " +
            "Это совпадения в записях, а не причины.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}
