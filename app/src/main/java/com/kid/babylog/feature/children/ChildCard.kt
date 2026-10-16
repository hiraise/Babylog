package com.kid.babylog.feature.children

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kid.babylog.R
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.Avatar
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.formatAge
import com.kid.babylog.core.ui.formatWeight
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import java.time.temporal.ChronoUnit

/** Выбор ребёнка; показывается, когда детей больше одного. */
@Composable
fun ChildSwitcher(children: List<Child>, selectedId: Long?, onSelect: (Long) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
    ) {
        children.forEach { child ->
            ChoiceChip(
                selected = child.id == selectedId,
                onClick = { onSelect(child.id) },
                label = child.name,
            )
        }
    }
}

/** Карточка выбранного ребёнка: имя, возраст, последний вес. Нажатие — правка. */
@Composable
fun ChildCard(child: Child, lastWeight: Weight?, now: Long, onEdit: () -> Unit) {
    val today = now.toLocalDate()
    val spacing = BabyLogTheme.spacing
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onEdit,
        onClickLabel = "изменить данные ребёнка",
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(spacing.m),
        ) {
            Avatar(child.name)
            Column(modifier = Modifier.weight(1f)) {
                Text(child.name, style = MaterialTheme.typography.titleMedium)
                val ageLine = child.birthDate
                    ?.let { "Возраст: ${formatAge(it.toLocalDate(), today, child.sex)}" }
                    ?: "Указать дату рождения…"
                Text(ageLine, style = MaterialTheme.typography.bodyMedium)
                val weightLine = if (lastWeight != null) {
                    val days = ChronoUnit.DAYS.between(lastWeight.start.toLocalDate(), today)
                    val whenText = when (days) {
                        0L -> "сегодня"
                        1L -> "вчера"
                        else -> "$days дн. назад"
                    }
                    "Вес: ${formatWeight(lastWeight.grams)} · $whenText"
                } else {
                    "Вес пока не отмечен"
                }
                Text(
                    weightLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Действие уже названо в onClickLabel карточки — иконка только подсказывает его глазами
            AppIcon(
                R.drawable.ic_edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
