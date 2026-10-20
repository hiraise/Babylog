package com.kid.babylog.feature.development

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.formatAge
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import java.time.LocalDate

/** Сколько советов недели видно на обзоре; остальные — в листе «все». */
private const val TIPS_ON_OVERVIEW = 3

/**
 * Раздел «Развитие» (ADR-0035, ADR-0037): неделя жизни, советы недели, «когда показать педиатру»
 * и окна навыков ВОЗ. «Подробнее» у навыков открывает экран навыков внутри раздела, системная «Назад»
 * возвращает к обзору. Совет целиком и все советы недели — нижние листы. Без даты рождения раздел
 * предлагает её указать ([onSetBirthDate]).
 */
@Composable
fun DevelopmentContent(
    child: Child,
    data: DevelopmentData?,
    today: LocalDate,
    onSetBirthDate: () -> Unit,
    modifier: Modifier = Modifier,
    initialSkills: Boolean = false,
) {
    var showSkills by rememberSaveable(child.id) { mutableStateOf(initialSkills) }
    var openTipId by rememberSaveable { mutableStateOf<String?>(null) }
    var showAllTips by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showSkills) { showSkills = false }
    val birth = child.birthDate?.toLocalDate()
    val week = remember(data, birth, today) { if (data != null && birth != null) developmentWeek(data, birth, today) else null }
    val spacing = BabyLogTheme.spacing
    // У обзора и экрана навыков своя прокрутка: «Назад» возвращает к обзору на то же место
    val overviewList = rememberLazyListState()
    val skillsList = rememberLazyListState()
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = if (showSkills) skillsList else overviewList,
        contentPadding = PaddingValues(start = spacing.l, top = spacing.m, end = spacing.l, bottom = spacing.xl),
        verticalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        when {
            data == null -> Unit
            birth == null -> item(key = "birth") { BirthDateCard(onSetBirthDate) }
            week == null -> item(key = "unborn") { NoteCard("Советы появятся, когда малыш родится.") }
            showSkills -> skillsDetail(week, data.milestones.source, onBack = { showSkills = false })
            else -> overview(
                week = week,
                age = formatAge(birth, today, child.sex),
                onOpenTip = { openTipId = it.id },
                onShowAll = { showAllTips = true },
                onShowSkills = { showSkills = true },
            )
        }
    }
    data?.tips?.find { it.id == openTipId }?.let { TipSheet(it, onDismiss = { openTipId = null }) }
    if (showAllTips && week != null) {
        AllTipsSheet(week, onOpen = { showAllTips = false; openTipId = it.id }, onDismiss = { showAllTips = false })
    }
}

