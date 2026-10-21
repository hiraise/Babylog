package com.kid.babylog.feature.analytics

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.theme.BabyLogTheme
import java.time.LocalDate
import java.time.ZoneId

/**
 * Вкладка «Аналитика» (ADR-0028, ADR-0029): обзор плиток ([AnalyticsOverview]) и подробный экран
 * раздела ([AnalyticsDetail]). Всё считается из [events] выбранного ребёнка при показе ([analyticsModel]).
 *
 * Касание плитки открывает раздел, «Назад» — стрелка или системная — возвращает к обзору на то же
 * место прокрутки. На экране раздела касание столбика показывает итоги под графиком, «В историю»
 * и строка ритма открывают день в «Истории» ([onOpenDay]). Период, раздел и выбранный столбик
 * переживают поворот; при смене ребёнка выбор столбика сбрасывается, раздел остаётся открытым.
 * [initialSection] — раздел, который открыт сразу: карточка плача на «Заметках» ведёт в «Плач»;
 * метка в разделе «Метки» открывает её на «Заметках» ([onOpenTag], ADR-0031).
 */
@Composable
fun AnalyticsContent(
    events: List<BabyEvent>,
    child: Child,
    now: Long,
    onOpenDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    initialSection: AnalyticsSection? = null,
    onOpenTag: (String) -> Unit = {},
) {
    var period by rememberSaveable { mutableStateOf(AnalyticsPeriod.WEEK) }
    var section by rememberSaveable { mutableStateOf(initialSection) }
    // Выбранный столбик — «метрика:номер»: строка сохраняется в Bundle как есть
    var selection by rememberSaveable(period, child.id) { mutableStateOf<String?>(null) }
    // Пересчитываем раз в минуту, а не на каждый тик секундного таймера экрана
    val minuteNow = now / 60_000
    val model = remember(events, period, minuteNow, child.sex) {
        analyticsModel(events, period, now, ZoneId.systemDefault(), child.sex)
    }
    // Прокрутка обзора живёт, пока открыт раздел: «Назад» возвращает к той же плитке
    val overviewList = rememberLazyListState()
    BackHandler(enabled = section != null) { section = null }

    val open = section
    if (open == null) {
        AnalyticsOverview(
            model = model,
            period = period,
            onPeriod = { period = it },
            onOpen = { section = it },
            listState = overviewList,
            modifier = modifier,
        )
    } else {
        AnalyticsDetail(
            section = open,
            model = model,
            period = period,
            onPeriod = { period = it },
            selection = selection,
            onSelection = { selection = it },
            onOpenDay = onOpenDay,
            onBack = { section = null },
            modifier = modifier,
            onOpenTag = onOpenTag,
        )
    }
}

/** Отступы списка вкладки: сверху не меньше размытия тени карточек, как на других вкладках. */
@Composable
internal fun analyticsListPadding(): PaddingValues {
    val spacing = BabyLogTheme.spacing
    return PaddingValues(start = spacing.l, top = spacing.m, end = spacing.l, bottom = spacing.xl)
}

/** Чипы периода, пояснение к штриховке и — если в периоде болел — когда ([IllnessLegend], ADR-0040). */
@Composable
internal fun PeriodPicker(period: AnalyticsPeriod, onSelect: (AnalyticsPeriod) -> Unit, illnessNote: String? = null) {
    val spacing = BabyLogTheme.spacing
    Column {
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
            AnalyticsPeriod.entries.forEach { option ->
                ChoiceChip(selected = option == period, onClick = { onSelect(option) }, label = option.title)
            }
        }
        Text(
            if (period == AnalyticsPeriod.ALL) {
                "Заштрихована неделя, которая ещё идёт: в средние входят только прошедшие сутки"
            } else {
                "Заштрихован день, который ещё идёт: в средние он не входит"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        illnessNote?.let { IllnessLegend(it) }
    }
}

/** Карточка-подсказка вместо графиков: [text] посередине. */
@Composable
internal fun HintCard(text: String) {
    AppCard(Modifier.fillMaxWidth()) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
