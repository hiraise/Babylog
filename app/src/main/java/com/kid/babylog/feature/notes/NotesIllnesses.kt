package com.kid.babylog.feature.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.ui.RU
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.temperatureValue
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.theme.illnessContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val IllnessIconSize = 24.dp
private val MONTH = DateTimeFormatter.ofPattern("LLLL", RU)
private val MONTH_YEAR = DateTimeFormatter.ofPattern("LLLL yyyy", RU)

/**
 * «Болезни» в «Заметках» (ADR-0040): идущая — под «Сейчас», на фоне болезни; прошлые — по месяцам
 * начала, свежие сверху. Касание карточки открывает экран болезни ([onOpen]).
 */
internal fun LazyListScope.illnessItems(entries: List<IllnessEntry>, today: LocalDate, onOpen: (Illness) -> Unit) {
    val zone = ZoneId.systemDefault()
    val (active, past) = entries.partition { it.illness.isActive }
    val thisYear = today.year
    val groups = buildList {
        if (active.isNotEmpty()) add("Сейчас" to active)
        past.groupBy { entry ->
            val start = Instant.ofEpochMilli(entry.illness.start).atZone(zone).toLocalDate()
            start.format(if (start.year == thisYear) MONTH else MONTH_YEAR).replaceFirstChar(Char::titlecase)
        }.forEach { (month, list) -> add(month to list) }
    }
    groups.forEach { (header, list) ->
        item(key = "illness-header:$header") {
            Text(
                header,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = BabyLogTheme.spacing.xs).semantics { heading() },
            )
        }
        items(list, key = { "illness:${it.illness.id}" }) { entry -> IllnessEntryCard(entry, onOpen = { onOpen(entry.illness) }) }
    }
}

/**
 * Карточка болезни: идущая — «Болеет · 3-й день», под ней названия и с какого дня; прошлая — названия
 * («Болезнь» без них), дни, самая высокая температура и чем лечили.
 */
@Composable
private fun IllnessEntryCard(entry: IllnessEntry, onOpen: () -> Unit) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    val active = entry.illness.isActive
    val muted = if (active) colors.tertiary else colors.onSurfaceVariant
    val max = entry.maxTenths?.let { "макс. ${temperatureValue(it)}" }
    val title = if (active) "Болеет · ${entry.days}-й день" else entry.title ?: "Болезнь"
    val lines = if (active) {
        listOf(listOfNotNull(entry.title ?: "без названия", entry.range, max).joinToString(" · "))
    } else {
        listOfNotNull(
            listOfNotNull(entry.range, "${entry.days} ${plural(entry.days, "день", "дня", "дней")}", max).joinToString(" · "),
            entry.medicines.takeIf { it.isNotEmpty() }?.joinToString(", "),
        )
    }
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        onClickLabel = "открыть болезнь",
        containerColor = if (active) colors.illnessContainer else colors.surfaceContainerLow,
        contentColor = if (active) colors.onTertiaryContainer else colors.onSurface,
        contentPadding = PaddingValues(horizontal = spacing.l, vertical = spacing.m),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            AppIcon(R.drawable.ic_illness, contentDescription = null, modifier = Modifier.size(IllnessIconSize), tint = if (active) colors.tertiary else colors.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = muted) }
            }
            AppIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = muted)
        }
    }
}
