package com.kid.babylog.feature.diary

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.BabyEvent
import com.kid.babylog.core.model.Child
import com.kid.babylog.core.model.Illness
import com.kid.babylog.core.model.Temperature
import com.kid.babylog.core.model.TemperatureMethod
import com.kid.babylog.core.model.ongoingCourses
import com.kid.babylog.core.model.recentIllness
import com.kid.babylog.core.ui.bySex
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.components.AppSheet
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatShortDay
import com.kid.babylog.core.ui.temperatureValue
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.toLocalDate
import com.kid.babylog.feature.editor.IllnessNameSheet
import com.kid.babylog.feature.editor.IllnessRecoveredSheet
import com.kid.babylog.feature.editor.IllnessStartSheet
import com.kid.babylog.feature.editor.TemperatureSheet
import com.kid.babylog.feature.illness.DoctorSheet
import com.kid.babylog.feature.illness.IllnessActions
import com.kid.babylog.feature.illness.IllnessContent
import com.kid.babylog.feature.illness.doctorText
import com.kid.babylog.feature.illness.illnessScreenModel
import java.time.ZoneId

private const val NONE = -1L
private val RowIconBox = 44.dp

/**
 * Какие листы здоровья и болезни открыты (ADR-0040): флаги и id записей — переживают поворот экрана.
 * [nameIndex] — правка названия болезни [nameIllnessId] по номеру; -1 — новое.
 */
@Stable
internal class HealthSheets {
    var menu by mutableStateOf(false)
    var startIllness by mutableStateOf(false)
    var temperature by mutableStateOf(false)
    var nameIllnessId by mutableLongStateOf(NONE)
    var nameIndex by mutableIntStateOf(-1)
    var recoverId by mutableLongStateOf(NONE)
    var doctorId by mutableLongStateOf(NONE)

    fun editName(illness: Illness, index: Int) {
        nameIllnessId = illness.id
        nameIndex = index
    }

    companion object {
        val Saver = listSaver<HealthSheets, Any>(
            save = { listOf(it.menu, it.startIllness, it.temperature, it.nameIllnessId, it.nameIndex, it.recoverId, it.doctorId) },
            restore = { saved ->
                HealthSheets().apply {
                    menu = saved[0] as Boolean
                    startIllness = saved[1] as Boolean
                    temperature = saved[2] as Boolean
                    nameIllnessId = saved[3] as Long
                    nameIndex = saved[4] as Int
                    recoverId = saved[5] as Long
                    doctorId = saved[6] as Long
                }
            },
        )
    }
}

@Composable
internal fun rememberHealthSheets(): HealthSheets = rememberSaveable(saver = HealthSheets.Saver) { HealthSheets() }

/**
 * Листы здоровья и болезни главного экрана (ADR-0040): «Здоровье» (лекарство, температура, «Заболел(а)»
 * или идущая болезнь), начало болезни, температура, название, «Выздоровел(а)» и «Для врача».
 * Лист лекарства открывает экран ([onAddMedicine]): он общий с плиткой и карточкой болезни.
 */
@Composable
internal fun HealthSheetsHost(
    sheets: HealthSheets,
    state: DiaryUiState,
    child: Child,
    now: Long,
    viewModel: DiaryViewModel,
    onAddMedicine: () -> Unit,
    onOpenIllness: (Illness) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val active = state.activeIllness
    fun illnessById(id: Long): Illness? = state.events.firstNotNullOfOrNull { (it as? Illness)?.takeIf { illness -> illness.id == id } }

    if (sheets.menu) {
        HealthSheet(
            child = child,
            state = state,
            now = now,
            onDismiss = { sheets.menu = false },
            onMedicine = { sheets.menu = false; onAddMedicine() },
            onTemperature = { sheets.menu = false; sheets.temperature = true },
            onIllness = {
                sheets.menu = false
                if (active != null) onOpenIllness(active) else sheets.startIllness = true
            },
        )
    }

    if (sheets.startIllness) {
        val recent = remember(state.events, now / 60_000) { state.events.recentIllness(now) }
        IllnessStartSheet(
            sex = child.sex,
            recent = recent,
            nameChoices = state.illnessNames,
            lastTemperature = state.lastTemperature,
            onDismiss = { sheets.startIllness = false },
            onStart = { start, names, note, tenths ->
                viewModel.startIllness(start, names, note, tenths)
                sheets.startIllness = false
            },
            onResume = { illness ->
                viewModel.resumeIllness(illness)
                sheets.startIllness = false
            },
        )
    }

    if (sheets.temperature) {
        val save = { tenths: Int, method: TemperatureMethod?, time: Long, note: String, startIllness: Boolean ->
            viewModel.saveTemperature(Temperature(childId = child.id, start = time, tenths = tenths, method = method, note = note), startIllness)
        }
        TemperatureSheet(
            initial = null,
            last = state.lastTemperature,
            sex = child.sex,
            offerIllness = active == null,
            onDismiss = { sheets.temperature = false },
            onSave = { tenths, method, time, note, startIllness ->
                save(tenths, method, time, note, startIllness)
                sheets.temperature = false
            },
            onSaveWithMedicine = if (active != null) { tenths, method, time, note ->
                save(tenths, method, time, note, false)
                sheets.temperature = false
                onAddMedicine()
            } else null,
        )
    }

    illnessById(sheets.nameIllnessId)?.let { illness ->
        val old = illness.names.getOrNull(sheets.nameIndex)
        val close = { sheets.nameIllnessId = NONE; sheets.nameIndex = -1 }
        IllnessNameSheet(
            illness = illness,
            initial = old,
            choices = state.illnessNames,
            onDismiss = close,
            onSave = { name ->
                viewModel.saveIllnessName(illness, old, name)
                close()
            },
            onRemove = old?.let {
                {
                    viewModel.removeIllnessName(illness, it)
                    close()
                }
            },
        )
    }

    illnessById(sheets.recoverId)?.let { illness ->
        IllnessRecoveredSheet(
            illness = illness,
            sex = child.sex,
            course = remember(state.events, illness) { state.events.ongoingCourses(illness, now) },
            onDismiss = { sheets.recoverId = NONE },
            onSave = { end ->
                viewModel.recoverIllness(illness, end)
                sheets.recoverId = NONE
            },
        )
    }

    illnessById(sheets.doctorId)?.let { illness ->
        val text = remember(state.events, illness, now / 60_000) {
            doctorText(illnessScreenModel(state.events, illness, now, zone), child, state.lastWeight, zone)
        }
        DoctorSheet(text = text, onDismiss = { sheets.doctorId = NONE })
    }
}

