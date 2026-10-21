package com.kid.babylog.feature.illness

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.kid.babylog.R
import com.kid.babylog.core.model.startDate
import com.kid.babylog.core.ui.components.AppCard
import com.kid.babylog.core.ui.components.AppIcon
import com.kid.babylog.core.ui.formatAgo
import com.kid.babylog.core.ui.formatClock
import com.kid.babylog.core.ui.formatShortDay
import com.kid.babylog.core.ui.formatWeekdayDay
import com.kid.babylog.core.ui.temperatureValue
import com.kid.babylog.core.ui.theme.BabyLogTheme
import com.kid.babylog.core.ui.theme.illnessContainer
import com.kid.babylog.core.ui.toLocalDate
import java.time.ZoneId

private val IllnessIconSize = 28.dp

/** Лекарство давали так давно — пишем, когда это было, а не «… назад»: сутки «назад» не считают. */
private const val AGO_LIMIT_MS = 24 * 60 * 60 * 1000L

/**
 * Карточка идущей болезни на «Сегодня» (ADR-0040), над таймерами: день болезни и названия, последняя
 * температура и сколько прошло с последнего лекарства — ночью это главное. Только факты: без доз,
 * норм и оценок. Касание карточки, кроме кнопок, открывает экран болезни ([onOpen]); кнопки — температура,
 * лекарство и заметка: за ними и открывают «Сегодня», пока ребёнок болеет.
 */
@Composable
fun IllnessCard(
    model: IllnessCardModel,
    now: Long,
    onOpen: () -> Unit,
    onTemperature: () -> Unit,
    onMedicine: () -> Unit,
    onNote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = BabyLogTheme.spacing
    val colors = MaterialTheme.colorScheme
    val today = now.toLocalDate()
    val start = model.illness.startDate(ZoneId.systemDefault())
    AppCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = colors.illnessContainer,
        contentColor = colors.onTertiaryContainer,
        contentPadding = PaddingValues(spacing.m),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.s)) {
            // Нажимается всё, кроме кнопок: у карточки с кнопками внутри TalkBack иначе не различит действий
            Column(
                modifier = Modifier.fillMaxWidth().clickable(onClickLabel = "открыть болезнь", role = Role.Button, onClick = onOpen),
                verticalArrangement = Arrangement.spacedBy(spacing.s),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(spacing.m)) {
                    AppIcon(R.drawable.ic_illness, contentDescription = null, modifier = Modifier.size(IllnessIconSize), tint = colors.tertiary)
                    Column(Modifier.weight(1f)) {
                        Text("Болеет · ${model.day}-й день", style = MaterialTheme.typography.titleMedium)
                        val since = if (start == today) "с ${formatClock(model.illness.start)}" else "с ${formatWeekdayDay(start)}"
                        Text("${model.names ?: "без названия"} · $since", style = MaterialTheme.typography.bodySmall, color = colors.tertiary)
                    }
                    AppIcon(R.drawable.ic_chevron_right, contentDescription = null, tint = colors.tertiary)
                }
                model.lastTemperature?.let { temperature ->
                    Text(
                        buildAnnotatedString {
                            append("Температура ")
                            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(temperatureValue(temperature.tenths)) }
                            append(" ${whenText(temperature.start, now)}")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                model.lastMedicine?.let { medicine ->
                    Text(
                        buildAnnotatedString {
                            append("${medicine.name} — ")
                            if (now - medicine.last < AGO_LIMIT_MS) {
                                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(formatAgo(now - medicine.last)) }
                                append(", в ${formatClock(medicine.last)}")
                            } else {
                                append(whenText(medicine.last, now).removePrefix("в "))
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(spacing.s)) {
                CardButton("Температура", onTemperature, Modifier.weight(1f))
                CardButton("Лекарство", onMedicine, Modifier.weight(1f))
                CardButton("Заметка", onNote, Modifier.weight(1f))
            }
        }
    }
}

/** «в 03:10», «вчера в 21:00», «9 окт. в 21:00». */
private fun whenText(time: Long, now: Long): String {
    val day = time.toLocalDate()
    val today = now.toLocalDate()
    return if (day == today) "в ${formatClock(time)}" else "${formatShortDay(day, today)} в ${formatClock(time)}"
}

/** Кнопка карточки — светлая на фоне болезни: три в ряд, подпись уменьшается, если не влезает. */
@Composable
private fun CardButton(label: String, onClick: () -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelLarge
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceContainerLow, contentColor = colors.onSurface),
        contentPadding = PaddingValues(horizontal = BabyLogTheme.spacing.xs),
    ) {
        Text(label, style = style, maxLines = 1, autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * 0.75f, maxFontSize = style.fontSize))
    }
}
