package com.kid.babylog.feature.children

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Weight
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.Avatar
import com.kid.babylog.core.ui.components.SheetMenuItem
import com.kid.babylog.core.ui.diaryTitle
import com.kid.babylog.core.ui.formatAge
import com.kid.babylog.core.ui.formatWeight
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import java.time.LocalDate

/**
 * Выбор ребёнка в шапке (ADR-0019): аватар, «Дневник Маши» и под ним возраст и последний вес.
 * Касание открывает [ChildrenSheet]; стрелка подсказывает, что это выбор.
 */
@Composable
fun ChildSelector(child: Child, lastWeight: Weight?, now: Long, onClick: () -> Unit) {
    val spacing = BabyLogTheme.spacing
    val info = childInfo(child, lastWeight, now.toLocalDate())
    Row(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = "выбрать ребёнка", role = Role.Button, onClick = onClick)
            .padding(end = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Avatar()
        Column(Modifier.weight(1f, fill = false)) {
            // Шрифт названия приложения — как логотип; длинное имя уменьшает его, а не обрезается сразу
            val titleStyle = MaterialTheme.typography.headlineSmall
            Text(
                diaryTitle(child.name, child.sex),
                style = titleStyle,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = titleStyle.fontSize * 0.75f, maxFontSize = titleStyle.fontSize),
            )
            if (info != null) {
                Text(
                    info,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        AppIcon(R.drawable.ic_expand, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Лист «Дети»: все дети, у выбранного — отметка и карандаш (правка, удаление), внизу —
 * «Добавить ребёнка». Касание другого ребёнка выбирает его и закрывает лист.
 */
@Composable
fun ChildrenSheet(
    children: List<Child>,
    selectedId: Long,
    now: Long,
    onSelect: (Long) -> Unit,
    onEdit: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    val spacing = BabyLogTheme.spacing
    val today = now.toLocalDate()
    AppSheet(onDismiss = onDismiss, title = "Дети", icon = R.drawable.ic_child, edgeToEdge = true) { closer ->
        Column(Modifier.selectableGroup()) {
            children.forEach { child ->
                val selected = child.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .selectable(selected = selected, role = Role.RadioButton) { closer.close { onSelect(child.id) } }
                        .padding(start = spacing.l, end = spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.m),
                ) {
                    Avatar(size = 36.dp)
                    Column(Modifier.weight(1f)) {
                        Text(child.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        child.birthDate?.let {
                            Text(
                                formatAge(it.toLocalDate(), today, child.sex),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (selected) {
                        AppIcon(R.drawable.ic_check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { closer.close(onEdit) }, modifier = Modifier.clip(CircleShape)) {
                            AppIcon(R.drawable.ic_edit, contentDescription = "Изменить данные ребёнка")
                        }
                    }
                }
            }
        }
        SheetMenuItem(R.drawable.ic_add_child, "Добавить ребёнка", onClick = { closer.close(onAdd) })
    }
}

/** «4 мес 12 дн. · 6,4 кг»: возраст и последний вес; ничего не известно — null. */
internal fun childInfo(child: Child, lastWeight: Weight?, today: LocalDate): String? =
    listOfNotNull(
        child.birthDate?.let { formatAge(it.toLocalDate(), today, child.sex) },
        lastWeight?.let { formatWeight(it.grams) },
    ).joinToString(" · ").ifEmpty { null }
