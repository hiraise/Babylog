package com.kid.babylog.feature.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.kid.babylog.R
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.IllnessName
import com.kid.babylog.core.model.MAX_ILLNESS_NAME_LENGTH
import com.kid.babylog.core.model.Sex
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.lastDate
import com.kid.babylog.core.model.normalizeIllnessName
import com.kid.babylog.core.model.startDate
import com.kid.babylog.core.model.title
import com.kid.babylog.core.ui.DateTimeField
import com.kid.babylog.core.ui.DayPickSheet
import com.kid.babylog.core.ui.LocalClock
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.components.HintChip
import com.kid.babylog.core.ui.components.SheetAction
import com.kid.babylog.core.ui.formatChipDay
import com.kid.babylog.core.ui.formatDayRange
import com.kid.babylog.core.ui.plural
import com.kid.babylog.core.ui.temperatureValue
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import java.time.LocalDate
import java.time.ZoneId

private const val DAY_MILLIS = 86_400_000L

/** Подсказок названия под полем — не больше стольких: ряд-два, а не стена чипов. */
private const val MAX_NAME_HINTS = 5

/** Когда началась болезнь: сейчас, сутки назад или своё время. */
private enum class StartChoice(val label: String) { NOW("Сейчас"), YESTERDAY("Вчера"), OTHER("Раньше") }

/**
 * Лист «Заболел(а)» (ADR-0040): всё, кроме «Начать», — по желанию. Когда началось, что это (названия
 * [nameChoices] — прошлые и частые; можно несколько, можно потом), температура сейчас и комментарий.
 * Прошлая болезнь [recent] кончилась недавно — сначала вопрос: новая болезнь или прошлая вернулась
 * ([onResume] — у неё снова нет конца). Пол [sex] — для слова, имени в листе нет.
 */
@Composable
internal fun IllnessStartSheet(
    sex: Sex?,
    recent: Illness?,
    nameChoices: List<String>,
    lastTemperature: Temperature?,
    onDismiss: () -> Unit,
    onStart: (start: Long, names: List<String>, note: String, temperatureTenths: Int?) -> Unit,
    onResume: (Illness) -> Unit,
) {
    val clock = LocalClock.current
    var resume by rememberSaveable { mutableStateOf(false) }
    var startChoice by rememberSaveable { mutableStateOf(StartChoice.NOW) }
    var customStart by rememberSaveable { mutableLongStateOf(clock.millis() - 2 * DAY_MILLIS) }
    var names by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var withTemperature by rememberSaveable { mutableStateOf(false) }
    var temperature by rememberSaveable { mutableStateOf(temperatureValue(lastTemperature?.tenths ?: 366)) }
    val tenths = parseTemperature(temperature)
    val resumed = recent?.takeIf { resume }
    AppSheet(
        onDismiss = onDismiss,
        title = bySex(sex, "Заболел", "Заболела"),
        icon = R.drawable.ic_illness,
        confirm = if (resumed != null) {
            SheetAction("Продолжить") { onResume(resumed) }
        } else {
            SheetAction("Начать", enabled = !withTemperature || tenths != null) {
                val start = when (startChoice) {
                    StartChoice.NOW -> clock.millis()
                    StartChoice.YESTERDAY -> clock.millis() - DAY_MILLIS
                    StartChoice.OTHER -> customStart.coerceAtMost(clock.millis())
                }
                onStart(start, names.unpacked(), note.trim(), tenths.takeIf { withTemperature })
            }
        },
    ) {
        if (recent != null) RelapseChoice(recent, resume) { resume = it }
        if (resumed == null) {
            Text("Всё ниже — по желанию: можно сразу «Начать».", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.xs)) {
                Text("Когда началось?", style = MaterialTheme.typography.titleSmall)
                OptionChips(StartChoice.entries.map { it to it.label }, startChoice, onSelect = { startChoice = it })
                if (startChoice == StartChoice.OTHER) DateTimeField("Началось:", customStart) { customStart = it }
            }
            TagPicker(
                title = "Что это, если знаете",
                options = nameChoices,
                selected = names.unpacked(),
                onChange = { names = it.packed() },
                addLabel = "своё",
                normalize = ::normalizeIllnessName,
                maxLength = MAX_ILLNESS_NAME_LENGTH,
            )
            Text(
                "Название можно добавить потом, когда скажет врач, и не одно.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (withTemperature) {
                Text("Температура", style = MaterialTheme.typography.titleSmall)
                TemperatureField(temperature, { temperature = it }, previous = null)
            } else {
                TextButton(onClick = { withTemperature = true }) { Text("+ Температура") }
            }
            NoteField(note, { note = it })
        }
    }
}