/**
 * Лист «Здоровье» (ADR-0040) за плиткой на «Сегодня»: лекарство, температура и «Заболел(а)», а пока
 * болеет — «Болезнь · N-й день»: она открывает экран болезни. Под каждой строкой — когда было в последний
 * раз: ночью по ним сверяются. Пол — для слова, имени нет.
 */
@Composable
private fun HealthSheet(
    child: Child,
    state: DiaryUiState,
    now: Long,
    onDismiss: () -> Unit,
    onMedicine: () -> Unit,
    onTemperature: () -> Unit,
    onIllness: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val today = now.toLocalDate()
    fun at(time: Long) = "${formatShortDay(time.toLocalDate(), today)} в ${formatClock(time)}"
    AppSheet(onDismiss = onDismiss, title = "Здоровье", icon = R.drawable.ic_first_aid, edgeToEdge = true) { closer ->
        HealthRow(
            icon = R.drawable.ic_medicine,
            title = "Лекарство",
            caption = state.lastMedicine?.let { "${it.name} — ${at(it.start)}" },
            onClick = { closer.close(onMedicine) },
        )
        HealthRow(
            icon = R.drawable.ic_thermometer,
            title = "Температура",
            caption = state.lastTemperature?.let { "в прошлый раз ${at(it.start)} — ${temperatureValue(it.tenths)}" },
            onClick = { closer.close(onTemperature) },
        )
        val illness = state.activeIllness
        val zone = ZoneId.systemDefault()
        HealthRow(
            icon = R.drawable.ic_illness,
            title = if (illness == null) bySex(child.sex, "Заболел", "Заболела") else "Болезнь · ${today.toEpochDay() - illness.start.toLocalDate(zone).toEpochDay() + 1}-й день",
            caption = if (illness == null) "температура, лекарства и заметки соберутся в одну болезнь" else illness.names.joinToString(", ") { it.name }.ifEmpty { "без названия" },
            onClick = { closer.close(onIllness) },
            iconBackground = colors.tertiaryContainer,
            iconTint = colors.onTertiaryContainer,
        )
    }
}

/** Строка листа «Здоровье»: иконка в круге, название и под ним — когда было в последний раз. */
@Composable
private fun HealthRow(
    @DrawableRes icon: Int,
    title: String,
    caption: String?,
    onClick: () -> Unit,
    iconBackground: Color = MaterialTheme.colorScheme.primaryContainer,
    iconTint: Color = MaterialTheme.colorScheme.onPrimaryContainer,
) {
    val spacing = BabyLogTheme.spacing
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = spacing.l, vertical = spacing.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing.m),
    ) {
        Box(Modifier.size(RowIconBox).background(iconBackground, CircleShape), contentAlignment = Alignment.Center) {
            AppIcon(icon, contentDescription = null, tint = iconTint)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            caption?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        AppIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Экран болезни [illness] внутри раздела (ADR-0040, ADR-0035): модель — из записей ребёнка раз в минуту,
 * действия открывают листы [sheets], правку записи ([onEditRecord]) и лист лекарства ([onAddMedicine]).
 */
@Composable
internal fun IllnessScreen(
    illness: Illness,
    state: DiaryUiState,
    child: Child,
    now: Long,
    sheets: HealthSheets,
    viewModel: DiaryViewModel,
    onBack: () -> Unit,
    onEditRecord: (BabyEvent) -> Unit,
    onAddMedicine: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = ZoneId.systemDefault()
    val model = remember(state.events, illness, now / 60_000) { illnessScreenModel(state.events, illness, now, zone) }
    IllnessContent(
        model = model,
        sex = child.sex,
        now = now,
        actions = IllnessActions(
            onBack = onBack,
            onEdit = { onEditRecord(illness) },
            onAddName = { sheets.editName(illness, -1) },
            onEditName = { name -> sheets.editName(illness, illness.names.indexOf(name)) },
            onRecover = { sheets.recoverId = illness.id },
            onResume = { viewModel.resumeIllness(illness); Unit }.takeIf { !illness.isActive && state.activeIllness == null },
            onDoctor = { sheets.doctorId = illness.id },
            onAddTemperature = { sheets.temperature = true },
            onAddMedicine = onAddMedicine,
            onEditRecord = onEditRecord,
        ),
        modifier = modifier,
    )
}
