package com.kid.babylog.feature.diary

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.formatDayHeader
import com.kid.babylog.core.ui.guide.GuideTarget
import com.kid.babylog.core.ui.guide.guideTarget
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import java.time.LocalDate
import java.time.ZoneId

/** Дни с записями плюс сегодня, по возрастанию: по ним листается история. */
fun historyDays(events: List<BabyEvent>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<LocalDate> =
    (events.map { it.start.toLocalDate(zone) } + today).distinct().sorted()

@Composable
internal fun DayNavRow(
    selectedDate: LocalDate,
    today: LocalDate,
    hasPrev: Boolean,
    hasNext: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: () -> Unit,
) {
    val spacing = BabyLogTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = hasPrev) {
            AppIcon(R.drawable.ic_chevron_left, contentDescription = "Предыдущий день")
        }
        TextButton(
            onClick = onPick,
            modifier = Modifier.weight(1f).guideTarget(GuideTarget.DAY_PICKER),
        ) {
            AppIcon(
                R.drawable.ic_calendar,
                contentDescription = null,
                modifier = Modifier.padding(end = spacing.s),
            )
            // «28 сентября, воскресенье» на экране 360 dp шире места между стрелками
            val style = MaterialTheme.typography.titleMedium
            Text(
                formatDayHeader(selectedDate, today),
                style = style,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * 0.8f, maxFontSize = style.fontSize),
            )
        }
        IconButton(onClick = onNext, enabled = hasNext) {
            AppIcon(R.drawable.ic_chevron_right, contentDescription = "Следующий день")
        }
    }
}

/** Список дней с записями (новые сверху) и числом записей в каждом; тап по дню выбирает его. */
@Composable
internal fun DaySelectorSheet(
    days: List<LocalDate>,
    events: List<BabyEvent>,
    selectedDate: LocalDate,
    today: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val counts = remember(events) { events.groupingBy { it.start.toLocalDate() }.eachCount() }
    val spacing = BabyLogTheme.spacing
    // Список прокручивается сам (LazyColumn), поэтому лист без своей прокрутки
    AppSheet(
        onDismiss = onDismiss,
        title = "Выбрать день",
        icon = R.drawable.ic_calendar,
        scrollable = false,
        edgeToEdge = true,
    ) { closer ->
        LazyColumn(modifier = Modifier.heightIn(max = 420.dp).selectableGroup()) {
            items(days.sortedDescending(), key = { it.toEpochDay() }) { day ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .selectable(selected = day == selectedDate, role = Role.RadioButton) { closer.close { onPick(day) } }
                        .padding(vertical = spacing.m, horizontal = spacing.l),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // У Inter загружены веса до SemiBold: Bold был бы синтетическим
                    Text(
                        formatDayHeader(day, today),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (day == selectedDate) FontWeight.SemiBold else null,
                        color = if (day == selectedDate) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${counts[day] ?: 0}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
