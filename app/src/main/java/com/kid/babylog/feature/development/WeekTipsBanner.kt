package com.kid.babylog.feature.development

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kid.babylog.R
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.theme.BabyLogTheme

/**
 * Полоса советов на «Сегодня» (ADR-0035): под сводкой дня, чтобы кнопки записей оставались наверху.
 * Касание открывает раздел «Развитие». «Новые» — пока советы этой недели не открывали ([hasNewTips]).
 */
@Composable
fun WeekTipsBanner(week: DevelopmentWeek, isNew: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val spacing = BabyLogTheme.spacing
    AppCard(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
        onClickLabel = "открыть «Развитие»",
        containerColor = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
            AppIcon(R.drawable.ic_lightbulb, contentDescription = null)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                    Text("Советы на ${weekAccusative(week.week)}", style = MaterialTheme.typography.titleSmall)
                    if (isNew) {
                        Text(
                            "новые",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onPrimary,
                            modifier = Modifier
                                .background(colors.primary, MaterialTheme.shapes.extraSmall)
                                .padding(horizontal = spacing.s),
                        )
                    }
                }
                Text(bannerSummary(week), style = MaterialTheme.typography.bodySmall)
            }
            AppIcon(R.drawable.ic_chevron_right, contentDescription = null)
        }
    }
}

/** «Сон, кормление, безопасность · 7 советов»: темы недели по порядку и сколько всего советов. */
internal fun bannerSummary(week: DevelopmentWeek): String {
    val topics = week.allTips.map { it.category }.distinct().sortedBy { it.ordinal }
        .mapIndexed { index, category -> if (index == 0) category.title else category.title.lowercase() }
    val count = week.allTips.size
    return "${topics.joinToString(", ")} · $count ${plural(count, "совет", "совета", "советов")}"
}
