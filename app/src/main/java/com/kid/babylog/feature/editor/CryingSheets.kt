package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.Crying
import com.kid.babylog.core.model.LONGER_THAN_USUAL
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.ui.DateTimeField
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.diaperIcon
import com.kid.babylog.core.ui.diaperLabel
import com.kid.babylog.core.ui.formatAgo
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.theme.BabyLogTheme

private val ContextIconSize = 20.dp
private const val MINUTE = 60_000L

/** «Начался раньше?»: минуты и подпись чипа. Без «−»: «раньше» уже в вопросе, и ряд влезает в одну строку. */
private val EARLIER_OPTIONS = listOf(0 to "Нет", 5 to "5 мин", 10 to "10 мин", 15 to "15 мин")

/**
 * Лист после остановки плача (ADR-0030): сколько длился, «Начался раньше?» — таймер жмут, когда ребёнок
 * уже плачет, — «Что было до» из дневника ([context]), «Почему, как думаете?» — метки, общие с заметками,
 * «Что помогло?» — свой список, комментарий. «Сохранить» пишет начало и подробности в запись; «Ещё плачет»
 * ([onResume]) — остановили по ошибке, таймер идёт дальше; закрыть лист свайпом — плач остаётся без подробностей.
 */
@Composable
fun CryingDoneSheet(
    crying: Crying,
    context: CryingContext?,
    choices: CryingChoices,
    sex: Sex?,
    onResume: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (start: Long, reasons: List<String>, helped: List<String>, note: String) -> Unit,
) {
    val end = crying.end ?: crying.start
    var earlier by rememberSaveable { mutableIntStateOf(0) }
    val start = crying.start - earlier * MINUTE
    var reasons by rememberSaveable { mutableStateOf(crying.reasons.packed()) }
    var helped by rememberSaveable { mutableStateOf(crying.helped.packed()) }
    var note by rememberSaveable { mutableStateOf(crying.note) }
    AppSheet(
        onDismiss = onDismiss,
        title = "Плач · ${formatDuration(end - start)}",
        icon = R.drawable.ic_crying,
        confirm = SheetAction("Сохранить") { onSave(start, reasons.unpacked(), helped.unpacked(), note.trim()) },
        secondary = SheetAction("Ещё плачет", onClick = onResume),
    ) {
        CryingDoneFields(
            crying = crying,
            start = start,
            earlier = earlier,
            onEarlier = { earlier = it },
            context = context,
            choices = choices,
            sex = sex,
            reasons = reasons.unpacked(),
            onReasons = { reasons = it.packed() },
            helped = helped.unpacked(),
            onHelped = { helped = it.packed() },
            note = note,
            onNote = { note = it },
        )
    }
}

/**
 * Строки листа «Плач закончился» без самого листа — время, «Начался раньше?», «Что было до», метки,
 * комментарий. Отдельно, чтобы скриншот-тест снимал их на фоне листа (ModalBottomSheet в него не попадает).
 * [start] — начало с поправкой [earlier] минут.
 */
@Composable
internal fun CryingDoneFields(
    crying: Crying,
    start: Long,
    earlier: Int,
    onEarlier: (Int) -> Unit,
    context: CryingContext?,
    choices: CryingChoices,
    sex: Sex?,
    reasons: List<String>,
    onReasons: (List<String>) -> Unit,
    helped: List<String>,
    onHelped: (List<String>) -> Unit,
    note: String,
    onNote: (String) -> Unit,
) {
    Text(
        "${formatClock(start)}–${formatClock(crying.end ?: crying.start)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    StartedEarlier(earlier, onEarlier)
    context?.let { CryingContextCard(it, sex) }
    CryingDetails(reasons, onReasons, helped, onHelped, choices)
    NoteField(note, onNote)
}

/** «Начался раньше?»: нет или на 5, 10, 15 минут — без выбора даты и времени. Точнее — правкой в «Истории». */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StartedEarlier(earlier: Int, onEarlier: (Int) -> Unit) {
    Column {
        Text("Начался раньше, чем нажали?", style = MaterialTheme.typography.titleSmall)
        // Между рядами чипов воздух уже есть: каждый чип занимает 48 dp по высоте под палец
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s)) {
            EARLIER_OPTIONS.forEach { (minutes, label) ->
                ChoiceChip(selected = earlier == minutes, onClick = { onEarlier(minutes) }, label = label)
            }
        }
    }
}