/**
 * Прошлая болезнь [recent] кончилась недавно: новая — своя запись, или прошлая вернулась — у неё
 * снова нет конца, а дни между ними войдут в неё. Решает человек: по записям этого не понять.
 */
@Composable
private fun RelapseChoice(recent: Illness, resume: Boolean, onResume: (Boolean) -> Unit) {
    val spacing = BabyLogTheme.spacing
    val now = LocalClock.current.millis()
    val zone = ZoneId.systemDefault()
    val ended = recent.end ?: now
    val what = recent.title()?.let { "— $it — " } ?: ""
    AppCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            "Прошлая болезнь ${what}закончилась ${formatChipDay(ended.toLocalDate(), now.toLocalDate())}.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
        RadioRow(
            selected = !resume,
            title = "Новая болезнь",
            caption = "своя, с новыми названиями; прошлая останется как была",
            onClick = { onResume(false) },
        )
        RadioRow(
            selected = resume,
            title = "Прошлая вернулась",
            caption = "болезнь с ${formatChipDay(recent.startDate(zone), now.toLocalDate())} продолжится, дни между ними войдут в неё",
            onClick = { onResume(true) },
        )
    }
}

@Composable
private fun RadioRow(selected: Boolean, title: String, caption: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Название болезни (ADR-0040): новое или правка [initial] — тогда «Убрать» вместо «Отмены». Подсказки —
 * [choices] без названий, которые у болезни уже есть. «С какого дня» — сегодня, вчера, с начала болезни
 * или другой день внутри неё: отит находят на 4-й день, а ОРВИ стоит с начала.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun IllnessNameSheet(
    illness: Illness,
    initial: IllnessName?,
    choices: List<String>,
    onDismiss: () -> Unit,
    onSave: (IllnessName) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val spacing = BabyLogTheme.spacing
    val zone = ZoneId.systemDefault()
    val today = LocalClock.current.millis().toLocalDate()
    val first = illness.startDate(zone)
    val last = illness.lastDate(today, zone)
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    // -1 — с начала болезни (since = null); иначе epoch day
    var sinceDay by rememberSaveable { mutableLongStateOf(initial?.since?.toEpochDay() ?: if (illness.isActive) today.toEpochDay() else -1L) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val normalized = normalizeIllnessName(name)
    val others = illness.names.filter { it != initial }.map { it.name.lowercase() }
    val taken = normalized != null && normalized.lowercase() in others
    val hints = remember(choices, name, others) {
        val typed = name.trim().lowercase()
        choices.filter { it.lowercase() !in others && it.lowercase() != typed && (typed.isEmpty() || it.lowercase().startsWith(typed)) }.take(MAX_NAME_HINTS)
    }
    AppSheet(
        onDismiss = onDismiss,
        title = if (initial == null) "Название болезни" else "Название",
        icon = R.drawable.ic_illness,
        confirm = SheetAction(if (initial == null) "Добавить" else "Сохранить", enabled = normalized != null && !taken) {
            if (normalized != null) {
                val since = sinceDay.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay)?.takeIf { it > first }
                onSave(IllnessName(normalized, since))
            }
        },
        secondary = onRemove?.let { SheetAction("Убрать", onClick = it) },
    ) {
        illness.title()?.let {
            Text("Уже есть: $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(MAX_ILLNESS_NAME_LENGTH) },
            label = { Text("Название") },
            supportingText = if (taken) ({ Text("Такое название уже есть") }) else null,
            isError = taken,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        )
        if (hints.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                hints.forEach { hint -> HintChip(label = hint, onClick = { name = hint }) }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text("С какого дня", style = MaterialTheme.typography.titleSmall)
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                val options = buildList {
                    add(-1L to "с начала, ${formatDayRange(first, first)}")
                    if (last > first) add(last.toEpochDay() to formatChipDay(last, today))
                    if (last.minusDays(1) > first) add(last.minusDays(1).toEpochDay() to formatChipDay(last.minusDays(1), today))
                }
                options.forEach { (day, label) ->
                    ChoiceChip(selected = sinceDay == day, onClick = { sinceDay = day }, label = label)
                }
                val custom = sinceDay >= 0 && options.none { it.first == sinceDay }
                ChoiceChip(
                    selected = custom,
                    onClick = { picking = true },
                    label = if (custom) formatChipDay(LocalDate.ofEpochDay(sinceDay), today) else "Другой день",
                )
            }
        }
    }
    if (picking) {
        DayPickSheet(initial = sinceDay.takeIf { it >= 0 }?.let(LocalDate::ofEpochDay) ?: last, onDismiss = { picking = false }) { day ->
            sinceDay = if (day <= first) -1L else minOf(day, last).toEpochDay()
            picking = false
        }
    }
}

/**
 * «Выздоровел(а)» (ADR-0040): когда — сегодня, вчера, позавчера или другой день, не раньше начала.
 * Конец — в это же время суток выбранного дня. [course] — лекарства, начатые в болезни и ещё идущие:
 * их приёмы до конца курса останутся в болезни.
 */
@Composable
internal fun IllnessRecoveredSheet(
    illness: Illness,
    sex: Sex?,
    course: List<String>,
    onDismiss: () -> Unit,
    onSave: (end: Long) -> Unit,
) {
    val spacing = BabyLogTheme.spacing
    val zone = ZoneId.systemDefault()
    val now = LocalClock.current.millis()
    val today = now.toLocalDate()
    val first = illness.startDate(zone)
    var daysAgo by rememberSaveable { mutableIntStateOf(0) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val endDay = today.minusDays(daysAgo.toLong()).coerceAtLeast(first)
    val end = (now - daysAgo * DAY_MILLIS).coerceAtLeast(illness.start)
    AppSheet(
        onDismiss = onDismiss,
        title = bySex(sex, "Выздоровел", "Выздоровела"),
        icon = R.drawable.ic_illness,
        confirm = SheetAction(bySex(sex, "Выздоровел", "Выздоровела")) { onSave(end) },
    ) {
        illness.title()?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary) }
        Column(verticalArrangement = Arrangement.spacedBy(spacing.xs)) {
            Text("Когда?", style = MaterialTheme.typography.titleSmall)
            FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                (0..2).filter { !today.minusDays(it.toLong()).isBefore(first) }.forEach { ago ->
                    ChoiceChip(selected = daysAgo == ago, onClick = { daysAgo = ago }, label = formatChipDay(today.minusDays(ago.toLong()), today))
                }
                val custom = daysAgo > 2
                ChoiceChip(selected = custom, onClick = { picking = true }, label = if (custom) formatChipDay(endDay, today) else "Другой день")
            }
        }
        val days = (endDay.toEpochDay() - first.toEpochDay() + 1).toInt()
        AppCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Text(
                "${bySex(sex, "Болел", "Болела")} $days ${plural(days, "день", "дня", "дней")} · ${formatDayRange(first, endDay)}",
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (course.isNotEmpty()) {
            Text(
                "${course.joinToString(", ")} ещё даёте? Приёмы до конца курса тоже покажутся в этой болезни.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (picking) {
        DayPickSheet(initial = endDay, onDismiss = { picking = false }) { day ->
            daysAgo = (today.toEpochDay() - day.coerceAtLeast(first).toEpochDay()).toInt().coerceAtLeast(0)
            picking = false
        }
    }
}

/** Правка болезни: начало, конец (у закончившейся) и комментарий; названия правят на её экране. */
@Composable
internal fun IllnessEditSheet(
    initial: Illness,
    onDismiss: () -> Unit,
    onSave: (start: Long, end: Long?, note: String) -> Unit,
) {
    var start by rememberSaveable { mutableLongStateOf(initial.start) }
    // -1 — идущая болезнь без конца: mutableLongStateOf не хранит null
    var end by rememberSaveable { mutableLongStateOf(initial.end ?: -1L) }
    var note by rememberSaveable { mutableStateOf(initial.note) }
    val hasEnd = end >= 0
    val valid = !hasEnd || end >= start
    AppSheet(
        onDismiss = onDismiss,
        title = "Болезнь",
        icon = R.drawable.ic_illness,
        confirm = SheetAction("Сохранить", enabled = valid) { onSave(start, if (hasEnd) end else null, note.trim()) },
    ) {
        DateTimeField("Началась:", start) { start = it }
        if (hasEnd) {
            DateTimeField("Закончилась:", end) { end = it }
            if (!valid) {
                Text("Конец болезни не может быть раньше начала", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
        NoteField(note, { note = it })
    }
}
