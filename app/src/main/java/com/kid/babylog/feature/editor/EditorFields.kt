package com.kid.babylog.feature.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kid.babylog.core.ui.DateTimeField
import com.kid.babylog.core.ui.LocalClock
import com.kid.babylog.core.ui.components.ChoiceChip
import com.kid.babylog.core.ui.components.cardShadow
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatDuration
import com.kid.babylog.core.ui.theme.BabyLogTheme
import kotlinx.coroutines.delay

private val TIME_OPTIONS = listOf(
    "Сейчас" to 0,
    "−5 мин" to 5,
    "−15 мин" to 15,
    "−30 мин" to 30,
    "−1 ч" to 60,
)

/** Своё время — не больше суток назад: давнее записывают правкой, с датой и временем. */
internal const val MAX_MINUTES_AGO = 24 * 60 - 1

/**
 * Таймер, который закончится, когда начнётся новый (ADR-0020): [what] — «Кормление» или «Сон»,
 * [start] — его начало. Конец не раньше начала.
 */
class FinishingTimer(val what: String, val start: Long)

/**
 * Выбор «когда это было»: сейчас, N минут назад по чипу или своё число минут в чипе «Другое»
 * ([CustomMinutesChip]). С [finishing] под временем — когда закончится идущий другой таймер:
 * сон и кормление не идут одновременно.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TimeAgoPicker(minutesAgo: Int, onMinutesAgo: (Int) -> Unit, finishing: FinishingTimer? = null) {
    val clock = LocalClock.current
    val spacing = BabyLogTheme.spacing
    // Часы тикают, пока лист открыт, чтобы подпись не расходилась с реально сохраняемым временем
    var now by remember { mutableLongStateOf(clock.millis()) }
    LaunchedEffect(clock) {
        while (true) {
            now = clock.millis()
            delay(1_000)
        }
    }
    // null — выбран чип; строка — своё число минут, как набрано
    var custom by rememberSaveable { mutableStateOf<String?>(null) }
    Column {
        Text("Когда:", style = MaterialTheme.typography.titleSmall)
        // Между рядами чипов уже есть воздух: каждый чип занимает 48 dp по высоте под палец
        FlowRow(
            modifier = Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(spacing.s),
        ) {
            TIME_OPTIONS.forEach { (label, minutes) ->
                ChoiceChip(
                    selected = custom == null && minutesAgo == minutes,
                    onClick = {
                        custom = null
                        onMinutesAgo(minutes)
                    },
                    label = label,
                )
            }
            CustomMinutesChip(
                text = custom,
                onText = { typed ->
                    custom = customMinutesInput(typed)
                    onMinutesAgo(customMinutes(custom.orEmpty()))
                },
            )
        }
        val time = now - minutesAgo * 60_000L
        Text(
            "Время: " + formatClock(time) + customHint(custom, minutesAgo),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = spacing.xs),
        )
        if (finishing != null) {
            Text(
                "${finishing.what} закончится в ${formatClock(maxOf(finishing.start, time))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Чип «Другое»: касание превращает его на месте в поле минут с цифровой клавиатурой — «−25 мин».
 * [text] = null — обычный чип, строка — поле с набранным.
 *
 * Поле именно в чипе, а не строкой под чипами: высота листа не меняется, поэтому лист не подскакивает,
 * когда поле появляется, и с клавиатурой помещается над ней — едет вверх вместе с ней. Поле под
 * чипами давало два рывка: лист прыгал на высоту поля, а с клавиатурой разворачивался на весь экран.
 * Вид — как у выбранного [ChoiceChip]: фон `primary`, тень темы, высота чипа.
 */
@Composable
private fun CustomMinutesChip(text: String?, onText: (String) -> Unit) {
    if (text == null) {
        ChoiceChip(selected = false, onClick = { onText("") }, label = "Другое")
        return
    }
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small
    val style = MaterialTheme.typography.labelLarge.copy(color = colors.onPrimary)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Box(Modifier.minimumInteractiveComponentSize(), contentAlignment = Alignment.Center) {
        BasicTextField(
            value = text,
            onValueChange = onText,
            modifier = Modifier
                .focusRequester(focus)
                .semantics { contentDescription = "Минут назад" },
            textStyle = style,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            cursorBrush = SolidColor(colors.onPrimary),
            decorationBox = { field ->
                Row(
                    modifier = Modifier
                        .cardShadow(shape, BabyLogTheme.shadows.card)
                        .background(colors.primary, shape)
                        .height(FilterChipDefaults.Height)
                        .padding(horizontal = BabyLogTheme.spacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("−", style = style)
                    // Пустое поле не схлопывается: курсору и первой цифре есть место
                    Box(Modifier.widthIn(min = 12.dp)) { field() }
                    Text(" мин", style = style)
                }
            },
        )
    }
}

/** Ввод своих минут: только цифры, не больше четырёх. */
internal fun customMinutesInput(text: String): String = text.filter(Char::isDigit).take(4)

/** Минуты назад из ввода [customMinutesInput]: пусто — сейчас, больше суток — сутки без минуты. */
internal fun customMinutes(text: String): Int = text.toIntOrNull()?.coerceAtMost(MAX_MINUTES_AGO) ?: 0

/**
 * Пояснение к времени для своих минут: от часа — « · 1 ч 30 мин назад» (часы набирают минутами:
 * 90), больше суток — « · не больше суток». У чипов и до часа — пусто: время и так понятно.
 */
internal fun customHint(text: String?, minutesAgo: Int): String = when {
    text == null -> ""
    (text.toIntOrNull() ?: 0) > MAX_MINUTES_AGO -> " · не больше суток"
    minutesAgo >= 60 -> " · " + formatDuration(minutesAgo * 60_000L) + " назад"
    else -> ""
}

/** Добавление: чипы «N минут назад»; редактирование: полноценные дата и время. */
@Composable
internal fun TimeSection(
    isEdit: Boolean,
    minutesAgo: Int,
    onMinutesAgo: (Int) -> Unit,
    editTime: Long,
    onEditTime: (Long) -> Unit,
) {
    if (isEdit) {
        DateTimeField("Когда:", editTime, onEditTime)
    } else {
        TimeAgoPicker(minutesAgo, onMinutesAgo)
    }
}

/** Время записи: при правке — выбранное, при добавлении — «сейчас минус N минут». */
@Composable
internal fun rememberTimeResolver(): (isEdit: Boolean, minutesAgo: Int, editTime: Long) -> Long {
    val clock = LocalClock.current
    return remember(clock) {
        { isEdit, minutesAgo, editTime -> if (isEdit) editTime else clock.millis() - minutesAgo * 60_000L }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> OptionChips(
    options: List<Pair<T, String>>, // значение и подпись
    selected: T?,
    onSelect: (T) -> Unit,
    icon: (T) -> Int? = { null }, // иконка варианта перед подписью, null — без иконки
) {
    FlowRow(
        modifier = Modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(BabyLogTheme.spacing.s),
    ) {
        options.forEach { (value, label) ->
            ChoiceChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = label,
                icon = icon(value),
            )
        }
    }
}

@Composable
internal fun NoteField(value: String, onChange: (String) -> Unit, label: String = "Комментарий (необязательно)") {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}