/** Правка плача из «Истории»: начало и (у законченного) конец, причины, что помогло, комментарий. */
@Composable
internal fun CryingEditSheet(
    initial: Crying,
    choices: CryingChoices,
    onDismiss: () -> Unit,
    onSave: (start: Long, end: Long?, reasons: List<String>, helped: List<String>, note: String) -> Unit,
) {
    var start by rememberSaveable { mutableLongStateOf(initial.start) }
    // -1 — идущий плач без конца: mutableLongStateOf не хранит null
    var end by rememberSaveable { mutableLongStateOf(initial.end ?: -1L) }
    var reasons by rememberSaveable { mutableStateOf(initial.reasons.packed()) }
    var helped by rememberSaveable { mutableStateOf(initial.helped.packed()) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    val hasEnd = end >= 0
    val valid = !hasEnd || end >= start
    AppSheet(
        onDismiss = onDismiss,
        title = "Плач",
        icon = R.drawable.ic_crying,
        confirm = SheetAction("Сохранить", enabled = valid) {
            onSave(start, if (hasEnd) end else null, reasons.unpacked(), helped.unpacked(), note.trim())
        },
    ) {
        DateTimeField("Начался:", start) { start = it }
        if (hasEnd) {
            DateTimeField("Закончился:", end) { end = it }
            if (!valid) {
                Text(
                    "Конец плача не может быть раньше начала",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        CryingDetails(reasons.unpacked(), { reasons = it.packed() }, helped.unpacked(), { helped = it.packed() }, choices)
        NoteField(note, { note = it })
    }
}

/** «Почему» — метки, «что помогло» — свой список; в обоих можно выбрать несколько и дописать своё. */
@Composable
private fun CryingDetails(
    reasons: List<String>,
    onReasons: (List<String>) -> Unit,
    helped: List<String>,
    onHelped: (List<String>) -> Unit,
    choices: CryingChoices,
) {
    TagPicker("Почему, как думаете?", choices.reasons.shown, reasons, onReasons, addLabel = "своя метка", other = choices.reasons.other, otherTitle = "Метки заметок:")
    TagPicker("Что помогло?", choices.helped, helped, onHelped, addLabel = "своё")
}

/**
 * «Что было до» — до трёх строк из дневника: сколько не спал (и «обычно …», если заметно дольше),
 * когда и как ел, когда меняли подгузник. Строк нет — карточки нет.
 */
@Composable
private fun CryingContextCard(context: CryingContext, sex: Sex?) {
    val colors = MaterialTheme.colorScheme
    val spacing = BabyLogTheme.spacing
    val awake = context.awakeMillis
    val usual = context.usualAwakeMillis
    val longer = awake != null && usual != null && awake > usual * LONGER_THAN_USUAL
    val lines = buildList {
        awake?.let { add(Triple(R.drawable.ic_sleep, "${bySex(sex, "Не спал", "Не спала")} ${formatDuration(it)}", usual?.takeIf { longer })) }
        context.sinceFeedingMillis?.let { since ->
            val text = "${bySex(sex, "Ел", "Ела")} ${formatAgo(since)}" + (context.feeding?.let { " · $it" } ?: "")
            add(Triple(if (context.feedingByBottle) R.drawable.ic_bottle else R.drawable.ic_feeding, text, null))
        }
        context.sinceDiaperMillis?.let { since ->
            val kind = context.diaper
            val text = "Подгузник ${formatAgo(since)}" + (kind?.let { " · ${diaperLabel(it, sex)}" } ?: "")
            add(Triple(kind?.let(::diaperIcon) ?: R.drawable.ic_diaper, text, null))
        }
    }
    if (lines.isEmpty()) return
    AppCard(Modifier.fillMaxWidth(), containerColor = colors.surfaceContainerHighest) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Что было до", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Text("из дневника", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
            }
            lines.forEach { (icon, text, usualMillis) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                    AppIcon(icon, contentDescription = null, modifier = Modifier.size(ContextIconSize), tint = colors.primary)
                    Column {
                        Text(text, style = MaterialTheme.typography.bodyMedium)
                        // Заметно дольше обычного — подсказка, на что посмотреть; цвет только привлекает внимание
                        usualMillis?.let {
                            Text("обычно ${formatDuration(it)}", style = MaterialTheme.typography.bodySmall, color = colors.tertiary)
                        }
                    }
                }
            }
        }
    }
}