private fun LazyListScope.overview(
    week: DevelopmentWeek,
    age: String,
    onOpenTip: (Tip) -> Unit,
    onShowAll: () -> Unit,
    onShowSkills: () -> Unit,
) {
    item(key = "week") { WeekCard(week, age) }
    if (week.allTips.isEmpty()) {
        item(key = "no-tips") { NoteCard("Советы — на первый год жизни. Для детей постарше их пока нет.") }
    }
    if (week.tips.isNotEmpty()) {
        item(key = "tips") {
            AppCard(Modifier.fillMaxWidth()) {
                CardColumn {
                    CardTitle(R.drawable.ic_lightbulb, "Советы недели") {
                        if (week.allTips.size > TIPS_ON_OVERVIEW) TextButton(onClick = onShowAll) { Text("все ${week.allTips.size}") }
                    }
                    week.tips.take(TIPS_ON_OVERVIEW).forEach { tip -> TipRow(tip, onOpen = { onOpenTip(tip) }) }
                }
            }
        }
    }
    if (week.doctorTips.isNotEmpty()) {
        item(key = "doctor") {
            val colors = MaterialTheme.colorScheme
            AppCard(Modifier.fillMaxWidth(), containerColor = colors.tertiaryContainer, contentColor = colors.onTertiaryContainer) {
                CardColumn {
                    CardTitle(R.drawable.ic_stethoscope, "Когда показать педиатру")
                    week.doctorTips.forEach { tip -> DoctorTipRow(tip, onOpen = { onOpenTip(tip) }) }
                }
            }
        }
    }
    item(key = "skills") {
        AppCard(Modifier.fillMaxWidth()) {
            CardColumn {
                CardTitle(R.drawable.ic_development, "Навыки по возрасту") {
                    TextButton(onClick = onShowSkills) { Text("подробнее") }
                }
                MilestoneChart(week.milestones, week.ageDays)
                Text(windowsSummary(week), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    item(key = "disclaimer") { Disclaimer() }
}

private fun LazyListScope.skillsDetail(week: DevelopmentWeek, source: TipSource, onBack: () -> Unit) {
    item(key = "header") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { AppIcon(R.drawable.ic_chevron_left, contentDescription = "Назад к разделу") }
            Text("Навыки по возрасту", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        }
    }
    item(key = "chart") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
                Text(
                    "Полоса — возраст, в котором навык осваивают 98 детей из 100 (1-й и 99-й перцентили ВОЗ). " +
                        "Нет навыка и после правого края полосы — обсудите с педиатром.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                MilestoneChart(week.milestones, week.ageDays, rowHeight = 40.dp)
                SourceLink(source)
            }
        }
    }
    item(key = "windows") {
        AppCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
                week.milestones.forEach { (m, state) ->
                    Column {
                        Text(m.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "${stateText(state)} · с ${formatMonthValue(m.months.start)} до ${formatMonthValue(m.months.endInclusive)} мес",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    item(key = "disclaimer") { Disclaimer() }
}

@Composable
private fun WeekCard(week: DevelopmentWeek, age: String) {
    val colors = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth(), containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer) {
        Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.xs)) {
            Text(weekTitle(week.week), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            Text("$age · новые советы — каждую неделю", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Содержимое карточки с шагом `spacing.s`: `AppCard` сама шага между строками не даёт. */
@Composable
private fun CardColumn(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) { content() }
}

@Composable
private fun CardTitle(icon: Int, title: String, action: @Composable () -> Unit = {}) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
    ) {
        AppIcon(icon, contentDescription = null)
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        action()
    }
}

@Composable
private fun BirthDateCard(onSetBirthDate: () -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.m)) {
            Text("Укажите дату рождения", style = MaterialTheme.typography.titleMedium)
            Text(
                "По ней раздел подберёт советы на каждую неделю жизни и покажет окна навыков ВОЗ.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(onClick = onSetBirthDate) { Text("Указать дату рождения") }
        }
    }
}

@Composable
private fun NoteCard(text: String) {
    AppCard(Modifier.fillMaxWidth()) { Text(text, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun Disclaimer() {
    Text(
        "Справочная информация, не заменяет консультацию врача. Источники — у каждого совета.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun stateText(state: MilestoneState): String = when (state) {
    MilestoneState.AHEAD -> "впереди"
    MilestoneState.IN_WINDOW -> "сейчас в окне"
    MilestoneState.PAST -> "окно прошло"
}

/** Подпись под графиком на обзоре: какие окна сейчас открыты, а если ни одного — ближайшее. */
internal fun windowsSummary(week: DevelopmentWeek): String {
    val inWindow = week.milestones.filter { it.second == MilestoneState.IN_WINDOW }.map { it.first.title.lowercase() }
    val next = week.milestones.firstOrNull { it.second == MilestoneState.AHEAD }?.first
    return when {
        inWindow.isNotEmpty() -> "Сейчас в окне: ${inWindow.joinToString(", ")}."
        next != null -> "Ближайшее окно — «${next.title.lowercase()}», с ${formatMonthValue(next.months.start)} мес."
        else -> "Окна всех шести навыков уже прошли."
    }
}
