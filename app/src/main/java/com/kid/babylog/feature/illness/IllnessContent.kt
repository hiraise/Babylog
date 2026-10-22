package com.kid.babylog.feature.illness

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.sinceDate
import com.kid.babylog.core.model.startDate
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatDayHeader
import com.kid.babylog.core.ui.formatDayRange
import com.kid.babylog.core.ui.formatWeekdayDay
import com.kid.babylog.core.ui.guide.GuideTarget
import com.kid.babylog.core.ui.guide.guideTarget
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.theme.illnessContainer
import java.time.ZoneId

/** Действия экрана болезни: правка дат и названий, «Выздоровел(а)», «Для врача» и новые записи болезни. */
class IllnessActions(
    val onBack: () -> Unit,
    val onEdit: () -> Unit,
    val onAddName: () -> Unit,
    val onEditName: (IllnessName) -> Unit,
    val onRecover: () -> Unit,
    val onResume: (() -> Unit)?,
    val onDoctor: () -> Unit,
    val onAddTemperature: () -> Unit,
    val onAddMedicine: () -> Unit,
    val onEditRecord: (BabyEvent) -> Unit,
)

/**
 * Экран болезни (ADR-0040) — внутри раздела, «Назад» возвращает к нему. Сверху то, что нужно сразу:
 * сколько болеет, названия с днями, «Для врача» и «Выздоровел(а)» — их не ищут в конце ленты. Ниже —
 * температура с приёмами лекарств, лекарства, «по дневнику» против обычного и сутки болезни, свежие сверху.
 * Касание записи — её правка ([IllnessActions.onEditRecord]).
 */
@Composable
fun IllnessContent(model: IllnessScreenModel, sex: Sex?, now: Long, actions: IllnessActions, modifier: Modifier = Modifier) {
    val spacing = BabyLogTheme.spacing
    LazyColumn(
        modifier = modifier.fillMaxSize().guideTarget(GuideTarget.ILLNESS_SCREEN),
        contentPadding = PaddingValues(start = spacing.l, top = spacing.m, end = spacing.l, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        item(key = "header") { Header(actions) }
        item(key = "summary") { SummaryCard(model, sex, actions) }
        item(key = "temperature") { TemperatureCard(model, now, onAdd = actions.onAddTemperature.takeIf { model.illness.isActive }) }
        item(key = "medicines") { MedicinesCard(model.medicines, now, onAdd = actions.onAddMedicine.takeIf { model.illness.isActive }) }
        model.diary?.let { diary -> item(key = "diary") { DiaryCard(diary) } }
        item(key = "days-title") {
            Text("По дням", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = spacing.s).semantics { heading() })
        }
        items(model.byDay, key = { it.date.toEpochDay() }) { day ->
            Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
                Text(
                    "${formatDayHeader(day.date, model.today)} · ${day.number}-й день",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = spacing.xs).semantics { heading() },
                )
                DayCard(day, sex, actions.onEditRecord)
            }
        }
    }
}

/** «‹ Болезнь» и карандаш — правка начала, конца и комментария. */
@Composable
private fun Header(actions: IllnessActions) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = actions.onBack) { AppIcon(R.drawable.ic_chevron_left, contentDescription = "Назад") }
        Text("Болезнь", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
        IconButton(onClick = actions.onEdit) { AppIcon(R.drawable.ic_edit, contentDescription = "Изменить даты и комментарий") }
    }
}

/**
 * Шапка на фоне болезни: «Болеет · 4-й день» (прошлая — «Болела 8 дней»), с какого дня, названия
 * с днём «с» (касание — правка), «+ название», «Для врача» и «Выздоровел(а)»; у прошлой — «Ещё болеет»,
 * если закрыли по ошибке и другой болезни нет.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(model: IllnessScreenModel, sex: Sex?, actions: IllnessActions) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    val illness = model.illness
    val zone = ZoneId.systemDefault()
    val first = illness.startDate(zone)
    AppCard(Modifier.fillMaxWidth(), containerColor = colors.illnessContainer, contentColor = colors.onTertiaryContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Text(
                if (illness.isActive) "Болеет · ${model.days}-й день" else "${bySex(sex, "Болел", "Болела")} ${model.days} ${plural(model.days, "день", "дня", "дней")}",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                if (illness.isActive) "с ${formatWeekdayDay(first)}, ${formatClock(illness.start)}" else formatDayRange(first, first.plusDays(model.days - 1L)),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.tertiary,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalArrangement = Arrangement.Center) {
                illness.names.forEach { name ->
                    NameChip(name.name, "с ${formatDayRange(name.sinceDate(illness, zone), name.sinceDate(illness, zone))}") { actions.onEditName(name) }
                }
                TextButton(onClick = actions.onAddName, modifier = Modifier.align(Alignment.CenterVertically)) {
                    Text(if (illness.names.isEmpty()) "+ Название" else "+ название")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                OutlinedButton(onClick = actions.onDoctor, modifier = Modifier.weight(1f)) {
                    AppIcon(R.drawable.ic_share, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Для врача")
                }
                if (illness.isActive) {
                    Button(onClick = actions.onRecover, modifier = Modifier.weight(1f).guideTarget(GuideTarget.RECOVER_BUTTON, ButtonDefaults.shape)) {
                        Text(bySex(sex, "Выздоровел", "Выздоровела"))
                    }
                } else {
                    actions.onResume?.let { resume ->
                        TextButton(onClick = resume, modifier = Modifier.weight(1f)) { Text("Ещё болеет") }
                    }
                }
            }
        }
    }
}

/** Название болезни пилюлей на фоне карточки: «ОРВИ  с 7 окт.»; касание — правка или «Убрать». */
@Composable
private fun NameChip(name: String, since: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spacing = BabyLogTheme.spacing
    AppCard(
        modifier = Modifier.minimumInteractiveComponentSize(),
        onClick = onClick,
        onClickLabel = "изменить название",
        containerColor = colors.surfaceContainerLow,
        contentPadding = PaddingValues(horizontal = spacing.m, vertical = spacing.s),
    ) {
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(spacing.s), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = MaterialTheme.typography.labelLarge)
            Text(since, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}
